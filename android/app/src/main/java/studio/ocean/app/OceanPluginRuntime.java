package studio.ocean.app;

import android.content.Context;
import java.io.*;
import java.util.*;
import org.json.JSONArray;
import org.json.JSONObject;

/** Discovers terminal-registered Ocean plugins without granting them new Android privileges. */
public final class OceanPluginRuntime {
    public static final class Plugin {
        public final String id,name,description,command;
        public final File executable;
        Plugin(String id,String name,String description,String command,File executable){
            this.id=id;this.name=name;this.description=description;this.command=command;this.executable=executable;
        }
    }

    private OceanPluginRuntime(){}

    public static JSONObject list(Context context)throws Exception{
        JSONArray plugins=new JSONArray();
        File dir=new File(context.getFilesDir(),"home/.ocean/plugins");
        File[] manifests=dir.listFiles((d,n)->n.endsWith(".plugin"));
        if(manifests!=null){
            Arrays.sort(manifests,Comparator.comparing(File::getName,String.CASE_INSENSITIVE_ORDER));
            for(File manifest:manifests){
                try{
                    Plugin plugin=read(context,manifest);
                    boolean connected=context.getSharedPreferences("ocean_plugin_state",Context.MODE_PRIVATE)
                            .getBoolean("connected_"+plugin.id,true);
                    plugins.put(new JSONObject()
                            .put("id",plugin.id)
                            .put("name",plugin.name)
                            .put("description",plugin.description)
                            .put("command",plugin.command)
                            .put("connected",connected)
                            .put("available",plugin.executable.isFile()&&plugin.executable.canExecute()));
                }catch(Exception ignored){}
                if(plugins.length()>=100)break;
            }
        }
        return new JSONObject().put("plugins",plugins).put("exit_code",0);
    }

    public static Plugin resolveConnected(Context context,String id)throws Exception{
        id=safeId(id);
        if(id.isEmpty())throw new IllegalArgumentException("Plugin id is invalid");
        boolean connected=context.getSharedPreferences("ocean_plugin_state",Context.MODE_PRIVATE)
                .getBoolean("connected_"+id,true);
        if(!connected)throw new IllegalStateException("Plugin is disconnected: "+id);
        File manifest=new File(new File(context.getFilesDir(),"home/.ocean/plugins"),id+".plugin");
        if(!manifest.isFile())throw new IllegalArgumentException("Plugin is not registered: "+id);
        Plugin plugin=read(context,manifest);
        if(!plugin.executable.isFile()||!plugin.executable.canExecute())throw new IllegalStateException("Plugin command is unavailable: "+plugin.command);
        return plugin;
    }

    private static Plugin read(Context context,File manifest)throws Exception{
        Properties p=new Properties();
        try(FileInputStream input=new FileInputStream(manifest)){p.load(input);}
        String id=safeId(p.getProperty("id",manifest.getName().replace(".plugin","")));
        if(id.isEmpty())throw new IllegalArgumentException("Invalid plugin id");
        String name=bounded(p.getProperty("name",id),128);
        String description=bounded(p.getProperty("description","Terminal-registered Ocean plugin."),512);
        String command=p.getProperty("command","").trim();
        if(!command.matches("[A-Za-z0-9._+:-]{1,128}"))throw new IllegalArgumentException("Invalid plugin command");
        File bin=new File(new File(context.getFilesDir(),"usr/bin"),command).getCanonicalFile();
        File root=new File(context.getFilesDir(),"usr/bin").getCanonicalFile();
        if(!bin.getPath().startsWith(root.getPath()+File.separator))throw new IllegalArgumentException("Plugin command escapes Ocean prefix");
        return new Plugin(id,name,description,command,bin);
    }

    private static String safeId(String value){
        if(value==null)return "";
        String id=value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]","");
        return id.length()>80?id.substring(0,80):id;
    }
    private static String bounded(String value,int max){
        if(value==null)return "";
        value=value.replace('\0',' ').replace('\r',' ').replace('\n',' ').trim();
        return value.length()>max?value.substring(0,max):value;
    }
}
