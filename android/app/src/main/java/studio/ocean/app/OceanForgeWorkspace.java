package studio.ocean.app;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Structured source access confined to Ocean Forge's private workspace. */
public final class OceanForgeWorkspace {
    private static final int MAX_READ_BYTES=128*1024;
    private static final int MAX_WRITE_BYTES=256*1024;
    private static final int MAX_SEARCH_FILES=1200;
    private static final int MAX_SEARCH_MATCHES=120;

    private OceanForgeWorkspace(){}

    public static JSONObject execute(Context context,JSONObject args)throws Exception{
        String action=args.getString("action");
        File root=new File(context.getFilesDir(),"home/ocean-forge/workspace/current").getCanonicalFile();
        if(!root.isDirectory())throw new IllegalStateException("Ocean Forge workspace is not initialized");

        if(action.equals("list"))return list(root,args.optString("path",""));
        if(action.equals("read"))return read(root,args.getString("path"),args.optInt("start_line",1),args.optInt("end_line",400));
        if(action.equals("search"))return search(root,args.getString("query"),args.optString("path",""));
        if(action.equals("write"))return write(root,args.getString("path"),args.getString("content"),args.optString("expected_sha256",""));
        if(action.equals("replace"))return replace(root,args.getString("path"),args.getString("old_text"),args.getString("new_text"),args.optString("expected_sha256",""));
        if(action.equals("move"))return move(root,args.getString("path"),args.getString("to_path"),args.getString("expected_sha256"));
        if(action.equals("delete"))return delete(root,args.getString("path"),args.getString("expected_sha256"));
        throw new IllegalArgumentException("Unsupported Forge workspace action");
    }

    private static JSONObject list(File root,String relative)throws Exception{
        File dir=resolve(root,relative,false);
        if(!dir.isDirectory())throw new IllegalArgumentException("Forge path is not a directory");
        File[] files=dir.listFiles();
        JSONArray entries=new JSONArray();
        if(files!=null){
            Arrays.sort(files,Comparator.comparing(File::getName,String.CASE_INSENSITIVE_ORDER));
            for(File file:files){
                if(skip(root,file))continue;
                entries.put(new JSONObject()
                        .put("path",relative(root,file))
                        .put("type",file.isDirectory()?"directory":"file")
                        .put("bytes",file.isFile()?file.length():0));
                if(entries.length()>=200)break;
            }
        }
        return new JSONObject().put("entries",entries).put("exit_code",0);
    }

    private static JSONObject read(File root,String relative,int start,int end)throws Exception{
        if(start<1||end<start||end-start>1000)throw new IllegalArgumentException("Invalid Forge line range");
        File file=resolve(root,relative,true);
        rejectSensitive(relative);
        if(!file.isFile())throw new IllegalArgumentException("Forge file does not exist");
        if(file.length()>MAX_READ_BYTES)throw new IllegalArgumentException("Forge file exceeds structured read limit");
        StringBuilder text=new StringBuilder();
        int line=0,returned=0;
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8))){
            String value;
            while((value=reader.readLine())!=null){
                line++;
                if(line<start)continue;
                if(line>end)break;
                text.append(line).append(": ").append(value).append('\n');
                returned++;
                if(text.length()>MAX_READ_BYTES)break;
            }
        }
        return new JSONObject().put("path",relative).put("content",text.toString()).put("lines",returned).put("sha256",sha256(file)).put("exit_code",0);
    }

    private static JSONObject search(File root,String query,String relative)throws Exception{
        if(query.isEmpty()||query.length()>200)throw new IllegalArgumentException("Search query must be 1-200 characters");
        File base=resolve(root,relative,false);
        if(!base.exists())throw new IllegalArgumentException("Forge search path does not exist");
        JSONArray matches=new JSONArray();
        ArrayDeque<File> queue=new ArrayDeque<>();
        queue.add(base);
        int files=0;
        while(!queue.isEmpty()&&files<MAX_SEARCH_FILES&&matches.length()<MAX_SEARCH_MATCHES){
            File next=queue.removeFirst();
            if(skip(root,next))continue;
            if(next.isDirectory()){
                File[] children=next.listFiles();
                if(children!=null)for(File child:children)queue.addLast(child);
                continue;
            }
            files++;
            if(next.length()>MAX_READ_BYTES||binaryName(next.getName()))continue;
            try(BufferedReader reader=new BufferedReader(new InputStreamReader(new FileInputStream(next),StandardCharsets.UTF_8))){
                String line;int number=0;
                while((line=reader.readLine())!=null){
                    number++;
                    if(line.contains(query)){
                        String snippet=line.length()>300?line.substring(0,300)+"…":line;
                        matches.put(new JSONObject().put("path",relative(root,next)).put("line",number).put("text",snippet));
                        if(matches.length()>=MAX_SEARCH_MATCHES)break;
                    }
                }
            }catch(Exception ignored){}
        }
        return new JSONObject().put("matches",matches).put("files_scanned",files).put("exit_code",0);
    }

    private static JSONObject write(File root,String relative,String content,String expectedSha)throws Exception{
        rejectSensitive(relative);
        byte[] bytes=content.getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_WRITE_BYTES)throw new IllegalArgumentException("Forge write exceeds 256 KiB");
        File file=resolve(root,relative,false);
        String previous=file.isFile()?sha256(file):"";
        verifyExpected(previous,expectedSha,file.isFile());
        atomicWrite(file,bytes);
        return new JSONObject().put("path",relative).put("bytes",bytes.length).put("previous_sha256",previous).put("sha256",sha256(file)).put("exit_code",0);
    }

    private static JSONObject replace(File root,String relative,String oldText,String newText,String expectedSha)throws Exception{
        rejectSensitive(relative);
        File file=resolve(root,relative,true);
        if(!file.isFile()||file.length()>MAX_WRITE_BYTES)throw new IllegalArgumentException("Forge file is unavailable for structured replacement");
        String previous=sha256(file); verifyExpected(previous,expectedSha,true);
        String content=readText(file,MAX_WRITE_BYTES);
        int first=content.indexOf(oldText);
        if(first<0)throw new IllegalArgumentException("Forge replacement text was not found");
        if(content.indexOf(oldText,first+oldText.length())>=0)throw new IllegalArgumentException("Forge replacement text is ambiguous; provide a larger exact block");
        String updated=content.substring(0,first)+newText+content.substring(first+oldText.length());
        byte[] bytes=updated.getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_WRITE_BYTES)throw new IllegalArgumentException("Forge replacement exceeds 256 KiB");
        atomicWrite(file,bytes);
        return new JSONObject().put("path",relative).put("previous_sha256",previous).put("sha256",sha256(file)).put("exit_code",0);
    }

    private static JSONObject move(File root,String from,String to,String expectedSha)throws Exception{
        rejectSensitive(from); rejectSensitive(to);
        File source=resolve(root,from,true), target=resolve(root,to,false);
        if(!source.isFile())throw new IllegalArgumentException("Forge source file does not exist");
        if(target.exists())throw new IllegalArgumentException("Forge destination already exists");
        String previous=sha256(source); verifyExpected(previous,expectedSha,true);
        File parent=target.getParentFile();
        if(parent==null||(!parent.isDirectory()&&!parent.mkdirs()))throw new IOException("Could not create Forge destination directory");
        if(!source.renameTo(target)){
            atomicWrite(target,readBytes(source,MAX_WRITE_BYTES));
            if(!source.delete())throw new IOException("Forge move copied the file but could not remove the original");
        }
        return new JSONObject().put("from",from).put("path",to).put("sha256",sha256(target)).put("exit_code",0);
    }

    private static JSONObject delete(File root,String relative,String expectedSha)throws Exception{
        rejectSensitive(relative);
        File file=resolve(root,relative,true);
        if(!file.isFile())throw new IllegalArgumentException("Forge delete accepts files only");
        String previous=sha256(file); verifyExpected(previous,expectedSha,true);
        long bytes=file.length();
        if(!file.delete())throw new IOException("Could not delete Forge source file");
        return new JSONObject().put("path",relative).put("deleted",true).put("bytes",bytes).put("previous_sha256",previous).put("exit_code",0);
    }

    private static void verifyExpected(String actual,String expected,boolean exists){
        if(expected==null||expected.isEmpty())return;
        if(!exists||!actual.equalsIgnoreCase(expected))throw new IllegalStateException("Forge source changed since it was inspected");
    }

    private static void atomicWrite(File file,byte[] bytes)throws Exception{
        File parent=file.getParentFile();
        if(parent==null||(!parent.isDirectory()&&!parent.mkdirs()))throw new IOException("Could not create Forge source directory");
        File temp=new File(parent,file.getName()+".ocean-tmp");
        try(FileOutputStream out=new FileOutputStream(temp,false)){out.write(bytes);out.flush();out.getFD().sync();}
        if(file.exists()&&!file.delete())throw new IOException("Could not replace Forge source file");
        if(!temp.renameTo(file))throw new IOException("Could not commit Forge source file");
    }

    private static String readText(File file,int max)throws Exception{
        return new String(readBytes(file,max),StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(File file,int max)throws Exception{
        if(file.length()>max)throw new IllegalArgumentException("Forge file exceeds operation size limit");
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>max)throw new IllegalArgumentException("Forge file exceeds operation size limit");out.write(buffer,0,n);}}
        return out.toByteArray();
    }

    private static File resolve(File root,String relative,boolean requireFile)throws Exception{
        if(relative==null)relative="";
        if(relative.startsWith("/")||relative.indexOf('\0')>=0)throw new IllegalArgumentException("Forge paths must be relative");
        File file=new File(root,relative).getCanonicalFile();
        String prefix=root.getPath()+File.separator;
        if(!file.equals(root)&&!file.getPath().startsWith(prefix))throw new IllegalArgumentException("Forge path escapes the workspace");
        if(requireFile&&file.isDirectory())throw new IllegalArgumentException("Expected a Forge file");
        return file;
    }

    private static void rejectSensitive(String path){
        if(sensitivePath(path))
            throw new IllegalArgumentException("Signing, credential, Git-internal and local-secret files are not available through Forge workspace tools");
    }

    private static boolean sensitivePath(String path){
        String p=path.replace('\\','/').toLowerCase(Locale.ROOT);
        return p.equals(".git")||p.startsWith(".git/")||p.equals("android/local.properties")||
                p.endsWith(".jks")||p.endsWith(".keystore")||p.endsWith(".p12")||p.endsWith(".pem")||
                p.endsWith(".pk8")||p.endsWith(".env");
    }

    private static boolean skip(File root,File file){
        String p=relative(root,file).replace('\\','/');
        return sensitivePath(p)||p.equals(".gradle")||p.startsWith(".gradle/")||
                p.endsWith("/build")||p.contains("/build/")||p.startsWith("android/app/src/forgeNative/");
    }

    private static boolean binaryName(String name){
        String n=name.toLowerCase(Locale.ROOT);
        return n.endsWith(".apk")||n.endsWith(".aar")||n.endsWith(".jar")||n.endsWith(".so")||n.endsWith(".class")||
                n.endsWith(".png")||n.endsWith(".jpg")||n.endsWith(".jpeg")||n.endsWith(".webp")||n.endsWith(".zip")||
                n.endsWith(".gz")||n.endsWith(".zst")||n.endsWith(".pdf");
    }

    private static String relative(File root,File file){
        String base=root.getPath();
        String path=file.getPath();
        if(path.equals(base))return "";
        return path.startsWith(base+File.separator)?path.substring(base.length()+1):path;
    }

    private static String sha256(File file)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)){
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);
        }
        StringBuilder hex=new StringBuilder();
        for(byte b:digest.digest())hex.append(String.format(Locale.ROOT,"%02x",b&255));
        return hex.toString();
    }
}
