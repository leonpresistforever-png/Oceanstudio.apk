package studio.ocean.app;

import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import studio.ocean.app.terminal.OceanTerminalActivity;
import studio.ocean.app.terminal.OceanTerminalRuntimeService;
import studio.ocean.app.runtime.RuntimePortsActivity;

/** Connects explicit provider tool calls to the native runtime; prose is never executable. */
public final class OceanAgentRunner {
    public interface ConnectionCallback { void onSuccess(); void onFailure(String error); }
    public interface AgentCallback {
        void onThought(String thought);
        void onToolStart(String toolName, String command);
        void onToolOutput(String outputChunk);
        void onToolComplete(int exitCode);
        void onResponse(String response);
        void onError(String error);
    }

    private final Context context;
    private final OceanByokManager byokManager;
    private final OceanAgentSettings agentSettings;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile Thread worker;
    private volatile HttpURLConnection activeConnection;
    private OceanAgentConversation conversation;
    private String conversationDigest;

    public OceanAgentRunner(Context context) {
        this.context = context.getApplicationContext();
        byokManager = new OceanByokManager(this.context);
        agentSettings = new OceanAgentSettings(this.context);
    }
    public boolean isRunning() { return running.get(); }
    public void cancel() {
        Thread active = worker;
        if (active != null) active.interrupt();
        HttpURLConnection connection = activeConnection;
        if (connection != null) new Thread(connection::disconnect, "ocean-cancel-http").start();
    }
    public void resetConversation() {
        if (running.get()) throw new IllegalStateException("Stop the current request before starting a new chat");
        conversation = null;
        conversationDigest = null;
    }

    public void processPrompt(String prompt, AgentCallback callback) {
        if (prompt == null || prompt.trim().isEmpty()) return;
        if (!running.compareAndSet(false, true)) {
            mainHandler.post(() -> callback.onError("Another request is running. Stop it before starting a new one."));
            return;
        }
        CrashSurvival.mark("START_AGENT_WORKER");
        worker = new Thread(() -> {
            try {
                CrashSurvival.mark("AGENT_WORKER_RUNNING");
                String text = prompt.trim(), result;
                if (OceanAgentRequests.opensTerminal(text)) {
                    openTerminal(callback);
                    result = "Ocean Terminal opened.";
                } else {
                    String command = OceanAgentRequests.explicitCommand(text);
                    if (command != null) {
                        if (!pluginConnected("terminal")) throw new IOException("Ocean Terminal plugin is disconnected in Plugins.");
                        JSONObject execution = runTerminal(command, null, agentSettings.commandTimeoutSeconds(), callback);
                        result = execution.optInt("exit_code", -1) == 0 ? "Command completed. Output is shown above."
                                : "Command exited with code " + execution.optInt("exit_code", -1) + ". " + execution.optString("error", "Review its output above.");
                    } else {
                        if (!byokManager.isVerified()) throw new IOException("Connect a model in BYOK Models & APIs using Save & Test Connection.");
                        OceanModelConfig config = configuredModel();
                        String digest = byokManager.configurationDigest()+"|"+agentSettings.signature();
                        if (conversation == null || !digest.equals(conversationDigest)) {
                            conversation = new OceanAgentConversation(config.provider, config.model, agentSettings.temperature(), agentSettings.topP(), agentSettings.maxTokens(), agentSettings.maxRounds(), agentSettings.maxToolCalls(), agentSettings.keepSessionAlive(), agentSettings.reasoningEffort(), agentSettings.userInstructions());
                            conversationDigest = digest;
                        }
                        status(callback, "Working with " + config.model + "…");
                        result = conversation.run(text, body -> { CrashSurvival.mark("PROVIDER_REQUEST"); JSONObject reply=send(config,body); CrashSurvival.mark("PROVIDER_RESPONSE_RECEIVED"); return reply; }, (name, args) -> {
                            CrashSurvival.mark("EXECUTE_AGENT_TOOL");
                            if (name.equals("open_terminal")) { if(!pluginConnected("terminal")) throw new IOException("Ocean Terminal plugin is disconnected."); return openTerminal(callback); }
                            if (name.equals("device_status") || name.equals("list_android_apps") || name.equals("open_android_app") || name.equals("inspect_android_screen") || name.equals("capture_android_screen") || name.equals("interact_android_screen")) {
                                if(!pluginConnected("device")) throw new IOException("Device Access plugin is disconnected.");
                                return runRuntimeTool("Device control", name, callback, () -> studio.ocean.app.device.DeviceControlService.execute(context,name,args));
                            }
                            if (name.equals("list_runtime_ports")) { if(!pluginConnected("runtime")) throw new IOException("Runtime Ports plugin is disconnected."); return runRuntimeTool("Runtime ports", "Scan Ocean listeners", callback, RuntimePortsActivity::listForAgent); }
                            if (name.equals("open_runtime_port")) { if(!pluginConnected("runtime")) throw new IOException("Runtime Ports plugin is disconnected."); return runRuntimeTool("Open runtime port", "localhost:" + args.getInt("port"), callback,
                                    () -> RuntimePortsActivity.openForAgent(context, args.getInt("port"), args.optString("path", "/"))); }
                            if (name.equals("interact_runtime_page")) { if(!pluginConnected("runtime")) throw new IOException("Runtime Ports plugin is disconnected."); return runRuntimeTool("Runtime page", args.getString("action"), callback,
                                    () -> RuntimePortsActivity.interactForAgent(args)); }
                            if(name.equals("ocean_forge")){
                                if(!pluginConnected("forge")) throw new IOException("Ocean Forge plugin is disconnected.");
                                if(!pluginConnected("terminal")) throw new IOException("Ocean Terminal plugin is disconnected.");
                                String action=args.getString("action");
                                String commandText="ocean-forge "+action;
                                if(action.equals("checkpoint")&&args.has("label")) commandText+=" "+shellQuote(args.getString("label"));
                                int timeout=(action.equals("build")||action.equals("test"))?300:120;
                                return runTerminal(commandText,null,timeout,callback);
                            }
                            if(name.equals("ocean_forge_workspace")){
                                if(!pluginConnected("forge")) throw new IOException("Ocean Forge plugin is disconnected.");
                                return runRuntimeTool("Forge workspace",args.getString("action"),callback,
                                        () -> OceanForgeWorkspace.execute(context,args));
                            }
                            if(!pluginConnected("terminal")) throw new IOException("Ocean Terminal plugin is disconnected.");
                            return runTerminal(args.getString("command"), args.optString("cwd", null), args.optInt("timeout_seconds", agentSettings.commandTimeoutSeconds()), callback);
                        }, thought -> status(callback, thought));
                    }
                }
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                String response = result;
                mainHandler.post(() -> callback.onResponse(response));
            } catch (Exception error) {
                boolean stopped = error instanceof InterruptedException || Thread.currentThread().isInterrupted();
                String detail = stopped ? "Stopped. Commands that already finished are shown above." : OceanAgentConversation.safeMessage(error);
                mainHandler.post(() -> callback.onError(detail));
            } finally {
                worker = null;
                running.set(false);
            }
        }, "ocean-agent");
        worker.start();
    }

    private void status(AgentCallback callback, String text) { mainHandler.post(() -> callback.onThought(text)); }
    private OceanModelConfig configuredModel() {
        return new OceanModelConfig(byokManager.getProvider(), byokManager.getModel(), byokManager.getApiKey(), byokManager.getBaseUrl());
    }
    public void testConnection(ConnectionCallback callback) {
        final String digest = byokManager.configurationDigest();
        new Thread(() -> {
            try {
                OceanModelConfig config = configuredModel();
                new OceanAgentConversation(config.provider, config.model).testConnection(body -> send(config, body));
                mainHandler.post(() -> {
                    if (!digest.equals(byokManager.configurationDigest())) { callback.onFailure("Settings changed during the test. Test the current settings again."); return; }
                    byokManager.markVerified(digest);
                    callback.onSuccess();
                });
            } catch (Exception error) {
                mainHandler.post(() -> callback.onFailure(OceanAgentConversation.safeMessage(error)));
            }
        }, "ocean-byok-test").start();
    }

    private JSONObject send(OceanModelConfig config, JSONObject body) throws Exception {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        HttpURLConnection conn = (HttpURLConnection) new URL(config.endpoint()).openConnection();
        // Never forward a saved API key to a redirect target.
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        if (config.provider.equals("google")) conn.setRequestProperty("x-goog-api-key", config.apiKey);
        else if (config.provider.equals("anthropic")) {
            conn.setRequestProperty("x-api-key", config.apiKey);
            conn.setRequestProperty("anthropic-version", "2023-06-01");
        } else conn.setRequestProperty("Authorization", "Bearer " + config.apiKey);
        conn.setConnectTimeout(agentSettings.connectTimeoutMs());
        conn.setReadTimeout(agentSettings.antiTimeout()?agentSettings.readTimeoutMs():Math.min(agentSettings.readTimeoutMs(),60000));
        conn.setDoOutput(true);
        boolean agentRequest = Thread.currentThread() == worker;
        if (agentRequest) activeConnection = conn;
        try {
            try (OutputStream out = conn.getOutputStream()) { out.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
            int code = conn.getResponseCode();
            InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (stream != null) try (InputStream input = stream) {
                byte[] buffer = new byte[8192]; int length;
                while ((length = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
                    if (bytes.size() + length > 2 * 1024 * 1024) throw new IOException("Provider response exceeded the size limit");
                    bytes.write(buffer, 0, length);
                }
            }
            String text = new String(bytes.toByteArray(), StandardCharsets.UTF_8);
            if (code < 200 || code >= 300) {
                String detail = text;
                try { detail = new JSONObject(text).getJSONObject("error").optString("message", text); } catch (Exception ignored) {}
                detail = detail.replace(config.apiKey, "[redacted]");
                if (detail.length() > 600) detail = detail.substring(0, 600);
                if (code == 404) detail += " Check the model ID in BYOK Models & APIs; a provider name such as google is not a model ID.";
                throw new IOException("Provider HTTP " + code + ": " + detail);
            }
            return new JSONObject(text);
        } finally {
            if (agentRequest) activeConnection = null;
            conn.disconnect();
        }
    }

    private boolean pluginConnected(String id) { return context.getSharedPreferences("ocean_plugin_state",Context.MODE_PRIVATE).getBoolean("connected_"+id,true); }
    private static String shellQuote(String value){ return "'"+value.replace("'","'\\''")+"'"; }

    private JSONObject openTerminal(AgentCallback callback) throws Exception {
        CountDownLatch ready = new CountDownLatch(1);
        Throwable[] failure = new Throwable[1];
        mainHandler.post(() -> {
            callback.onToolStart("Open terminal", "Ocean Terminal");
            try { context.startActivity(new Intent(context, OceanTerminalActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (Exception error) { failure[0] = error; }
            callback.onToolComplete(failure[0] == null ? 0 : -1);
            ready.countDown();
        });
        if (!ready.await(10, TimeUnit.SECONDS)) throw new IOException("The terminal screen did not respond");
        if (failure[0] != null) throw new IOException("Could not open terminal", failure[0]);
        return new JSONObject().put("opened", true).put("exit_code", 0);
    }

    private interface RuntimeAction { JSONObject run() throws Exception; }
    private JSONObject runRuntimeTool(String name, String detail, AgentCallback callback, RuntimeAction action) throws Exception {
        mainHandler.post(() -> callback.onToolStart(name, detail));
        int exit = -1;
        try {
            JSONObject result = action.run();
            exit = result.has("error") ? -1 : result.optInt("exit_code", 0);
            JSONObject visible = new JSONObject(result.toString()); visible.remove("image_base64");
            String output = visible.toString();
            if (output.length() > 12000) output = output.substring(0, 12000) + "…";
            String finalOutput = output;
            mainHandler.post(() -> callback.onToolOutput(finalOutput));
            return result;
        } finally {
            int finalExit = exit;
            mainHandler.post(() -> callback.onToolComplete(finalExit));
        }
    }

    /** A worker awaits results; service callbacks and all UI events stay on the main thread. */
    private JSONObject runTerminal(String command, String cwd, int timeoutSeconds, AgentCallback callback) throws Exception {
        OceanAgentConversation.validateTool("run_terminal_command", new JSONObject().put("command", command).put("timeout_seconds", timeoutSeconds));
        CountDownLatch completed = new CountDownLatch(1);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        AtomicBoolean disposed = new AtomicBoolean();
        boolean[] bound = {false}, truncated = {false};
        int[] exitCode = {-1};
        Throwable[] failure = new Throwable[1];
        OceanTerminalRuntimeService.CommandHandle[] handle = {null};
        ServiceConnection connection = new ServiceConnection() {
            @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                if (disposed.get()) return;
                try {
                    OceanTerminalRuntimeService service = ((OceanTerminalRuntimeService.LocalBinder) binder).service();
                    handle[0] = service.requestCommand(command, cwd, timeoutSeconds, new OceanTerminalRuntimeService.CommandCallback() {
                        @Override public void onOutput(byte[] bytes, int length) {
                            if (disposed.get()) return;
                            int keep = Math.min(length, Math.max(0, 32768 - output.size()));
                            output.write(bytes, 0, keep);
                            if (keep < length) truncated[0] = true;
                            if (keep > 0) { String chunk = new String(bytes, 0, keep, StandardCharsets.UTF_8); mainHandler.post(() -> callback.onToolOutput(chunk)); }
                        }
                        @Override public void onExit(int code) { exitCode[0] = code; completed.countDown(); }
                        @Override public void onFailure(Throwable error) { failure[0] = error; completed.countDown(); }
                    });
                } catch (Exception error) { failure[0] = error; completed.countDown(); }
            }
            @Override public void onServiceDisconnected(ComponentName name) { failure[0] = new IOException("Terminal service disconnected"); completed.countDown(); }
            @Override public void onNullBinding(ComponentName name) { failure[0] = new IOException("Terminal service unavailable"); completed.countDown(); }
            @Override public void onBindingDied(ComponentName name) { failure[0] = new IOException("Terminal service binding ended"); completed.countDown(); }
        };
        mainHandler.post(() -> {
            if (disposed.get()) return;
            callback.onToolStart("Terminal", command);
            try {
                Intent intent = new Intent(context, OceanTerminalRuntimeService.class);
                bound[0] = context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
                if (!bound[0]) throw new IOException("Ocean Terminal service is unavailable");
            } catch (Exception error) { failure[0] = error; completed.countDown(); }
        });
        try {
            if (!completed.await(timeoutSeconds + 15L, TimeUnit.SECONDS)) throw new IOException("Terminal did not complete before its deadline");
            if (failure[0] != null) throw new IOException("Terminal: " + OceanAgentConversation.safeMessage(failure[0]));
            String text = new String(output.toByteArray(), StandardCharsets.UTF_8);
            if (truncated[0]) text += "\n[Output truncated to 32 KiB]";
            JSONObject result = new JSONObject().put("command", command).put("output", text)
                    .put("exit_code", exitCode[0]).put("output_truncated", truncated[0]);
            if (handle[0] != null && handle[0].timedOut()) result.put("timed_out", true).put("error", "Command exceeded " + timeoutSeconds + " seconds and was stopped");
            return result;
        } catch (InterruptedException error) { exitCode[0] = 130; throw error; }
        finally {
            disposed.set(true);
            int code = exitCode[0];
            mainHandler.post(() -> {
                if (handle[0] != null) handle[0].cancel();
                if (bound[0]) { context.unbindService(connection); bound[0] = false; }
                callback.onToolComplete(code);
            });
        }
    }
}
