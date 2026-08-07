package studio.ocean.nativeterminal;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import dalvik.system.DexClassLoader;

/**
 * Dynamic plugin loader using DexClassLoader — enables marketplace .dex plugins
 * following the plugin.json manifest standard (Acode-compatible).
 */
public class PluginManager {
    private static final String TAG = "PluginManager";
    private static final String PLUGINS_DIR = "app_plugins";

    private final Context context;
    private final File pluginsRoot;
    private final List<LoadedPlugin> loaded = new ArrayList<>();

    public PluginManager(Context context) {
        this.context = context;
        this.pluginsRoot = new File(context.getFilesDir(), PLUGINS_DIR);
        this.pluginsRoot.mkdirs();
    }

    public File getPluginsRoot() { return pluginsRoot; }

    public List<LoadedPlugin> getLoadedPlugins() { return new ArrayList<>(loaded); }

    public LoadedPlugin installFromDirectory(File pluginDir) throws Exception {
        File manifestFile = new File(pluginDir, "plugin.json");
        if (!manifestFile.exists()) {
            throw new IllegalArgumentException("plugin.json not found in " + pluginDir.getName());
        }

        JSONObject manifest = new JSONObject(readText(manifestFile));
        String id = manifest.getString("id");
        String main = manifest.optString("main", "classes.dex");
        String name = manifest.optString("name", id);
        String version = manifest.optString("version", "1.0.0");

        File dexFile = new File(pluginDir, main);
        if (!dexFile.exists()) {
            throw new IllegalArgumentException("Main dex not found: " + main);
        }

        File optimizedDir = new File(context.getCodeCacheDir(), "plugin_opt_" + id);
        optimizedDir.mkdirs();

        DexClassLoader loader = new DexClassLoader(
            dexFile.getAbsolutePath(),
            optimizedDir.getAbsolutePath(),
            null,
            context.getClassLoader()
        );

        LoadedPlugin plugin = new LoadedPlugin(id, name, version, pluginDir, loader);
        loaded.removeIf(p -> p.id.equals(id));
        loaded.add(plugin);
        Log.i(TAG, "Loaded plugin: " + name + " v" + version);
        return plugin;
    }

    public void unload(String id) {
        loaded.removeIf(p -> p.id.equals(id));
    }

    public Object invokePluginMethod(String pluginId, String className, String methodName) throws Exception {
        LoadedPlugin plugin = findPlugin(pluginId);
        if (plugin == null) throw new IllegalArgumentException("Plugin not loaded: " + pluginId);
        Class<?> cls = plugin.loader.loadClass(className);
        Object instance = cls.getDeclaredConstructor().newInstance();
        return cls.getMethod(methodName).invoke(instance);
    }

    private LoadedPlugin findPlugin(String id) {
        for (LoadedPlugin p : loaded) {
            if (p.id.equals(id)) return p;
        }
        return null;
    }

    private static String readText(File file) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    public static class LoadedPlugin {
        public final String id;
        public final String name;
        public final String version;
        public final File directory;
        public final DexClassLoader loader;

        LoadedPlugin(String id, String name, String version, File directory, DexClassLoader loader) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.directory = directory;
            this.loader = loader;
        }
    }
}
