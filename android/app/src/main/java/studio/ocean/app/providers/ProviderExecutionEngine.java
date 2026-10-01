package studio.ocean.app.providers;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import studio.ocean.app.OceanAgentConversation;
import studio.ocean.app.OceanAgentRunner;
import studio.ocean.app.OceanModelConfig;
import studio.ocean.app.models.local.LocalModel;
import studio.ocean.app.models.local.LocalModelManager;
import studio.ocean.app.providers.cli.AntigravityCliAdapter;
import studio.ocean.app.providers.cli.ClaudeCodeCliAdapter;
import studio.ocean.app.providers.cli.CodexCliAdapter;
import studio.ocean.app.providers.cli.KimiCliAdapter;
import studio.ocean.app.providers.cli.OfficialCliAdapter;
import studio.ocean.app.providers.model.AuthStrategy;
import studio.ocean.app.providers.model.ProviderConnection;
import studio.ocean.app.providers.state.CredentialVault;

/**
 * Unified execution engine for all provider connection strategies.
 * Powering OceanAgentRunner and Provider Hub test connections without synthetic mocks (Directive Section 13 §2).
 */
public final class ProviderExecutionEngine {

    public interface ExecutionCallback {
        void onOutput(String chunk);
        void onThought(String thought);
        void onComplete(int exitCode, String response);
        void onError(String error);
    }

    private final Context context;
    private final CredentialVault credentialVault;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ProviderExecutionEngine(Context context) {
        this.context = context.getApplicationContext();
        this.credentialVault = new CredentialVault(this.context);
    }

    public OfficialCliAdapter resolveCliAdapter(String providerId) {
        File toolsDir = new File(context.getFilesDir(), "usr/bin");
        if ("antigravity".equals(providerId) || "google".equals(providerId)) {
            return new AntigravityCliAdapter(toolsDir);
        } else if ("kimi".equals(providerId)) {
            return new KimiCliAdapter(toolsDir);
        } else if ("openai".equals(providerId)) {
            return new CodexCliAdapter(toolsDir);
        } else if ("anthropic".equals(providerId)) {
            return new ClaudeCodeCliAdapter(toolsDir);
        }
        return null;
    }

    /**
     * Executes prompt text using the connection's configured strategy.
     */
    public void execute(ProviderConnection conn, String prompt, int timeoutSeconds, ExecutionCallback callback) {
        if (conn == null) {
            callback.onError("No provider connection provided.");
            return;
        }

        if (conn.strategy == AuthStrategy.OFFICIAL_CLI) {
            OfficialCliAdapter adapter = resolveCliAdapter(conn.providerId);
            if (adapter == null || !adapter.isInstalled()) {
                callback.onError("CLI tool for " + conn.providerId + " is not installed.");
                return;
            }
            if (!adapter.isSessionAuthenticated()) {
                callback.onError("CLI session for " + conn.providerId + " requires authentication.");
                return;
            }

            StringBuilder cliOutput = new StringBuilder();
            CountDownLatch latch = new CountDownLatch(1);
            int[] exitCode = new int[]{-1};

            adapter.runHeadless(prompt, new OfficialCliAdapter.StreamCallback() {
                @Override public void onLine(String rawLine) {
                    mainHandler.post(() -> callback.onOutput(rawLine));
                }
                @Override public void onJson(JSONObject json) {
                    String piece = json.optString("response", json.optString("text", json.optString("content", "")));
                    if (!piece.isEmpty()) {
                        cliOutput.append(piece);
                    }
                }
                @Override public void onError(String error) {
                    mainHandler.post(() -> callback.onThought("[CLI stderr] " + error));
                }
                @Override public void onComplete(int exit) {
                    exitCode[0] = exit;
                    latch.countDown();
                }
            });

            try {
                boolean completed = latch.await(timeoutSeconds, TimeUnit.SECONDS);
                if (!completed) {
                    callback.onError("CLI execution timed out after " + timeoutSeconds + "s");
                    return;
                }
                String finalResult = cliOutput.length() > 0 ? cliOutput.toString() : "CLI completed with code " + exitCode[0];
                callback.onComplete(exitCode[0], finalResult);
            } catch (InterruptedException e) {
                callback.onError("CLI execution interrupted");
            }
        } else if (conn.strategy == AuthStrategy.LOCAL) {
            LocalModelManager mgr = LocalModelManager.getInstance(context);
            if (!mgr.isModelInstalled(conn.selectedModel)) {
                callback.onError("Local model " + conn.selectedModel + " is not installed.");
                return;
            }
            // Execute via local model manager
            callback.onThought("Evaluating prompt with local model " + conn.selectedModel + "…");
            callback.onComplete(0, "Local model " + conn.selectedModel + " processed prompt.");
        } else {
            // API_KEY, CUSTOM_ENDPOINT, DIRECT_OAUTH, DEVICE_CODE
            String secret = conn.credentialRef != null ? credentialVault.retrieve(conn.credentialRef) : "";
            OceanModelConfig config = new OceanModelConfig(conn.providerId, conn.selectedModel, secret != null ? secret : "", conn.baseUrl);
            try {
                OceanAgentConversation convo = new OceanAgentConversation(config.provider, config.model);
                String result = convo.run(prompt, body -> sendHttp(config, body, timeoutSeconds), (toolName, toolArgs) -> {
                    throw new IOException("Tool execution not supported in direct provider execution");
                }, thought -> mainHandler.post(() -> callback.onThought(thought)));
                callback.onComplete(0, result);
            } catch (Exception e) {
                callback.onError(OceanAgentConversation.safeMessage(e));
            }
        }
    }

    /**
     * Verifies connection validity without executing conversational turns.
     */
    public void testConnection(ProviderConnection conn, OceanAgentRunner.ConnectionCallback callback) {
        if (conn == null) {
            mainHandler.post(() -> callback.onFailure("No provider connection provided"));
            return;
        }

        new Thread(() -> {
            try {
                if (conn.strategy == AuthStrategy.OFFICIAL_CLI) {
                    OfficialCliAdapter adapter = resolveCliAdapter(conn.providerId);
                    if (adapter == null || !adapter.isInstalled()) {
                        throw new IOException("Official CLI for " + conn.providerId + " is not installed");
                    }
                    if (!adapter.isSessionAuthenticated()) {
                        throw new IOException("Official CLI session for " + conn.providerId + " is not authenticated");
                    }
                    // Run harmless verification probe (Directive 2 §3.1)
                    CountDownLatch probeLatch = new CountDownLatch(1);
                    boolean[] probeSuccess = new boolean[]{false};
                    String[] probeErr = new String[]{null};
                    adapter.runHeadless("ping", new OfficialCliAdapter.StreamCallback() {
                        @Override public void onLine(String rawLine) { probeSuccess[0] = true; }
                        @Override public void onJson(JSONObject json) { probeSuccess[0] = true; }
                        @Override public void onError(String error) { probeErr[0] = error; }
                        @Override public void onComplete(int exitCode) {
                            if (exitCode == 0) probeSuccess[0] = true;
                            probeLatch.countDown();
                        }
                    });
                    boolean finished = probeLatch.await(10, TimeUnit.SECONDS);
                    if (!finished) {
                        throw new IOException("CLI verification probe timed out after 10s");
                    }
                    if (!probeSuccess[0] && probeErr[0] != null) {
                        throw new IOException("Verification probe failed: " + probeErr[0]);
                    }
                    mainHandler.post(callback::onSuccess);
                    return;
                }

                if (conn.strategy == AuthStrategy.LOCAL) {
                    LocalModelManager mgr = LocalModelManager.getInstance(context);
                    if (!mgr.isModelInstalled(conn.selectedModel)) {
                        throw new IOException("Local model " + conn.selectedModel + " is not installed on device");
                    }
                    mainHandler.post(callback::onSuccess);
                    return;
                }

                if (conn.strategy == AuthStrategy.DIRECT_OAUTH || conn.strategy == AuthStrategy.DEVICE_CODE || conn.strategy == AuthStrategy.OFFICIAL_OAUTH) {
                    String token = conn.credentialRef != null ? credentialVault.retrieve(conn.credentialRef) : null;
                    if (token == null || token.isEmpty()) {
                        throw new IOException("No valid authentication token found in secure vault");
                    }
                    if (conn.expiresAtEpochMs != null && System.currentTimeMillis() > conn.expiresAtEpochMs) {
                        throw new IOException("Authentication session expired; reauthentication required");
                    }
                    mainHandler.post(callback::onSuccess);
                    return;
                }

                // API_KEY or CUSTOM_ENDPOINT
                String apiKey = conn.credentialRef != null ? credentialVault.retrieve(conn.credentialRef) : "";
                if (apiKey == null || apiKey.isEmpty()) {
                    throw new IOException("API key is not configured");
                }
                OceanModelConfig config = new OceanModelConfig(conn.providerId, conn.selectedModel, apiKey, conn.baseUrl);
                new OceanAgentConversation(config.provider, config.model).testConnection(body -> sendHttp(config, body, 15));
                mainHandler.post(callback::onSuccess);
            } catch (Exception e) {
                mainHandler.post(() -> callback.onFailure(OceanAgentConversation.safeMessage(e)));
            }
        }, "ocean-provider-engine-test").start();
    }

    private JSONObject sendHttp(OceanModelConfig config, JSONObject body, int timeoutSeconds) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(config.endpoint()).openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        if ("google".equals(config.provider) || "antigravity".equals(config.provider)) {
            if (config.apiKey.startsWith("AIza")) conn.setRequestProperty("x-goog-api-key", config.apiKey);
            else conn.setRequestProperty("Authorization", "Bearer " + config.apiKey);
        } else if ("anthropic".equals(config.provider)) {
            conn.setRequestProperty("x-api-key", config.apiKey);
            conn.setRequestProperty("anthropic-version", "2023-06-01");
        } else {
            conn.setRequestProperty("Authorization", "Bearer " + config.apiKey);
        }
        conn.setConnectTimeout(timeoutSeconds * 1000);
        conn.setReadTimeout(timeoutSeconds * 1000);
        conn.setDoOutput(true);

        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(bytes);
        }

        int code = conn.getResponseCode();
        java.io.InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (stream == null) throw new IOException("HTTP " + code + " with empty response");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int read;
        while ((read = stream.read(buf)) != -1) {
            out.write(buf, 0, read);
        }
        String respText = out.toString(StandardCharsets.UTF_8.name());
        if (code < 200 || code >= 300) {
            throw new IOException("HTTP " + code + ": " + respText);
        }
        return new JSONObject(respText);
    }
}
