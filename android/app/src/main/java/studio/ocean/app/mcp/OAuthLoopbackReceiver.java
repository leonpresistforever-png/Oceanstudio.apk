package studio.ocean.app.mcp;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

/** Loopback-only OAuth receiver. Binds before registration/browser launch. */
public final class OAuthLoopbackReceiver implements AutoCloseable {
    public interface Listener { void received(String callback); void failed(String message); }
    private final ServerSocket socket;
    private final String callbackPath;
    private final String returnUri;
    private final String callbackHost;
    private volatile boolean closed;

    public OAuthLoopbackReceiver() throws IOException {
        this(0, "/callback", null);
    }

    public OAuthLoopbackReceiver(int port, String path, String returnUri) throws IOException {
        this(port, path, returnUri, "127.0.0.1");
    }
    public OAuthLoopbackReceiver(int port, String path, String returnUri, String host) throws IOException {
        if (path == null || !path.matches("/[a-zA-Z0-9/_-]+")) throw new IOException("Invalid callback path");
        if (returnUri != null && !returnUri.matches("ocean://(gateway|mcp)/return\\?ticket=[a-f0-9]{64}"))
            throw new IOException("Invalid application return URI");
        this.callbackPath = path;
        this.returnUri = returnUri;
        if (!"127.0.0.1".equals(host) && !"localhost".equals(host)) throw new IOException("Callback must use loopback");
        this.callbackHost = host;
        socket = new ServerSocket(port, 4, InetAddress.getByName("127.0.0.1"));
        socket.setSoTimeout(1000);
    }

    public String redirectUri() { return "http://" + callbackHost + ":" + socket.getLocalPort() + callbackPath; }

    public void listen(String state, Listener listener) {
        Thread thread = new Thread(() -> {
            long deadline = System.currentTimeMillis() + 300000;
            try {
                while (!closed && System.currentTimeMillis() < deadline) {
                    try (Socket client = socket.accept()) {
                        client.setSoTimeout(3000);
                        BufferedReader input = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
                        String line = boundedLine(input);
                        String callback = acceptedCallback(line, redirectUri(), state);
                        boolean accepted = callback != null;
                        byte[] body = (accepted
                                ? "Authorization received. Return to OceanStudio while the connection is verified."
                                : "Invalid callback.").getBytes(StandardCharsets.UTF_8);
                        OutputStream out = client.getOutputStream();
                        out.write(("HTTP/1.1 " + (accepted ? (returnUri == null ? "200 OK" : "302 Found") : "400 Bad Request")
                                + (accepted && returnUri != null ? "\r\nLocation: " + returnUri : "")
                                + "\r\nContent-Type: text/plain; charset=utf-8\r\nCache-Control: no-store\r\nContent-Length: "
                                + body.length + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                        out.write(body);
                        out.flush();
                        if (accepted) { listener.received(callback); return; }
                    } catch (SocketTimeoutException ignored) { }
                }
                if (!closed) listener.failed("Authorization timed out. Please reconnect.");
            } catch (IOException error) {
                if (!closed) listener.failed("Authorization callback listener stopped.");
            } finally { close(); }
        }, "ocean-mcp-oauth-callback");
        thread.setDaemon(true);
        thread.start();
    }

    private static String boundedLine(Reader input) throws IOException {
        StringBuilder line = new StringBuilder();
        int ch;
        while ((ch = input.read()) != -1 && ch != '\n') {
            if (line.length() >= 8192) throw new IOException("Callback request too large");
            if (ch != '\r') line.append((char) ch);
        }
        return line.toString();
    }

    static String acceptedCallback(String request, String redirect, String state) {
        try {
            String[] parts = request.split(" ");
            if (parts.length != 3 || !"GET".equals(parts[0]) || !parts[2].startsWith("HTTP/")) return null;
            URI path = new URI(parts[1]);
            if (path.isAbsolute() || path.getRawAuthority() != null || !new URI(redirect).getPath().equals(path.getPath())
                    || path.getRawFragment() != null || path.getRawQuery() == null) return null;
            String returnedState = null;
            boolean result = false;
            boolean seenCode = false, seenError = false;
            for (String field : path.getRawQuery().split("&")) {
                String[] pair = field.split("=", 2);
                String key = URLDecoder.decode(pair[0], "UTF-8");
                String value = pair.length == 2 ? URLDecoder.decode(pair[1], "UTF-8") : "";
                if ("state".equals(key)) { if (returnedState != null) return null; returnedState = value; }
                if ("code".equals(key)) { if (seenCode || seenError) return null; seenCode = true; result = !value.isEmpty(); }
                if ("error".equals(key)) { if (seenError || seenCode) return null; seenError = true; result = !value.isEmpty(); }
            }
            return result && returnedState != null && java.security.MessageDigest.isEqual(
                    state.getBytes(StandardCharsets.UTF_8), returnedState.getBytes(StandardCharsets.UTF_8))
                    ? redirect + "?" + path.getRawQuery() : null;
        } catch (Exception invalid) { return null; }
    }

    @Override public void close() {
        closed = true;
        try { socket.close(); } catch (IOException ignored) { }
    }
}
