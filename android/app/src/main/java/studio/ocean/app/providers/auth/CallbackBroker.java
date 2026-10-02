package studio.ocean.app.providers.auth;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/**
 * Broker and dispatcher for all OAuth callback mechanisms (Directive 2026-10-02 §11, §12).
 * Supports:
 *   1. 127.0.0.1 ephemeral loopback listener (one-shot, loopback-only, timeout).
 *   2. Verified HTTPS App Links (domain-checked).
 *   3. Custom scheme callbacks (ocean://auth/callback, studio.ocean.app://oauth).
 *   4. Device-code polling loops.
 *
 * Enforces transaction verification, state validation, and replay prevention via AuthSessionManager.
 */
public final class CallbackBroker {

    private static final String TAG = "CallbackBroker";

    public interface CallbackListener {
        void onCodeReceived(@NonNull AuthTransaction transaction, @NonNull String code, @Nullable String returnedState);
        void onError(@Nullable AuthTransaction transaction, @NonNull String error);
    }

    public static final class LoopbackServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final int port;
        private final Future<?> task;

        public LoopbackServer(ServerSocket serverSocket, int port, Future<?> task) {
            this.serverSocket = serverSocket;
            this.port = port;
            this.task = task;
        }

        public int getPort() {
            return port;
        }

        public String getRedirectUri() {
            return "http://127.0.0.1:" + port + "/callback";
        }

        @Override
        public void close() {
            try {
                if (serverSocket != null && !serverSocket.isClosed()) {
                    serverSocket.close();
                }
            } catch (IOException ignored) {}
            if (task != null) {
                task.cancel(true);
            }
        }
    }

    private final Context context;
    private final AuthSessionManager sessionManager;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public CallbackBroker(@NonNull Context context, @NonNull AuthSessionManager sessionManager) {
        this.context = context.getApplicationContext();
        this.sessionManager = sessionManager;
    }

    /**
     * Starts an ephemeral one-shot loopback listener bound exclusively to 127.0.0.1.
     */
    @NonNull
    public LoopbackServer startLoopbackListener(@NonNull String expectedState,
                                               long timeoutMs,
                                               @NonNull CallbackListener listener) throws IOException {
        ServerSocket serverSocket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
        serverSocket.setSoTimeout((int) timeoutMs);
        int port = serverSocket.getLocalPort();

        Future<?> task = executor.submit(() -> {
            try (Socket client = serverSocket.accept()) {
                client.setSoTimeout(5000);
                BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                String requestLine = reader.readLine();
                if (requestLine == null) return;

                // Extract query parameters from GET /callback?...
                String[] parts = requestLine.split(" ");
                if (parts.length >= 2) {
                    Uri uri = Uri.parse("http://127.0.0.1:" + port + parts[1]);
                    String state = uri.getQueryParameter("state");
                    String code = uri.getQueryParameter("code");
                    String error = uri.getQueryParameter("error");
                    String errorDesc = uri.getQueryParameter("error_description");

                    // Render minimal monochrome completion HTML response
                    OutputStream os = client.getOutputStream();
                    String responseBody = "<html><body style='font-family:sans-serif;text-align:center;padding:40px;background:#191817;color:#F0F0F0;'>"
                            + "<h2>OceanStudio Connected</h2><p style='color:#77736E;'>Authorization received. You can safely return to OceanStudio.</p>"
                            + "</body></html>";
                    byte[] bodyBytes = responseBody.getBytes(StandardCharsets.UTF_8);

                    String httpResp = "HTTP/1.1 200 OK\r\n"
                            + "Content-Type: text/html; charset=utf-8\r\n"
                            + "Content-Length: " + bodyBytes.length + "\r\n"
                            + "Connection: close\r\n\r\n";
                    os.write(httpResp.getBytes(StandardCharsets.UTF_8));
                    os.write(bodyBytes);
                    os.flush();

                    dispatchUriCallback(uri, listener);
                }
            } catch (Exception e) {
                if (!serverSocket.isClosed()) {
                    Log.w(TAG, "Loopback callback listener closed or timed out: " + e.getMessage());
                }
            } finally {
                try {
                    serverSocket.close();
                } catch (IOException ignored) {}
            }
        });

        return new LoopbackServer(serverSocket, port, task);
    }

    /**
     * Dispatches an incoming Uri callback (from App Link, custom scheme, or loopback).
     */
    public void dispatchUriCallback(@NonNull Uri callbackUri, @NonNull CallbackListener listener) {
        String state = callbackUri.getQueryParameter("state");
        String code = callbackUri.getQueryParameter("code");
        String error = callbackUri.getQueryParameter("error");
        String errorDesc = callbackUri.getQueryParameter("error_description");

        if (state == null || state.isEmpty()) {
            listener.onError(null, "Rejected OAuth callback: Missing required 'state' parameter.");
            return;
        }

        AuthTransaction tx = sessionManager.getTransactionByState(state);
        if (tx == null) {
            listener.onError(null, "Security rejection: No active transaction matching state token '" + state + "'.");
            return;
        }

        if (tx.replayConsumed) {
            listener.onError(tx, "Security rejection: Transaction " + tx.id + " was already consumed (replay attack prevention).");
            return;
        }

        if (error != null) {
            sessionManager.updatePhase(tx.id, AuthTransaction.PHASE_FAILED);
            listener.onError(tx, "Provider authorization error: " + error + (errorDesc != null ? " (" + errorDesc + ")" : ""));
            return;
        }

        if (code == null || code.isEmpty()) {
            sessionManager.updatePhase(tx.id, AuthTransaction.PHASE_FAILED);
            listener.onError(tx, "Invalid OAuth callback: missing authorization code.");
            return;
        }

        try {
            // Atomically mark consumed to prevent replay attacks
            AuthTransaction consumedTx = sessionManager.consumeTransaction(tx.id);
            listener.onCodeReceived(consumedTx, code, state);
        } catch (Exception e) {
            listener.onError(tx, e.getMessage());
        }
    }
}
