package studio.ocean.app;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;

public final class StudioProjectStore {
  private final File dir;
  public StudioProjectStore(Context c){dir=new File(c.getFilesDir(),"studio/projects");if(!dir.exists())dir.mkdirs();}
  public synchronized File save(String name,String json) throws IOException {
    String safe=name.replaceAll("[^A-Za-z0-9._-]","_"); if(safe.isEmpty())safe="Scene";
    File tmp=new File(dir,safe+".ocean.tmp"), out=new File(dir,safe+".ocean");
    try(FileOutputStream f=new FileOutputStream(tmp)){f.write(json.getBytes(StandardCharsets.UTF_8));f.getFD().sync();}
    if(out.exists()&&!out.delete())throw new IOException("Cannot replace project");
    if(!tmp.renameTo(out))throw new IOException("Atomic project save failed");
    return out;
  }
  public synchronized String load(String name) throws IOException {
    String safe=name.replaceAll("[^A-Za-z0-9._-]","_");File f=new File(dir,safe+".ocean");
    ByteArrayOutputStream b=new ByteArrayOutputStream();try(FileInputStream in=new FileInputStream(f)){byte[] buf=new byte[8192];int n;while((n=in.read(buf))>0)b.write(buf,0,n);}return b.toString("UTF-8");
  }
  public File[] list(){File[] f=dir.listFiles((d,n)->n.endsWith(".ocean"));return f==null?new File[0]:f;}
}