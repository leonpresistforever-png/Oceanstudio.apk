package studio.ocean.app;

import android.content.Context;
import java.io.*;
import java.security.MessageDigest;
import java.util.Locale;

/** Installs the APK-bundled Forge command into Ocean's private runtime prefix. */
public final class OceanForgeInstaller {
    private OceanForgeInstaller(){}

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
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(InputStream in=context.getAssets().open("ocean/forge/ocean-forge")){
            byte[] buffer=new byte[8192];int n;
            while((n=in.read(buffer))!=-1){
                if(out.size()+n>256*1024)throw new IOException("Ocean Forge asset is unexpectedly large");
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
