package studio.ocean.app.terminal;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Base64;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import androidx.core.content.FileProvider;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/**
 * Lightweight native IPC server providing HTTP and WebSocket APIs
 * for CLI tools (ocean-open, ocean-share, ocean-api, etc.).
 * Listens strictly on 127.0.0.1:8088.
 */
public final class OceanIpcServer {
    public static final int DEFAULT_PORT = 8088;
    private static final String NOTIFICATION_CHANNEL_ID = "ocean_ipc_notifications";
    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Set<Socket> wsClients = Collections.synchronizedSet(new HashSet<>());
    private ServerSocket serverSocket;
    private volatile boolean running;

    public OceanIpcServer(Context context) {
        this.context = context.getApplicationContext();
        ensureNotificationChannel();
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        executor.execute(this::runServer);
    }

    public synchronized void stop() {
        running = false;
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (Throwable ignored) {}
            serverSocket = null;
        }
        synchronized (wsClients) {
            for (Socket s : wsClients) {
                try { s.close(); } catch (Throwable ignored) {}
            }
            wsClients.clear();
        }
    }

    private void runServer() {
        try {
            serverSocket = new ServerSocket(DEFAULT_PORT, 50, InetAddress.getByName("127.0.0.1"));
            TerminalDiagnosticBundle.log("startup.log", "[IPC] OceanIpcServer listening on 127.0.0.1:" + DEFAULT_PORT);
            while (running && !serverSocket.isClosed()) {
                try {
                    Socket client = serverSocket.accept();
                    executor.execute(() -> handleClient(client));
                } catch (Throwable t) {
                    if (!running) break;
                }
            }
        } catch (Throwable t) {
            TerminalDiagnosticBundle.log("startup.log", "[IPC] Server start error: " + t.getMessage());
        }
    }

    private void handleClient(Socket socket) {
        try {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

            String requestLine = reader.readLine();
            if (requestLine == null) { socket.close(); return; }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) { socket.close(); return; }

            String method = parts[0].toUpperCase();
            String path = parts[1];

            // Read headers
            String line;
            int contentLength = 0;
            String wsKey = null;
            boolean isWebSocketUpgrade = false;

            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                String lower = line.toLowerCase();
                if (lower.startsWith("content-length:")) {
                    contentLength = Integer.parseInt(line.split(":", 2)[1].trim());
                } else if (lower.startsWith("sec-websocket-key:")) {
                    wsKey = line.split(":", 2)[1].trim();
                } else if (lower.startsWith("upgrade:") && line.toLowerCase().contains("websocket")) {
                    isWebSocketUpgrade = true;
                }
            }

            // Handle WebSocket Upgrade
            if (isWebSocketUpgrade && wsKey != null) {
                handleWebSocketHandshake(wsKey, out);
                wsClients.add(socket);
                handleWebSocketConnection(socket, in);
                return;
            }

            // Read request body if present
            String body = "";
            if (contentLength > 0) {
                char[] buf = new char[contentLength];
                int read = 0;
                while (read < contentLength) {
                    int r = reader.read(buf, read, contentLength - read);
                    if (r < 0) break;
                    read += r;
                }
                body = new String(buf, 0, read);
            }

            // Route HTTP endpoints
            JSONObject responseJson = dispatch(method, path, body);
            byte[] responseBytes = responseJson.toString().getBytes(StandardCharsets.UTF_8);

            String httpResponse = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/json; charset=utf-8\r\n" +
                    "Access-Control-Allow-Origin: *\r\n" +
                    "Content-Length: " + responseBytes.length + "\r\n\r\n";
            out.write(httpResponse.getBytes(StandardCharsets.US_ASCII));
            out.write(responseBytes);
            out.flush();
            socket.close();
        } catch (Throwable ignored) {
            try { socket.close(); } catch (Throwable e) {}
        }
    }

    private JSONObject dispatch(String method, String path, String body) {
        JSONObject result = new JSONObject();
        try {
            if ("/api/ping".equals(path)) {
                result.put("status", "ok");
                result.put("app", "OceanStudio");
            } else if ("/api/info".equals(path)) {
                result.put("status", "ok");
                result.put("app", "OceanStudio");
                result.put("version", "1.0");
                result.put("arch", Build.SUPPORTED_ABIS[0]);
                result.put("android_version", Build.VERSION.RELEASE);
                result.put("sdk", Build.VERSION.SDK_INT);
                result.put("device", Build.MANUFACTURER + " " + Build.MODEL);
            } else if ("/api/toast".equals(path)) {
                JSONObject json = new JSONObject(body);
                String msg = json.optString("message", json.optString("text", ""));
                boolean isLong = json.optBoolean("long", false);
                mainHandler.post(() -> Toast.makeText(context, msg, isLong ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show());
                result.put("success", true);
            } else if ("/api/open".equals(path)) {
                JSONObject json = new JSONObject(body);
                String target = json.optString("target", json.optString("url", ""));
                handleOpen(target);
                result.put("success", true);
            } else if ("/api/share".equals(path)) {
                JSONObject json = new JSONObject(body);
                String text = json.optString("text", "");
                String title = json.optString("title", "Share via Ocean");
                handleShare(text, title);
                result.put("success", true);
            } else if ("/api/notification".equals(path)) {
                JSONObject json = new JSONObject(body);
                String title = json.optString("title", "OceanStudio");
                String content = json.optString("content", json.optString("message", ""));
                showNotification(title, content);
                result.put("success", true);
            } else if ("/api/vibrate".equals(path)) {
                JSONObject json = new JSONObject(body);
                long duration = json.optLong("duration_ms", 300);
                Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null && v.hasVibrator()) {
                    v.vibrate(duration);
                }
                result.put("success", true);
            } else if ("/api/clipboard".equals(path)) {
                if ("POST".equals(method)) {
                    JSONObject json = new JSONObject(body);
                    String text = json.optString("text", "");
                    mainHandler.post(() -> {
                        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Ocean Clipboard", text));
                    });
                    result.put("success", true);
                } else {
                    ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    String text = "";
                    if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                        CharSequence cs = cm.getPrimaryClip().getItemAt(0).getText();
                        if (cs != null) text = cs.toString();
                    }
                    result.put("success", true);
                    result.put("text", text);
                }
            } else if ("/api/battery".equals(path)) {
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent bStatus = context.registerReceiver(null, ifilter);
                int level = bStatus != null ? bStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
                int scale = bStatus != null ? bStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1) : -1;
                int status = bStatus != null ? bStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1) : -1;
                int plugged = bStatus != null ? bStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) : 0;
                float pct = (level >= 0 && scale > 0) ? (level / (float) scale) * 100f : -1;

                result.put("percentage", Math.round(pct));
                result.put("charging", status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
                result.put("plugged", plugged > 0);
                result.put("success", true);
            } else if ("/api/device".equals(path)) {
                JSONObject json = body == null || body.isEmpty() ? new JSONObject() : new JSONObject(body);
                String tool = json.optString("tool", "");
                JSONObject args = json.optJSONObject("args");
                if (args == null) args = json;
                if (tool.isEmpty()) throw new IllegalArgumentException("tool is required");
                result = studio.ocean.app.device.DeviceControlService.execute(context, tool, args);
            } else if ("/api/intent".equals(path)) {
                JSONObject json = body == null || body.isEmpty() ? new JSONObject() : new JSONObject(body);
                result = handleIntent(json);
            } else if ("/api/capture".equals(path)) {
                JSONObject json = body == null || body.isEmpty() ? new JSONObject() : new JSONObject(body);
                result = handleCapture(json);
            } else if ("/api/x11".equals(path)) {
                JSONObject json = body == null || body.isEmpty() ? new JSONObject() : new JSONObject(body);
                String action = json.optString("action", "status");
                if ("open".equals(action)) {
                    int port = json.optInt("port", studio.ocean.app.render.OceanX11Activity.DEFAULT_RFB_PORT);
                    studio.ocean.app.render.OceanX11Activity.open(context, port);
                    result.put("success", true).put("port", port);
                } else {
                    result = studio.ocean.app.render.OceanX11Activity.control(action, json);
                }
            } else if ("/api/3d".equals(path)) {
                JSONObject json = body == null || body.isEmpty() ? new JSONObject() : new JSONObject(body);
                String action = json.optString("action", "status");
                if ("open".equals(action)) {
                    String model = json.optString("path", "");
                    if (model.isEmpty()) throw new IllegalArgumentException("path is required");
                    studio.ocean.app.render.Ocean3DActivity.open(context, model);
                    result.put("success", true).put("path", model);
                } else {
                    result = studio.ocean.app.render.Ocean3DActivity.control(action, json);
                }
            } else if ("/api/share-file".equals(path)) {
                JSONObject json = new JSONObject(body);
                handleShareFile(json.getString("path"), json.optString("title", "Share via Ocean"));
                result.put("success", true);
            } else {
                result.put("error", "Unknown endpoint: " + path);
                result.put("success", false);
            }
        } catch (Throwable t) {
            try {
                result.put("error", t.getMessage());
                result.put("success", false);
            } catch (Throwable ignored) {}
        }
        return result;
    }

    private JSONObject handleIntent(JSONObject json) throws Exception {
        String action = json.optString("action", Intent.ACTION_VIEW);
        String uri = json.optString("uri", "");
        String pkg = json.optString("package", "");
        String type = json.optString("type", "");
        Intent i = uri.isEmpty() ? new Intent(action) : new Intent(action, Uri.parse(uri));
        if (!pkg.isEmpty()) i.setPackage(pkg);
        if (!type.isEmpty()) i.setType(type);
        if (json.optBoolean("chooser", false)) {
            Intent chooser = Intent.createChooser(i, json.optString("title", "Open with"));
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        } else {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            JSONObject extras = json.optJSONObject("extras");
            if (extras != null) {
                java.util.Iterator<String> keys = extras.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object value = extras.opt(key);
                    if (value instanceof Boolean) i.putExtra(key, (Boolean)value);
                    else if (value instanceof Integer) i.putExtra(key, (Integer)value);
                    else if (value instanceof Long) i.putExtra(key, (Long)value);
                    else if (value instanceof Double) i.putExtra(key, (Double)value);
                    else if (value != null) i.putExtra(key, String.valueOf(value));
                }
            }
            context.startActivity(i);
        }
        return new JSONObject().put("success", true).put("action", action).put("uri", uri).put("package", pkg);
    }

    private JSONObject handleCapture(JSONObject json) throws Exception {
        String action = json.optString("action", "status");
        if ("screenshot".equals(action)) {
            return studio.ocean.app.device.DeviceControlService.execute(
                    context, "capture_android_screen", new JSONObject());
        }
        if ("selfie".equals(action) || "photo".equals(action) || "record_start".equals(action)) {
            String mode = "record_start".equals(action) ? "record" : action;
            Intent i = new Intent(context, studio.ocean.app.capture.OceanCaptureActivity.class)
                    .putExtra("mode", mode).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(i);
            return new JSONObject().put("success", true).put("requires_user_confirmation", true).put("mode", mode);
        }
        if ("record_stop".equals(action)) {
            Intent stop = new Intent(context, studio.ocean.app.capture.OceanScreenRecordService.class)
                    .setAction(studio.ocean.app.capture.OceanScreenRecordService.ACTION_STOP);
            context.startService(stop);
            return new JSONObject().put("success", true);
        }
        android.content.SharedPreferences p = context.getSharedPreferences(
                studio.ocean.app.capture.OceanCaptureActivity.PREF, Context.MODE_PRIVATE);
        return new JSONObject()
                .put("success", true)
                .put("photo_status", p.getString("photo_status", "idle"))
                .put("photo_path", p.getString("photo_path", ""))
                .put("record_status", p.getString("record_status", "idle"))
                .put("record_path", p.getString("record_path", ""))
                .put("photo_error", p.getString("photo_error", ""))
                .put("record_error", p.getString("record_error", ""));
    }

    private void handleShareFile(String path, String title) throws Exception {
        File file = new File(path);
        if (!file.isFile()) throw new IOException("File not found: " + path);
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".diagnostics", file);
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType(getMimeType(path));
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Intent chooser = Intent.createChooser(send, title);
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(chooser);
    }

    private void handleOpen(String target) {
        try {
            Intent intent;
            if (target.startsWith("app:")) {
                String pkg = target.substring(4);
                intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
                if (intent == null) throw new IllegalArgumentException("No launchable app: " + pkg);
            } else if (target.startsWith("http://") || target.startsWith("https://") || target.startsWith("mailto:") || target.startsWith("geo:") || target.startsWith("market:") || target.startsWith("tel:")) {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(target));
            } else {
                Intent launch = context.getPackageManager().getLaunchIntentForPackage(target);
                File file = new File(target);
                if (launch != null && !file.exists()) {
                    intent = launch;
                } else {
                Uri uri;
                try {
                    uri = FileProvider.getUriForFile(context, context.getPackageName() + ".diagnostics", file);
                } catch (Throwable ignored) {
                    uri = Uri.fromFile(file);
                }
                String mime = getMimeType(target);
                intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, mime != null ? mime : "*/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
            TerminalDiagnosticBundle.log("startup.log", "[IPC] handleOpen error: " + t.getMessage());
        }
    }

    private void handleShare(String text, String title) {
        try {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, text);
            Intent chooser = Intent.createChooser(shareIntent, title);
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(chooser);
        } catch (Throwable t) {
            TerminalDiagnosticBundle.log("startup.log", "[IPC] handleShare error: " + t.getMessage());
        }
    }

    private void showNotification(String title, String content) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true);
            nm.notify((int) System.currentTimeMillis(), builder.build());
        } catch (Throwable t) {
            TerminalDiagnosticBundle.log("startup.log", "[IPC] showNotification error: " + t.getMessage());
        }
    }

    private void ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "Ocean Notifications",
                        NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription("Notifications sent from Ocean terminal and tools");
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void handleWebSocketHandshake(String key, OutputStream out) throws Exception {
        String acceptKey = Base64.encodeToString(
                MessageDigest.getInstance("SHA-1").digest((key + WS_GUID).getBytes(StandardCharsets.UTF_8)),
                Base64.NO_WRAP);
        String response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: " + acceptKey + "\r\n\r\n";
        out.write(response.getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private void handleWebSocketConnection(Socket socket, InputStream in) {
        try {
            while (running && !socket.isClosed()) {
                int b1 = in.read();
                if (b1 < 0) break;
                int b2 = in.read();
                if (b2 < 0) break;

                int opcode = b1 & 0x0F;
                if (opcode == 8) break; // Close frame

                boolean masked = (b2 & 0x80) != 0;
                long len = b2 & 0x7F;
                if (len == 126) {
                    len = ((in.read() << 8) | in.read());
                } else if (len == 127) {
                    len = 0;
                    for (int i = 0; i < 8; i++) len = (len << 8) | in.read();
                }

                byte[] mask = new byte[4];
                if (masked) {
                    for (int i = 0; i < 4; i++) mask[i] = (byte) in.read();
                }

                byte[] payload = new byte[(int) len];
                int totalRead = 0;
                while (totalRead < len) {
                    int r = in.read(payload, totalRead, (int) (len - totalRead));
                    if (r < 0) break;
                    totalRead += r;
                }

                if (masked) {
                    for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
                }

                String msg = new String(payload, StandardCharsets.UTF_8);
                // Handle WebSocket message
                try {
                    JSONObject req = new JSONObject(msg);
                    String action = req.optString("action", "");
                    JSONObject resp = dispatch("POST", "/api/" + action, msg);
                    sendWebSocketText(socket, resp.toString());
                } catch (Throwable t) {
                    sendWebSocketText(socket, "{\"error\":\"" + t.getMessage() + "\"}");
                }
            }
        } catch (Throwable ignored) {
        } finally {
            wsClients.remove(socket);
            try { socket.close(); } catch (Throwable ignored) {}
        }
    }

    public void broadcastWebSocket(String message) {
        synchronized (wsClients) {
            for (Socket s : wsClients) {
                try {
                    sendWebSocketText(s, message);
                } catch (Throwable ignored) {}
            }
        }
    }

    private void sendWebSocketText(Socket socket, String text) throws IOException {
        byte[] raw = text.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(0x81); // Fin + text opcode
        if (raw.length <= 125) {
            baos.write(raw.length);
        } else if (raw.length <= 65535) {
            baos.write(126);
            baos.write((raw.length >> 8) & 0xFF);
            baos.write(raw.length & 0xFF);
        } else {
            baos.write(127);
            for (int i = 7; i >= 0; i--) baos.write((int) ((raw.length >> (8 * i)) & 0xFF));
        }
        baos.write(raw);
        OutputStream out = socket.getOutputStream();
        out.write(baos.toByteArray());
        out.flush();
    }

    private static String getMimeType(String url) {
        String ext = "";
        int i = url.lastIndexOf('.');
        if (i > 0) ext = url.substring(i + 1).toLowerCase();
        switch (ext) {
            case "txt": case "log": return "text/plain";
            case "html": case "htm": return "text/html";
            case "json": return "application/json";
            case "pdf": return "application/pdf";
            case "png": return "image/png";
            case "jpg": case "jpeg": return "image/jpeg";
            case "gif": return "image/gif";
            case "mp4": return "video/mp4";
            case "mp3": return "audio/mpeg";
            case "zip": return "application/zip";
            case "apk": return "application/vnd.android.package-archive";
            default: return "*/*";
        }
    }
}
