package studio.ocean.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

public final class StudioExtensionRegistry {
  public static final class Entry {
    public final String id,name,kind,version,source; public final boolean enabled,builtin;
    Entry(String i,String n,String k,String v,String s,boolean e,boolean b){id=i;name=n;kind=k;version=v;source=s;enabled=e;builtin=b;}
  }
  private static final String PREF="ocean_studio_extensions";
  private final SharedPreferences prefs;
  public StudioExtensionRegistry(Context c){prefs=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
  public List<Entry> builtins(){
    ArrayList<Entry> x=new ArrayList<>();
    x.add(new Entry("mesh-doctor","Mesh Doctor","plugin","1.0","builtin",true,true));
    x.add(new Entry("gltf-tools","glTF Tools","extension","1.0","builtin",true,true));
    x.add(new Entry("scene-optimizer","Scene Optimizer","plugin","1.0","builtin",true,true));
    x.add(new Entry("material-lab","Material Lab","plugin","1.0","builtin",true,true));
    x.add(new Entry("script-console","Script Console","extension","1.0","builtin",false,true));
    return x;
  }
  public List<String> sources(){String raw=prefs.getString("sources","");ArrayList<String> out=new ArrayList<>();if(!raw.isEmpty())for(String s:raw.split("\n"))if(!s.trim().isEmpty())out.add(s.trim());return out;}
  public void addSource(String uri){if(uri==null)return;uri=uri.trim();if(!(uri.startsWith("https://")||uri.startsWith("content://")))throw new IllegalArgumentException("Only HTTPS or local document sources are allowed");List<String> s=sources();if(!s.contains(uri))s.add(uri);StringBuilder b=new StringBuilder();for(String x:s)b.append(x).append('\n');prefs.edit().putString("sources",b.toString()).apply();}
  public boolean isEnabled(String id,boolean def){return prefs.getBoolean("enabled."+id,def);}
  public void setEnabled(String id,boolean value){prefs.edit().putBoolean("enabled."+id,value).apply();}
}