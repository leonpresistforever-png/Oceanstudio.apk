package studio.ocean.app.providers.gateway;

import android.content.*;
import android.os.*;
import android.net.Uri;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import studio.ocean.app.mcp.OAuthLoopbackReceiver;
import studio.ocean.app.providers.model.*;
import studio.ocean.app.providers.state.*;
import studio.ocean.app.terminal.OceanTerminalRuntimeService;

/** Installs, owns and connects to the real upstream gateway; no synthetic account state. */
public final class OceanGatewayManager {
    public interface Listener {
        void progress(String message);
        void authorize(String url);
        void connected(ProviderConnection connection);
        void failed(String message);
    }
    private static OceanGatewayManager instance;
    public static synchronized OceanGatewayManager get(Context context) {
        if (instance == null) instance = new OceanGatewayManager(context.getApplicationContext());
        return instance;
    }
    public static String upstreamProvider(String id) {
        if ("antigravity".equals(id)) return "agy";
        if ("antigravity_ide".equals(id) || "antigravity_20".equals(id)) return "antigravity";
        if ("openai".equals(id)) return "codex";
        return null;
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Object startupLock = new Object();
    private final CredentialVault vault;
    private final File command, data;
    private volatile OceanTerminalRuntimeService service;
    private volatile OceanTerminalRuntimeService.CommandHandle daemon;
    private volatile int port;
    private volatile GatewayClient client;
    private volatile String returnTicket;
    private volatile OAuthLoopbackReceiver receiver;
    private volatile Listener activeListener;
    private volatile long daemonGeneration;

    private OceanGatewayManager(Context context) {
        this.context = context;
        vault = new CredentialVault(context);
        command = new File(context.getFilesDir(), "usr/bin/ocean-gateway");
        data = new File(context.getFilesDir(), "usr/var/lib/ocean-gateway");
        port = context.getSharedPreferences("ocean_gateway", 0).getInt("port", 20129);
    }
    public String baseUrl() { return "http://127.0.0.1:" + port + "/v1"; }

    public void installAndStart(Listener listener) {
        worker.execute(() -> {
            try { ensureReady(listener); listener.progress("Ocean gateway is running on 127.0.0.1:" + port); }
            catch (Exception error) { listener.failed(message(error)); }
        });
    }

    public void connect(ProviderDescriptor descriptor, Listener listener) {
        String upstream = upstreamProvider(descriptor.id);
        if (upstream == null) { listener.failed("No gateway adapter selected"); return; }
        synchronized (this) {
            if (activeListener != null) { listener.progress("Complete the account authorization already open in your browser."); return; }
            activeListener = listener;
        }
        worker.execute(() -> {
            try {
                if (!vault.isAvailable()) throw new IOException("Android credential encryption is unavailable");
                ensureReady(listener);
                // Reuse a previously authorized upstream account after app restart.
                JSONArray existing = client.connections(upstream).optJSONArray("connections");
                if (existing != null) for (int i = 0; i < existing.length(); i++) {
                    JSONObject account = existing.getJSONObject(i);
                    if (account.optBoolean("isActive", true)) {
                        try { verifyAccount(descriptor, upstream, account, listener); }
                        finally { finishAuthorization(); }
                        return;
                    }
                }
                if (activeListener != listener) return;
                returnTicket = randomHex();
                String returnUri = "ocean://gateway/return?ticket=" + returnTicket;
                receiver = new OAuthLoopbackReceiver("codex".equals(upstream) ? 1455 : 0,
                        "codex".equals(upstream) ? "/auth/callback" : "/callback", returnUri,
                        "codex".equals(upstream) ? "localhost" : "127.0.0.1");
                OAuthLoopbackReceiver current = receiver;
                JSONObject auth = client.authorize(upstream, current.redirectUri());
                String authUrl = auth.optString("authUrl"), state = auth.optString("state");
                String verifier = auth.optString("codeVerifier"), redirect = auth.optString("redirectUri");
                URI authorization = new URI(authUrl);
                if (!"https".equals(authorization.getScheme()) || authorization.getHost() == null
                        || authorization.getUserInfo() != null || state.isEmpty()
                        || !verifier.matches("[A-Za-z0-9._~-]{43,128}") || !current.redirectUri().equals(redirect))
                    throw new IOException("Gateway returned an invalid authorization transaction");
                Map<String,String> parameters = query(authorization.getRawQuery());
                if (!state.equals(parameters.get("state")) || !redirect.equals(parameters.get("redirect_uri")))
                    throw new IOException("Gateway authorization state or redirect did not match its callback listener");
                if ("authorization_code_pkce".equals(auth.optString("flowType"))) {
                    String challenge = android.util.Base64.encodeToString(MessageDigest.getInstance("SHA-256")
                            .digest(verifier.getBytes(StandardCharsets.US_ASCII)),
                            android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP | android.util.Base64.NO_PADDING);
                    if (!"S256".equals(parameters.get("code_challenge_method"))
                            || !challenge.equals(parameters.get("code_challenge")))
                        throw new IOException("Gateway PKCE challenge did not match its verifier");
                }
                current.listen(state, new OAuthLoopbackReceiver.Listener() {
                    @Override public void received(String callback) {
                        worker.execute(() -> {
                            try {
                                listener.progress("Authorization received. Connecting and checking your model…");
                                Map<String,String> values = query(new URI(callback).getRawQuery());
                                if (values.containsKey("error")) throw new IOException("Account authorization was declined: " + values.get("error"));
                                JSONObject exchange = client.exchange(upstream, values.get("code"), redirect, verifier, state);
                                if (!exchange.optBoolean("success")) throw new IOException("Gateway token exchange did not complete");
                                JSONObject account = exchange.getJSONObject("connection");
                                verifyAccount(descriptor, upstream, account, listener);
                            } catch (Exception error) { listener.failed(message(error)); }
                            finally { finishAuthorization(); }
                        });
                    }
                    @Override public void failed(String error) { finishAuthorization(); listener.failed(error); }
                });
                listener.progress("Sign in to " + descriptor.title + " in your browser. Ocean will reconnect automatically.");
                listener.authorize(authUrl);
            } catch (Exception error) { finishAuthorization(); listener.failed(message(error)); }
        });
    }

    public boolean acceptReturn(Uri uri) {
        if (uri == null || !"ocean".equals(uri.getScheme()) || !"gateway".equals(uri.getHost())
                || !"/return".equals(uri.getPath())) return false;
        String ticket = uri.getQueryParameter("ticket"), expected = returnTicket;
        if (ticket == null || expected == null || !MessageDigest.isEqual(ticket.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8))) return false;
        returnTicket = null;
        Listener listener = activeListener;
        if (listener != null) listener.progress("Verifying the authorized connection…");
        return true;
    }
    private synchronized void finishAuthorization() {
        if (receiver != null) receiver.close();
        receiver = null; activeListener = null;
        // The one-use return ticket contains no code or token; retain it until the browser returns.
    }
    public void cancelAuthorization() { finishAuthorization(); returnTicket = null; }

    public void ensureReady() throws Exception { ensureReady(null); }
    private void ensureReady(Listener listener) throws Exception {
        synchronized (startupLock) {
            String password = password();
            GatewayClient candidate = new GatewayClient(port);
            boolean repairRunningServer = false;
            if (password != null) try {
                candidate.login(password);
                candidate.connections("antigravity");
                client = candidate; return;
            } catch (GatewayClient.HttpFailure error) { repairRunningServer = error.status >= 500; }
              catch (Exception ignored) { }
            OceanTerminalRuntimeService runtime = bindRuntime();
            if (listener != null) listener.progress("Installing and checking Ocean gateway and Node.js…");
            // Bootstrap activates its prefix atomically. Install the command AFTER that activation.
            runCommand(runtime, "true", 900);
            prepareCommand();
            runInstall(runtime, listener);
            if (repairRunningServer) {
                if (listener != null) listener.progress("Repairing the gateway's Android dependency and restarting its server…");
                daemonGeneration++;
                OceanTerminalRuntimeService.CommandHandle previous = daemon;
                daemon = null; client = null;
                if (previous != null) previous.cancel();
                runCommand(runtime, shell(command.getAbsolutePath()) + " stop", 30);
            }
            password = password();
            if (password == null) throw new IOException("Gateway initialization did not create its private management credentials");
            if (daemon == null) {
                try (ServerSocket available = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))) { }
                catch (IOException busy) {
                    try (ServerSocket available = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) { port = available.getLocalPort(); }
                    context.getSharedPreferences("ocean_gateway", 0).edit().putInt("port", port).apply();
                }
                if (listener != null) listener.progress("Starting the local provider gateway…");
                File log = new File(data, "gateway.log");
                if (log.length() > 2 * 1024 * 1024) {
                    File previous = new File(data, "gateway.log.previous");
                    if (previous.exists()) previous.delete();
                    log.renameTo(previous);
                }
                final long generation = ++daemonGeneration;
                daemon = runtime.requestDaemonCommand("OCEAN_GATEWAY_PORT=" + port + " exec " + shell(command.getAbsolutePath())
                        + " serve >> " + shell(log.getAbsolutePath()) + " 2>&1",
                        new OceanTerminalRuntimeService.CommandCallback() {
                            @Override public void onOutput(byte[] bytes, int length) { }
                            @Override public void onExit(int code) { if (daemonGeneration == generation) { daemon = null; client = null; } }
                            @Override public void onFailure(Throwable error) { if (daemonGeneration == generation) { daemon = null; client = null; } }
                        });
            }
            candidate = new GatewayClient(port);
            Exception last = null;
            long deadline = System.currentTimeMillis() + 120000;
            while (System.currentTimeMillis() < deadline) {
                try { candidate.login(password); candidate.connections("antigravity"); client = candidate; return; }
                catch (Exception error) { last = error; Thread.sleep(500); }
            }
            throw new IOException("Ocean gateway did not become ready", last);
        }
    }

    private OceanTerminalRuntimeService bindRuntime() throws Exception {
        if (service != null) return service;
        CountDownLatch ready = new CountDownLatch(1);
        main.post(() -> {
            boolean bound = context.bindService(new Intent(context, OceanTerminalRuntimeService.class), new ServiceConnection() {
                @Override public void onServiceConnected(ComponentName name, IBinder binder) {
                    service = ((OceanTerminalRuntimeService.LocalBinder) binder).service(); ready.countDown();
                }
                @Override public void onServiceDisconnected(ComponentName name) { service = null; daemon = null; }
                @Override public void onNullBinding(ComponentName name) { ready.countDown(); }
            }, Context.BIND_AUTO_CREATE);
            if (!bound) ready.countDown();
        });
        if (!ready.await(15, TimeUnit.SECONDS) || service == null) throw new IOException("Ocean runtime service could not start");
        return service;
    }
    private void runInstall(OceanTerminalRuntimeService runtime, Listener listener) throws Exception {
        runCommand(runtime, shell(command.getAbsolutePath()) + " install", 1200);
    }
    private void runCommand(OceanTerminalRuntimeService runtime, String commandText, int seconds) throws Exception {
        CountDownLatch complete = new CountDownLatch(1);
        int[] exit = {-1}; Throwable[] failure = {null};
        StringBuilder output = new StringBuilder();
        runtime.requestCommand(commandText, null, seconds,
                new OceanTerminalRuntimeService.CommandCallback() {
                    @Override public void onOutput(byte[] bytes, int length) {
                        output.append(new String(bytes, 0, length, StandardCharsets.UTF_8));
                        if (output.length() > 8192) output.delete(0, output.length() - 8192);
                    }
                    @Override public void onExit(int code) { exit[0] = code; complete.countDown(); }
                    @Override public void onFailure(Throwable error) { failure[0] = error; complete.countDown(); }
                });
        if (!complete.await(seconds + 15L, TimeUnit.SECONDS)) throw new IOException("Gateway installation timed out");
        if (failure[0] != null || exit[0] != 0) throw new IOException("Gateway installation failed (exit " + exit[0] + "): " + output.toString().trim(), failure[0]);
    }
    private void prepareCommand() throws Exception {
        File helper = new File(context.getFilesDir(), "usr/share/ocean-gateway/prepare-runtime.mjs");
        helper.getParentFile().mkdirs();
        copyAsset("ocean/gateway/prepare-runtime.mjs", helper);
        command.getParentFile().mkdirs();
        File temporary = new File(command.getParentFile(), "ocean-gateway.new");
        try (InputStream input = context.getAssets().open("ocean/gateway/ocean-gateway"); OutputStream output = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
        if (!temporary.setExecutable(true, true) || !temporary.renameTo(command)) throw new IOException("Could not install the gateway command");
    }
    private void copyAsset(String asset, File destination) throws IOException {
        File temporary = new File(destination.getParentFile(), destination.getName() + ".new");
        try (InputStream input = context.getAssets().open(asset); FileOutputStream output = new FileOutputStream(temporary)) {
            byte[] bytes = new byte[8192]; int count;
            while ((count = input.read(bytes)) != -1) output.write(bytes, 0, count);
            output.getFD().sync();
        }
        if (!temporary.renameTo(destination)) throw new IOException("Could not install " + destination.getName());
    }
    private String password() throws IOException {
        File environment = new File(data, ".env");
        if (!environment.isFile()) return null;
        try (BufferedReader input = new BufferedReader(new FileReader(environment))) {
            String line;
            while ((line = input.readLine()) != null) if (line.startsWith("INITIAL_PASSWORD=")) return line.substring(17);
        }
        return null;
    }

    private void verifyAccount(ProviderDescriptor descriptor, String upstream, JSONObject account, Listener listener) throws Exception {
        String accountId = account.getString("id");
        listener.progress("Discovering account models and checking inference…");
        String ref = "gateway_key_" + accountId;
        String key = vault.retrieve(ref);
        if (key == null) { key = client.createInferenceKey(accountId); vault.store(ref, key); }
        JSONObject discovery = client.providerModels(accountId);
        JSONArray models = discovery.optJSONArray("models");
        if (models == null || models.length() == 0) throw new IOException("The provider returned no account models");
        List<ModelDescriptor> catalog = new ArrayList<>();
        for (int i = 0; i < models.length(); i++) {
            JSONObject model = models.optJSONObject(i);
            String id = model == null ? models.optString(i) : model.optString("id", model.optString("model"));
            if (id.isEmpty()) continue;
            String routed = id.contains("/") ? id : upstream + "/" + id;
            catalog.add(new ModelDescriptor(routed, model == null ? id : model.optString("name", id),
                    model == null ? 0 : model.optInt("context_length", model.optInt("contextWindow", 0)), false,
                    model != null && model.optBoolean("supportsTools", false),
                    model != null && model.optBoolean("supportsVision", false), discovery.optString("source", "gateway")));
        }
        if (catalog.isEmpty()) throw new IOException("The provider model catalogue contained no model IDs");
        catalog.sort((a,b) -> Integer.compare(modelPriority(a.id), modelPriority(b.id)));
        String selected = null; Exception last = null;
        for (int i = 0; i < Math.min(catalog.size(), 3); i++) {
            ModelDescriptor model = catalog.get(i);
            try {
                JSONObject response = client.completion(key, model.id);
                JSONArray choices = response.optJSONArray("choices");
                JSONObject message = choices != null && choices.length() > 0 ? choices.getJSONObject(0).optJSONObject("message") : null;
                if (message == null || (message.optString("content", "").trim().isEmpty()
                        && message.optJSONArray("tool_calls") == null)) throw new IOException("Provider returned no completion");
                selected = model.id; break;
            } catch (Exception error) { last = error; }
        }
        if (selected == null) throw new IOException("Account authorization completed but the inference check failed", last);
        String display = account.optString("email", account.optString("displayName", descriptor.title));
        QuotaSnapshot quota;
        try { quota = parseQuota(client.quota(accountId), selected); }
        catch (Exception unavailable) { quota = QuotaSnapshot.unknown("", "gateway/provider-quota"); }
        List<ModelDescriptor> validated = new ArrayList<>();
        for (ModelDescriptor model : catalog) validated.add(new ModelDescriptor(model.id, model.name,
                model.contextLength, model.id.equals(selected), model.supportsTools, model.supportsVision, model.cooldownStatus));
        ProviderConnection connection = new ProviderConnection("gateway_" + accountId, descriptor.id, display,
                AuthStrategy.GATEWAY, ConnectionStatus.CONNECTED, baseUrl(), selected, ref, "gateway:" + accountId,
                Collections.emptyList(), null, quota, validated, System.currentTimeMillis());
        new ProviderConnectionStore(context).save(connection);
        configureFallback();
        listener.connected(connection);
    }
    private static int modelPriority(String model) {
        String id = model.toLowerCase(Locale.ROOT);
        if (id.contains("flash") && !id.contains("image")) return 0;
        if (id.contains("mini")) return 1;
        if (id.contains("sonnet")) return 2;
        if (id.contains("image") || id.contains("vision")) return 9;
        return 3;
    }
    public QuotaSnapshot refreshQuota(ProviderConnection connection) throws Exception {
        ensureReady();
        QuotaSnapshot quota = parseQuota(client.quota(accountId(connection)), connection.selectedModel);
        new ProviderConnectionStore(context).save(connection.withQuota(quota));
        return quota;
    }
    public void disconnect(ProviderConnection connection, Runnable complete, Listener listener) {
        worker.execute(() -> {
            try {
                if ("gateway_auto".equals(connection.id)) {
                    new ProviderConnectionStore(context).delete(connection.id);
                    vault.delete(connection.credentialRef); complete.run(); return;
                }
                ensureReady(); client.disconnect(accountId(connection));
                new ProviderConnectionStore(context).delete(connection.id);
                vault.delete(connection.credentialRef); configureFallback(); complete.run();
            } catch (Exception error) { listener.failed(message(error)); }
        });
    }
    private void configureFallback() throws Exception {
        ProviderConnectionStore store = new ProviderConnectionStore(context);
        JSONArray models = new JSONArray(), accounts = new JSONArray();
        for (ProviderConnection connection : store.listAll()) {
            if (connection.strategy != AuthStrategy.GATEWAY || "gateway_auto".equals(connection.id)
                    || connection.status != ConnectionStatus.CONNECTED) continue;
            String id = accountId(connection);
            accounts.put(id);
            models.put(new JSONObject().put("kind", "model").put("model", connection.selectedModel)
                    .put("connectionId", id));
        }
        if (models.length() < 2) { store.delete("gateway_auto"); vault.delete("gateway_auto_key"); return; }
        String model = client.configureFallback(models);
        String key = client.createFallbackKey(accounts);
        vault.store("gateway_auto_key", key);
        store.save(new ProviderConnection("gateway_auto", "custom", "Ocean Auto", AuthStrategy.GATEWAY,
                ConnectionStatus.CONNECTED, baseUrl(), model, "gateway_auto_key", "gateway:auto",
                Collections.emptyList(), null, QuotaSnapshot.unknown("Connected accounts", "gateway/provider-quota"),
                Collections.singletonList(new ModelDescriptor(model, "Ocean Auto", 0, true, false, false, "Connected accounts")),
                System.currentTimeMillis()));
    }
    private static String accountId(ProviderConnection connection) {
        if (connection.cliSessionRef == null || !connection.cliSessionRef.startsWith("gateway:"))
            throw new IllegalArgumentException("Missing gateway account identity");
        return GatewayClient.safeId(connection.cliSessionRef.substring(8));
    }
    public static QuotaSnapshot parseQuota(JSONObject response, String model) {
        return GatewayQuota.parse(response, model);
    }
    private static Map<String,String> query(String raw) throws Exception {
        Map<String,String> result = new HashMap<>();
        if (raw != null) for (String field : raw.split("&")) {
            String[] part = field.split("=", 2);
            String name = URLDecoder.decode(part[0], "UTF-8");
            if (result.put(name, part.length > 1 ? URLDecoder.decode(part[1], "UTF-8") : "") != null)
                throw new IOException("Duplicate authorization parameter");
        }
        return result;
    }
    private static String randomHex() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        StringBuilder hex = new StringBuilder(); for (byte value : bytes) hex.append(String.format("%02x", value));
        return hex.toString();
    }
    private static String shell(String text) { return "'" + text.replace("'", "'\"'\"'") + "'"; }
    private static String message(Exception error) {
        String result = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        if (error.getCause() != null && error.getCause().getMessage() != null) result += ": " + error.getCause().getMessage();
        return result;
    }
}
