package studio.ocean.app;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Real Firebase Identity Toolkit email authentication; it never fabricates a session. */
final class AuthClient {
    interface Callback { void complete(Result result); }
    static final class Result {
        final boolean success; final String token; final String email; final String message;
        Result(boolean success, String token, String email, String message) { this.success=success; this.token=token; this.email=email; this.message=message; }
    }
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    boolean configured() { return !BuildConfig.OCEAN_FIREBASE_API_KEY.isEmpty(); }
    void signIn(String email, String password, boolean signup, Callback callback) {
        String method = signup ? "signUp" : "signInWithPassword";
        JSONObject body = new JSONObject();
        try { body.put("email", email).put("password", password).put("returnSecureToken", true); }
        catch (Exception error) { callback.complete(new Result(false,null,null,error.getMessage())); return; }
        request("accounts:" + method, body, callback);
    }
    void reset(String email, Callback callback) {
        JSONObject body = new JSONObject();
        try { body.put("requestType", "PASSWORD_RESET").put("email", email); }
        catch (Exception error) { callback.complete(new Result(false,null,null,error.getMessage())); return; }
        request("accounts:sendOobCode", body, callback);
    }
    private void request(String method, JSONObject body, Callback callback) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://identitytoolkit.googleapis.com/v1/" + method + "?key=" + BuildConfig.OCEAN_FIREBASE_API_KEY);
                connection = (HttpURLConnection) url.openConnection(); connection.setRequestMethod("POST"); connection.setDoOutput(true); connection.setConnectTimeout(15000); connection.setReadTimeout(20000); connection.setRequestProperty("Content-Type", "application/json");
                try (OutputStream output = connection.getOutputStream()) { output.write(body.toString().getBytes(StandardCharsets.UTF_8)); }
                int status = connection.getResponseCode(); InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
                StringBuilder response = new StringBuilder(); try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) { String line; while ((line=reader.readLine()) != null) response.append(line); }
                JSONObject json = new JSONObject(response.toString());
                if (status >= 200 && status < 300) callback.complete(new Result(true,json.optString("idToken",null),json.optString("email",body.optString("email")),null));
                else callback.complete(new Result(false,null,null,json.optJSONObject("error") == null ? "Authentication failed" : json.optJSONObject("error").optString("message","Authentication failed")));
            } catch (Exception error) { callback.complete(new Result(false,null,null,error.getMessage())); }
            finally { if (connection != null) connection.disconnect(); }
        });
    }
}
