package studio.ocean.app;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.*;

public final class StudioOpenSourceTools {
  private static final boolean LOADED;
  private static String loadError="";
  static {
    boolean ok=false;
    try { System.loadLibrary("oceanstudio_ext"); ok=true; }
    catch(Throwable t){ loadError=t.getClass().getSimpleName()+": "+t.getMessage(); }
    LOADED=ok;
  }
  private StudioOpenSourceTools(){}
  public static boolean available(){return LOADED;}
  public static String unavailableReason(){return loadError;}
  public static native String nativeInspectGltf(String path);
  public static native String nativeSimplifyGltf(String path,float ratio);
  public static native String nativeVertexCacheGltf(String path);
  public static native String nativeInspectObj(String path);
  public static native String nativeInspectImage(String path);

  public static String inspectGltf(String path){return LOADED?nativeInspectGltf(path):error();}
  public static String simplifyGltf(String path,float ratio){return LOADED?nativeSimplifyGltf(path,ratio):error();}
  public static String vertexCacheGltf(String path){return LOADED?nativeVertexCacheGltf(path):error();}
  public static String inspectObj(String path){return LOADED?nativeInspectObj(path):error();}
  public static String inspectImage(String path){return LOADED?nativeInspectImage(path):error();}
  private static String error(){return "{\"ok\":false,\"error\":\"native open-source toolchain unavailable\"}";}

  public static File materialize(Context context,Uri uri,String displayName) throws IOException {
    String safe=(displayName==null||displayName.trim().isEmpty()?"asset":displayName).replaceAll("[^A-Za-z0-9._-]","_");
    File dir=new File(context.getCacheDir(),"studio-imports");if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create Studio import cache");
    File out=new File(dir,System.currentTimeMillis()+"-"+safe);
    try(InputStream in=context.getContentResolver().openInputStream(uri);FileOutputStream fos=new FileOutputStream(out)){
      if(in==null)throw new IOException("Cannot read selected document");
      byte[] buf=new byte[65536];int n;long total=0,max=512L*1024L*1024L;
      while((n=in.read(buf))>0){total+=n;if(total>max)throw new IOException("Asset exceeds 512 MB import limit");fos.write(buf,0,n);}
      fos.getFD().sync();
    }
    return out;
  }

  public static String displayName(Context context,Uri uri){
    Cursor c=null;
    try{c=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null);if(c!=null&&c.moveToFirst()){int i=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(i>=0){String n=c.getString(i);if(n!=null&&!n.trim().isEmpty())return n;}}}catch(Throwable ignored){}finally{if(c!=null)c.close();}
    String p=uri.getLastPathSegment();return p==null?"asset":p;
  }
}