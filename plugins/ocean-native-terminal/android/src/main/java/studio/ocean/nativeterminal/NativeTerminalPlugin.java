package studio.ocean.nativeterminal;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@CapacitorPlugin(name = "OceanNativeTerminal")
public class NativeTerminalPlugin extends Plugin {
    private final Map<String, ShellSession> sessions = new HashMap<>();
    private OceanLinuxSetup linuxSetup;
    private PrefixFilesystem prefixFs;
    private final ExecutorService setupExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void load() {
        linuxSetup = new OceanLinuxSetup(getContext());
        linuxSetup.setProgressBridge((message, percent) -> {
            JSObject event = new JSObject();
            event.put("message", message);
            event.put("percent", percent);
            notifyListeners("setupProgress", event);
        });
        prefixFs = new PrefixFilesystem(linuxSetup);
        setupExecutor.execute(() -> {
            if (!linuxSetup.isReady()) {
                linuxSetup.setup();
            }
        });
    }

    @PluginMethod
    public void setup(PluginCall call) {
        setupExecutor.execute(() -> {
            OceanLinuxSetup.SetupResult result = linuxSetup.setup();
            JSObject ret = new JSObject();
            ret.put("ready", result.ready);
            ret.put("prefix", result.prefix);
            ret.put("shell", result.shell);
            ret.put("message", result.message);
            call.resolve(ret);
        });
    }

    @PluginMethod
    public void getSetupStatus(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("ready", linuxSetup.isReady());
        ret.put("prefix", linuxSetup.getPrefixDir().getAbsolutePath());
        ret.put("shell", linuxSetup.getShellPath());
        ret.put("message", linuxSetup.isReady() ? linuxSetup.getLastMessage() : "Setting up…");
        call.resolve(ret);
    }

    @PluginMethod
    public void getHomePath(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("path", prefixFs.getHomePath());
        call.resolve(ret);
    }

    @PluginMethod
    public void readDir(PluginCall call) {
        String path = call.getString("path", prefixFs.getHomePath());
        try {
            JSArray items = prefixFs.readDir(path);
            JSObject ret = new JSObject();
            ret.put("items", items);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("readDir failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void readFile(PluginCall call) {
        String path = call.getString("path");
        if (path == null) {
            call.reject("path required");
            return;
        }
        try {
            JSObject ret = new JSObject();
            ret.put("content", prefixFs.readFile(path));
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("readFile failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void writeFile(PluginCall call) {
        String path = call.getString("path");
        String content = call.getString("content", "");
        if (path == null) {
            call.reject("path required");
            return;
        }
        try {
            prefixFs.writeFile(path, content);
            call.resolve();
        } catch (Exception e) {
            call.reject("writeFile failed: " + e.getMessage());
        }
    }

    @PluginMethod
    public void create(PluginCall call) {
        String id = call.getString("id", "default");
        String cwd = call.getString("cwd", linuxSetup.getHomeDir().getAbsolutePath());

        if (sessions.containsKey(id)) {
            sessions.get(id).kill();
            sessions.remove(id);
        }

        try {
            ShellSession session = new ShellSession(id, linuxSetup, cwd, new ShellSession.Listener() {
                @Override
                public void onData(String data) {
                    JSObject event = new JSObject();
                    event.put("id", id);
                    event.put("data", data);
                    notifyListeners("terminalData", event);
                }

                @Override
                public void onExit(int code) {
                    JSObject event = new JSObject();
                    event.put("id", id);
                    event.put("code", code);
                    notifyListeners("terminalExit", event);
                    sessions.remove(id);
                }

                @Override
                public void onPortDetected(int port, String url) {
                    JSObject event = new JSObject();
                    event.put("port", port);
                    event.put("url", url);
                    notifyListeners("portDetected", event);
                }
            });
            session.start();
            sessions.put(id, session);
            call.resolve();
        } catch (Exception e) {
            call.reject("Failed to create native terminal: " + e.getMessage());
        }
    }

    @PluginMethod
    public void write(PluginCall call) {
        String id = call.getString("id");
        String data = call.getString("data", "");
        ShellSession session = sessions.get(id);
        if (session == null) {
            call.reject("Session not found: " + id);
            return;
        }
        session.write(data);
        call.resolve();
    }

    @PluginMethod
    public void resize(PluginCall call) {
        String id = call.getString("id");
        int cols = call.getInt("cols", 80);
        int rows = call.getInt("rows", 24);
        ShellSession session = sessions.get(id);
        if (session != null) session.resize(cols, rows);
        call.resolve();
    }

    @PluginMethod
    public void kill(PluginCall call) {
        String id = call.getString("id");
        ShellSession session = sessions.remove(id);
        if (session != null) session.kill();
        call.resolve();
    }

    @PluginMethod
    public void restart(PluginCall call) {
        String id = call.getString("id");
        ShellSession old = sessions.remove(id);
        if (old != null) old.kill();
        create(call);
    }

    @PluginMethod
    public void getCapabilities(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("pty", PtyBridge.isAvailable());
        ret.put("fullBootstrap", linuxSetup.isFullBootstrap());
        ret.put("ready", linuxSetup.isReady());
        ret.put("prefix", linuxSetup.getPrefixDir().getAbsolutePath());
        ret.put("shell", linuxSetup.getShellPath());
        call.resolve(ret);
    }

    @PluginMethod
    public void clear(PluginCall call) {
        String id = call.getString("id");
        ShellSession session = sessions.get(id);
        if (session != null) session.clear();
        call.resolve();
    }
}
