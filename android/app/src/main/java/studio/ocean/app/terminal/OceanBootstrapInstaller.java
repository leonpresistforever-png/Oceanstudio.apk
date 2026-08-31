package studio.ocean.app.terminal;

import android.content.Context;
import android.system.Os;
import com.github.luben.zstd.ZstdInputStream;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.json.JSONObject;
import studio.ocean.app.OceanPaths;

/** Verifies and transactionally installs the CI-generated Ocean bootstrap. */
public final class OceanBootstrapInstaller {
    private static final String ASSET_ROOT="ocean/bootstrap/aarch64/";
    private final Context context; private final OceanPaths paths;
    public OceanBootstrapInstaller(Context context){this.context=context.getApplicationContext();this.paths=new OceanPaths(context);}

    public synchronized void install() throws IOException {
        try{installVerified();}catch(IOException error){throw error;}catch(Exception error){throw new IOException("Invalid Ocean bootstrap",error);}
    }
    private void installVerified() throws Exception {
        if(OceanRuntimeState.isInstalled(context))return;
        if(!"arm64-v8a".equals(OceanEnvironment.architecture()))throw new IOException("Ocean runtime supports arm64-v8a only");
        JSONObject manifest=manifest();
        if(!"studio.ocean.app".equals(manifest.optString("packageName"))||!"aarch64".equals(manifest.optString("architecture"))
            ||!"ocean-aarch64.tar.zst".equals(manifest.optString("archive")))throw new IOException("Bootstrap identity mismatch");
        if(!paths.home().isDirectory()&&!paths.home().mkdirs())throw new IOException("Cannot create Ocean HOME");
        try{Os.chmod(paths.home().getAbsolutePath(),0700);}catch(Exception error){throw new IOException("Cannot secure Ocean HOME",error);}
        TerminalStartupLog.stage("05H","Ocean HOME ready path="+paths.home());
        File archive=new File(context.getCacheDir(),"ocean-aarch64.tar.zst");
        copyAndVerify(archive,manifest.getLong("archiveSize"),manifest.getString("archiveSha256"));
        File staging=new File(paths.root(),".ocean-bootstrap-staging");deleteTree(staging);
        if(!staging.mkdirs())throw new IOException("Cannot create bootstrap staging directory");
        try{
            extract(archive,staging);File candidate=new File(staging,"usr");validate(candidate);
            File previous=new File(paths.root(),".ocean-prefix-previous");deleteTree(previous);
            if(paths.prefix().exists()&&!paths.prefix().renameTo(previous))throw new IOException("Cannot preserve previous prefix");
            if(!candidate.renameTo(paths.prefix())){if(previous.exists())previous.renameTo(paths.prefix());throw new IOException("Cannot activate Ocean prefix");}
            JSONObject marker=new JSONObject();marker.put("bootstrapVersion",manifest.getString("bootstrapVersion"));marker.put("abi","arm64-v8a");
            marker.put("prefix",paths.prefix().getCanonicalPath());marker.put("archiveSha256",manifest.getString("archiveSha256"));marker.put("verified",true);
            Files.write(paths.runtimeMarker().toPath(),(marker.toString()+"\n").getBytes(StandardCharsets.UTF_8));deleteTree(previous);
        }finally{archive.delete();deleteTree(staging);}
    }
    private JSONObject manifest()throws IOException{try(InputStream in=context.getAssets().open(ASSET_ROOT+"ocean-aarch64.manifest.json")){return new JSONObject(new String(readAll(in),StandardCharsets.UTF_8));}catch(Exception error){throw new IOException("Verified Ocean bootstrap is not bundled in this APK",error);}}
    private void copyAndVerify(File output,long expectedSize,String expectedHash)throws Exception{MessageDigest digest=MessageDigest.getInstance("SHA-256");long size=0;byte[] buffer=new byte[65536];try(InputStream in=context.getAssets().open(ASSET_ROOT+"ocean-aarch64.tar.zst");FileOutputStream out=new FileOutputStream(output)){for(int count;(count=in.read(buffer))!=-1;){out.write(buffer,0,count);digest.update(buffer,0,count);size+=count;}out.getFD().sync();}if(size!=expectedSize||!hex(digest.digest()).equalsIgnoreCase(expectedHash)){output.delete();throw new IOException("Bootstrap integrity check failed");}}
    private void extract(File archive,File staging)throws IOException{String root=staging.getCanonicalPath()+File.separator;try(TarArchiveInputStream tar=new TarArchiveInputStream(new ZstdInputStream(new BufferedInputStream(new FileInputStream(archive))))){for(TarArchiveEntry entry;(entry=tar.getNextTarEntry())!=null;){String name=entry.getName();if(name.startsWith("/")||name.indexOf('\0')>=0)throw new IOException("Unsafe archive path");File target=new File(staging,name);if(!target.getCanonicalPath().startsWith(root))throw new IOException("Archive path traversal");if(entry.isDirectory()){if(!target.isDirectory()&&!target.mkdirs())throw new IOException("Cannot create "+name);continue;}File parent=target.getParentFile();if(parent!=null&&!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Cannot create archive parent");if(entry.isSymbolicLink()){String link=entry.getLinkName();if(link.startsWith("/")||!new File(parent,link).getCanonicalPath().startsWith(root))throw new IOException("Unsafe archive symlink");try{Os.symlink(link,target.getAbsolutePath());}catch(Exception error){throw new IOException("Cannot create symlink",error);}continue;}if(!entry.isFile())throw new IOException("Unsupported archive entry");try(FileOutputStream out=new FileOutputStream(target)){byte[] buffer=new byte[65536];long remaining=entry.getSize();while(remaining>0){int count=tar.read(buffer,0,(int)Math.min(buffer.length,remaining));if(count<0)throw new IOException("Truncated archive");out.write(buffer,0,count);remaining-=count;}}try{Os.chmod(target.getAbsolutePath(),entry.getMode()&0777);}catch(Exception error){throw new IOException("Cannot set archive mode",error);}}}}
    private static void validate(File prefix)throws IOException{for(String name:new String[]{"bash","apt","dpkg","pkg"}){File executable=new File(prefix,"bin/"+name);if(!executable.isFile()||!executable.canExecute())throw new IOException("Bootstrap missing "+name);}}
    private static byte[] readAll(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];for(int count;(count=in.read(buffer))!=-1;)out.write(buffer,0,count);return out.toByteArray();}
    private static String hex(byte[] bytes){StringBuilder out=new StringBuilder();for(byte value:bytes)out.append(String.format("%02x",value&255));return out.toString();}
    private static void deleteTree(File file)throws IOException{if(!file.exists())return;if(file.isDirectory()&&!Files.isSymbolicLink(file.toPath())){File[] children=file.listFiles();if(children!=null)for(File child:children)deleteTree(child);}if(!file.delete())throw new IOException("Cannot delete "+file);}
}
