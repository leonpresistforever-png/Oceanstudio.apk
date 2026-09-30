package studio.ocean.app.providers;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import androidx.browser.customtabs.CustomTabsIntent;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/** Loopback OAuth 2.0 with PKCE for provider connections (Custom Tab + manual URL fallback). */
public final class ProviderOAuthSession {
    public interface Callback {
        void onWaiting(String detail);
        void onSuccess(String accessToken, String refreshToken);
        void onFailure(String message);
    }

    private static final String GOOGLE_AUTH = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_SCOPE = "https://www.googleapis.com/auth/generative-language";

    private final Context context;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean finished = new AtomicBoolean();
    private ServerSocket server;
    private String verifier;
    private String challenge;
    private int redirectPort;

    public ProviderOAuthSession(Context context) {
        this.context = context.getApplicationContext();
    }

    public void startGoogle(Callback callback) {
        worker.execute(() -> {
            try {
                // Mandatory preflight gate: prevent launching broken loopback OAuth
                studio.ocean.app.providers.auth.AuthPreflight preflight = new studio.ocean.app.providers.auth.AuthPreflight(context);
                studio.ocean.app.providers.model.ProviderDescriptor desc = studio.ocean.app.providers.ProviderRegistry.find("google");
                studio.ocean.app.providers.auth.AuthPreflight.PreflightResult preflightResult =
                        preflight.validate(desc, studio.ocean.app.providers.model.AuthStrategy.OFFICIAL_OAUTH, null, null);
                if (!preflightResult.isReady) {
                    if (finished.compareAndSet(false, true)) {
                        callback.onFailure(preflightResult.failureTitle + ": " + preflightResult.failureMessage);
                    }
                    return;
                }

                preparePkce();
                redirectPort = bindLoopback();
                String redirect = "http://127.0.0.1:" + redirectPort + "/oauth/callback";
                String clientId = context.getString(studio.ocean.app.R.string.default_web_client_id);
                Uri auth = Uri.parse(GOOGLE_AUTH).buildUpon()
                        .appendQueryParameter("client_id", clientId)
                        .appendQueryParameter("redirect_uri", redirect)
                        .appendQueryParameter("response_type", "code")
                        .appendQueryParameter("scope", GOOGLE_SCOPE)
                        .appendQueryParameter("code_challenge", challenge)
                        .appendQueryParameter("code_challenge_method", "S256")
                        .appendQueryParameter("access_type", "offline")
                        .appendQueryParameter("prompt", "consent")
                        .build();
                callback.onWaiting("Waiting for Google sign-in on port " + redirectPort + "…");
                openCustomTab(auth);
                String code = awaitAuthorizationCode(redirect);
                if (code == null) throw new IOException("Authorization was not completed");
                JSONObject token = exchangeGoogle(code, redirect, clientId);
                String access = token.optString("access_token", "");
                if (access.isEmpty()) throw new IOException("Token response missing access_token");
                if (!finished.compareAndSet(false, true)) return;
                callback.onSuccess(access, token.optString("refresh_token", ""));
            } catch (Exception error) {
                if (finished.compareAndSet(false, true)) callback.onFailure(error.getMessage());
            } finally {
                closeServer();
            }
        });
    }

    public void completeWithRedirectUrl(String url, Callback callback) {
        worker.execute(() -> {
            try {
                Uri uri = Uri.parse(url.trim());
                String code = uri.getQueryParameter("code");
                if (code == null || code.isEmpty()) throw new IOException("Paste the full redirect URL that contains ?code=");
                if (verifier == null || challenge == null) preparePkce();
                String redirect = "http://127.0.0.1:" + (redirectPort > 0 ? redirectPort : 53682) + "/oauth/callback";
                String clientId = context.getString(studio.ocean.app.R.string.default_web_client_id);
                JSONObject token = exchangeGoogle(code, redirect, clientId);
                String access = token.optString("access_token", "");
                if (access.isEmpty()) throw new IOException("Token response missing access_token");
                if (!finished.compareAndSet(false, true)) return;
                callback.onSuccess(access, token.optString("refresh_token", ""));
            } catch (Exception error) {
                if (finished.compareAndSet(false, true)) callback.onFailure(error.getMessage());
            }
        });
    }

    public void cancel() {
        finished.set(true);
        closeServer();
    }

    private void preparePkce() throws Exception {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        verifier = Base64.encodeToString(random, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        challenge = Base64.encodeToString(digest, Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    private int bindLoopback() throws IOException {
        server = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
        return server.getLocalPort();
    }

    private void openCustomTab(Uri auth) {
        CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
        builder.setShowTitle(true);
        CustomTabsIntent tabs = builder.build();
        tabs.intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        tabs.launchUrl(context, auth);
    }

    private String awaitAuthorizationCode(String redirectUri) throws IOException {
        if (server == null) throw new IOException("Loopback server is not running");
        server.setSoTimeout(300_000);
        try (Socket socket = server.accept()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            String request = reader.readLine();
            String location = "http://127.0.0.1:" + redirectPort + "/oauth/done";
            String code = null;
            if (request != null && request.startsWith("GET ")) {
                int pathStart = request.indexOf(' ') + 1;
                int pathEnd = request.indexOf(' ', pathStart);
                String path = pathEnd > pathStart ? request.substring(pathStart, pathEnd) : "/";
                Uri uri = Uri.parse("http://127.0.0.1" + path);
                code = uri.getQueryParameter("code");
            }
            byte[] body = ("<!DOCTYPE html><html><body><p>Connected. Return to Ocean Studio.</p></body></html>")
                    .getBytes(StandardCharsets.UTF_8);
            String response = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "
                    + body.length + "\r\nConnection: close\r\n\r\n";
            OutputStream output = socket.getOutputStream();
            output.write(response.getBytes(StandardCharsets.US_ASCII));
            output.write(body);
            output.flush();
            return code;
        }
    }

    private JSONObject exchangeGoogle(String code, String redirect, String clientId) throws Exception {
        URL url = new URL(GOOGLE_TOKEN);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(20_000);
        connection.setReadTimeout(20_000);
        String body = "grant_type=authorization_code"
                + "&code=" + Uri.encode(code)
                + "&client_id=" + Uri.encode(clientId)
                + "&redirect_uri=" + Uri.encode(redirect)
                + "&code_verifier=" + Uri.encode(verifier);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
        int status = connection.getResponseCode();
        String payload = readStream(status >= 400 ? connection.getErrorStream() : connection.getInputStream());
        JSONObject json = new JSONObject(payload);
        if (status >= 400) throw new IOException(json.optString("error_description", json.toString()));
        return json;
    }

    private static String readStream(java.io.InputStream stream) throws IOException {
        if (stream == null) return "";
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }

    private void closeServer() {
        if (server != null) {
            try { server.close(); } catch (IOException ignored) { }
            server = null;
        }
    }
}
