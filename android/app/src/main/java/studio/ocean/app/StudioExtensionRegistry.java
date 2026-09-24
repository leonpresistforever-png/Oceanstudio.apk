package studio.ocean.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

public final class StudioExtensionRegistry {
  public static final class Entry {
    public final String id,name,kind,version,source,description;
    public final boolean enabled,builtin;
    Entry(String i,String n,String k,String v,String s,String d,boolean e,boolean b){id=i;name=n;kind=k;version=v;source=s;description=d;enabled=e;builtin=b;}
  }
  private static final String PREF="ocean_studio_extensions";
  private final SharedPreferences prefs;
  public StudioExtensionRegistry(Context c){prefs=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
  public List<Entry> builtins(){
    return Arrays.asList(
      new Entry("grid-overlay","Grid Overlay","extension","1.0","builtin","Perspective construction grid in the viewport.",true,true),
      new Entry("axis-guides","Axis Guides","extension","1.0","World-axis guide lines on the baseplate.",true,true),
      new Entry("spawn-marker","Spawn Marker","extension","1.0","Render the project spawn marker.",true,true),
      new Entry("primitive-preview","Primitive Preview","extension","1.0","Render the starter cube preview.",true,true),
      new Entry("viewport-hud","Viewport HUD","extension","1.0","Show camera mode and active-tool HUD.",true,true),
      new Entry("gesture-hints","Gesture Hints","extension","1.0","Show mobile gesture help above the toolbar.",true,true),
      new Entry("touch-orbit","Touch Orbit","extension","1.0","Enable one-finger viewport orbit/pan control.",true,true),
      new Entry("multitouch-pan","Multi-touch Pan","extension","1.0","Enable two-finger camera panning.",true,true),
      new Entry("ai-scene-context","AI Scene Context","extension","1.0","Attach the current scene snapshot to Studio AI commands.",true,true),
      new Entry("persistent-import-access","Persistent Imports","extension","1.0","Retain Android document permissions for imported assets.",true,true),
      new Entry("autosave-background","Background Autosave","extension","1.0","Atomically save the current scene when Studio backgrounds.",true,true),
      new Entry("auto-inspector","Auto Inspector","extension","1.0","Open Inspector automatically when selecting an object.",true,true),
      new Entry("confirm-destructive","Confirm Destructive Actions","extension","1.0","Require confirmation before destructive plugin actions.",true,true),
      new Entry("dark-system-bars","Dark System Bars","extension","1.0","Keep Android system bars visually integrated with Studio.",true,true),
      new Entry("import-scene-nodes","Import Scene Nodes","extension","1.0","Create scene-graph nodes for imported documents.",true,true)
    );
  }
  public List<String> sources(){String raw=prefs.getString("sources","");ArrayList<String> out=new ArrayList<>();if(!raw.isEmpty())for(String s:raw.split("\n"))if(!s.trim().isEmpty())out.add(s.trim());return out;}
  public void addSource(String uri){if(uri==null)return;uri=uri.trim();if(!(uri.startsWith("https://")||uri.startsWith("content://")))throw new IllegalArgumentException("Only HTTPS or local document sources are allowed");List<String> s=sources();if(!s.contains(uri))s.add(uri);StringBuilder b=new StringBuilder();for(String x:s)b.append(x).append('\n');prefs.edit().putString("sources",b.toString()).apply();}
  public boolean isEnabled(String id,boolean def){return prefs.getBoolean("enabled."+id,def);}
  public boolean isEnabled(String id){for(Entry e:builtins())if(e.id.equals(id))return isEnabled(id,e.enabled);return false;}
  public void setEnabled(String id,boolean value){prefs.edit().putBoolean("enabled."+id,value).apply();}
}