package studio.ocean.app.models.local;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.json.*;

/** Official Ollama HTTP API: start server, upload verified GGUF, create, load, infer. */
public final class OllamaClient {
    private final String origin;
    public OllamaClient(int port) {
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Invalid local port");
        origin = "http://127.0.0.1:" + port;
    }
    public String baseUrl() { return origin + "/v1"; }
    public JSONObject version() throws Exception { return request("GET", "/api/version", null, null, 10); }
    public String importModel(String id, File gguf, int context) throws Exception {
        String alias = "ocean-" + id;
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new FileInputStream(gguf)) {
            byte[] bytes = new byte[65536]; int count;
            while ((count = in.read(bytes)) != -1) sha.update(bytes, 0, count);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : sha.digest()) hex.append(String.format(java.util.Locale.US, "%02x", b));
        String digest = "sha256:" + hex;
        try {
            if (request("POST", "/api/show", new JSONObject().put("model", alias), null, 20)
                    .optString("modelfile").contains(hex)) return alias;
        } catch (IOException missing) { }
        try { request("HEAD", "/api/blobs/" + digest, null, null, 10); }
        catch (IOException missing) { request("POST", "/api/blobs/" + digest, null, gguf, 180); }
        JSONObject created = request("POST", "/api/create", new JSONObject().put("model", alias)
                .put("files", new JSONObject().put(gguf.getName(), digest))
                .put("parameters", new JSONObject().put("num_ctx", context).put("num_gpu", 0))
                .put("stream", false), null, 180);
        if (!"success".equals(created.optString("status"))) throw new IOException("Ollama model registration did not finish");
        return alias;
    }
    public void load(String model) throws Exception {
        request("POST", "/api/generate", new JSONObject().put("model", model)
                .put("prompt", "").put("keep_alive", -1).put("stream", false), null, 180);
    }
    public String infer(String model) throws Exception {
        JSONObject response = request("POST", "/v1/chat/completions", new JSONObject().put("model", model)
                .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", "Reply with OK")))
                .put("max_tokens", 8).put("stream", false), null, 120);
        JSONArray choices = response.optJSONArray("choices");
        if (choices == null || choices.length() == 0) throw new IOException("Ollama returned no generated text");
        JSONObject message = choices.getJSONObject(0).optJSONObject("message");
        String text = message == null ? "" : message.optString("content", "").trim();
        if (text.isEmpty()) throw new IOException("Ollama returned empty generated text");
        return text;
    }
    public void unload(String model) throws Exception {
        request("POST", "/api/generate", new JSONObject().put("model", model).put("keep_alive", 0)
                .put("stream", false), null, 30);
    }
    private JSONObject request(String method, String path, JSONObject body, File upload, int timeout) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(origin + path).openConnection(Proxy.NO_PROXY);
        connection.setInstanceFollowRedirects(false); connection.setConnectTimeout(3000);
        connection.setReadTimeout(timeout * 1000); connection.setRequestMethod(method);
        try {
            if (body != null || upload != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", upload == null ? "application/json" : "application/octet-stream");
                byte[] payload = body == null ? null : body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(upload == null ? payload.length : upload.length());
                try (OutputStream out = connection.getOutputStream()) {
                    if (upload == null) out.write(payload);
                    else try (InputStream in = new FileInputStream(upload)) {
                        byte[] bytes = new byte[65536]; int count;
                        while ((count = in.read(bytes)) != -1) out.write(bytes, 0, count);
                    }
                }
            }
            int status = connection.getResponseCode();
            InputStream input = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            if (input != null) try (InputStream in = input) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = in.read(buffer)) != -1) {
                    if (bytes.size() + count > 2 * 1024 * 1024) throw new IOException("Ollama response exceeded limit");
                    bytes.write(buffer, 0, count);
                }
            }
            String text = bytes.toString("UTF-8");
            if (status < 200 || status >= 300) throw new IOException("Ollama HTTP " + status + " " + path
                    + (text.isEmpty() ? "" : ": " + text.substring(0, Math.min(512, text.length()))));
            return text.trim().isEmpty() ? new JSONObject() : new JSONObject(text);
        } finally { connection.disconnect(); }
    }
}
