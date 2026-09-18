package studio.ocean.app;

import android.content.Context;
import java.io.*;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;

/** Installs the APK-bundled Forge command into Ocean's private runtime prefix. */
public final class OceanForgeInstaller {
    private OceanForgeInstaller(){}

    public static synchronized File ensureSourceBundle(Context context)throws IOException{
        File state=new File(context.getFilesDir(),"home/ocean-forge/state");
        if(!state.isDirectory()&&!state.mkdirs())throw new IOException("Could not create Ocean Forge state directory");
        File target=new File(state,"source.zip");
        File temp=new File(state,"source.zip.tmp");
        MessageDigest digest;
        try{digest=MessageDigest.getInstance("SHA-256");}catch(Exception e){throw new IOException("SHA-256 unavailable",e);}
        long total=0;
        try(InputStream in=context.getAssets().open("ocean/forge/source.zip");
            FileOutputStream out=new FileOutputStream(temp,false)){
            byte[] buffer=new byte[32768];int n;
            while((n=in.read(buffer))!=-1){
                total+=n;
                if(total>128L*1024L*1024L)throw new IOException("Ocean Forge source bundle is unexpectedly large");
                digest.update(buffer,0,n);out.write(buffer,0,n);
            }
            out.flush();out.getFD().sync();
        }
        String incoming=hex(digest.digest());
        if(target.isFile()){
            try{
                if(incoming.equals(sha256(target))){temp.delete();return target;}
            }catch(Exception ignored){}
            if(!target.delete())throw new IOException("Could not replace Ocean Forge source bundle");
        }
        if(!temp.renameTo(target))throw new IOException("Could not activate Ocean Forge source bundle");
        target.setReadable(true,true);target.setWritable(true,true);
        return target;
    }

    public static synchronized File ensureToolOverlay(Context context)throws IOException{
        final String assetRoot="ocean/forge/tools";
        String[] names=context.getAssets().list(assetRoot);
        File dir=new File(context.getFilesDir(),"forge-tools/bin");
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Could not create Forge tool overlay directory");
        Set<String> expected=new HashSet<>();
        if(names!=null)for(String name:names){
            if(name==null||!name.matches("[A-Za-z0-9._+-]{1,160}"))continue;
            expected.add(name);
            byte[] asset=readAsset(context,assetRoot+"/"+name,1024*1024);
            File target=new File(dir,name),temp=new File(dir,name+".tmp");
            boolean same=false;
            if(target.isFile()){
                try{same=sha256(asset).equals(sha256(target));}catch(Exception ignored){}
            }
            if(!same){
                try(FileOutputStream out=new FileOutputStream(temp,false)){out.write(asset);out.flush();out.getFD().sync();}
                if(target.exists()&&!target.delete())throw new IOException("Could not replace Forge overlay tool: "+name);
                if(!temp.renameTo(target))throw new IOException("Could not activate Forge overlay tool: "+name);
            }else temp.delete();
            if(!target.setExecutable(true,true))throw new IOException("Could not mark Forge overlay tool executable: "+name);
            target.setReadable(true,true);target.setWritable(true,true);
        }
        File[] stale=dir.listFiles();
        if(stale!=null)for(File file:stale){
            if(file.isFile()&&!file.getName().endsWith(".tmp")&&!expected.contains(file.getName()))file.delete();
            else if(file.getName().endsWith(".tmp"))file.delete();
        }
        return dir;
    }

    public static synchronized File ensure(Context context)throws IOException{
        byte[] asset=readAsset(context);
        File target=new File(context.getFilesDir(),"usr/bin/ocean-forge");
        if(target.isFile()){
            try{
                if(sha256(asset).equals(sha256(target))){
                    if(!target.canExecute())target.setExecutable(true,true);
                    return target;
                }
            }catch(Exception ignored){}
        }

        File parent=target.getParentFile();
        if(parent==null||(!parent.isDirectory()&&!parent.mkdirs()))throw new IOException("Could not create Ocean Forge runtime directory");
        File temp=new File(parent,"ocean-forge.tmp");
        try(FileOutputStream out=new FileOutputStream(temp,false)){out.write(asset);out.flush();out.getFD().sync();}
        if(target.exists()&&!target.delete())throw new IOException("Could not replace Ocean Forge command");
        if(!temp.renameTo(target))throw new IOException("Could not activate Ocean Forge command");
        if(!target.setExecutable(true,true))throw new IOException("Could not mark Ocean Forge executable");
        target.setReadable(true,true);target.setWritable(true,true);
        return target;
    }

    private static byte[] readAsset(Context context)throws IOException{
        return readAsset(context,"ocean/forge/ocean-forge",256*1024);
    }
    private static byte[] readAsset(Context context,String path,int limit)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=context.getAssets().open(path)){
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1){
                if(out.size()+n>limit)throw new IOException("Ocean Forge asset is unexpectedly large: "+path);
                out.write(buffer,0,n);
            }
        }
        return out.toByteArray();
    }

    private static String sha256(byte[] bytes)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        digest.update(bytes);return hex(digest.digest());
    }

    private static String sha256(File file)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);}
        return hex(digest.digest());
    }

    private static String hex(byte[] bytes){
        StringBuilder value=new StringBuilder();
        for(byte b:bytes)value.append(String.format(Locale.ROOT,"%02x",b&255));
        return value.toString();
    }
}
