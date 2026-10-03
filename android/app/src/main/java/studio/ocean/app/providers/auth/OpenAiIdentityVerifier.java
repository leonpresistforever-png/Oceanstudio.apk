package studio.ocean.app.providers.auth;

import java.util.Base64;
import java.io.*;
import java.net.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.RSAPublicKeySpec;
import org.json.*;

/** Verifies RS256 identity tokens against the fixed issuer's published public keys. */
final class OpenAiIdentityVerifier {
    private static byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }
    static JSONObject verify(String token, String clientId, String nonce) throws Exception {
        if (token == null || token.length() > 65536 || nonce == null) throw new IOException("Missing OpenAI identity token or nonce");
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) throw new IOException("Malformed OpenAI identity token");
        JSONObject header = new JSONObject(new String(decode(parts[0]), StandardCharsets.UTF_8));
        if (!"RS256".equals(header.optString("alg"))) throw new IOException("Unsupported OpenAI identity signature algorithm");
        String kid = header.getString("kid");
        HttpURLConnection conn = (HttpURLConnection) new URL("https://auth.openai.com/.well-known/jwks.json").openConnection();
        conn.setInstanceFollowRedirects(false); conn.setConnectTimeout(10000); conn.setReadTimeout(10000);
        JSONObject jwks;
        try {
            if (conn.getResponseCode() != 200) throw new IOException("OpenAI identity keys unavailable");
            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[4096]; int n;
                while ((n = in.read(buf)) != -1) {
                    if (out.size() + n > 262144) throw new IOException("Oversized OpenAI identity key response");
                    out.write(buf, 0, n);
                }
                jwks = new JSONObject(out.toString("UTF-8"));
            }
        } finally { conn.disconnect(); }
        return verifyWithKeys(token, clientId, nonce, jwks);
    }
    static JSONObject verifyWithKeys(String token, String clientId, String nonce, JSONObject jwks) throws Exception {
        if (token == null || token.length() > 65536 || nonce == null) throw new IOException("Missing OpenAI identity token or nonce");
        String[] parts = token.split("\\.", -1);
        if (parts.length != 3) throw new IOException("Malformed OpenAI identity token");
        JSONObject header = new JSONObject(new String(decode(parts[0]), StandardCharsets.UTF_8));
        if (!"RS256".equals(header.optString("alg"))) throw new IOException("Unsupported OpenAI identity signature algorithm");
        String kid = header.getString("kid");
        JSONArray keys = jwks.getJSONArray("keys"); boolean verified = false;
        for (int i = 0; i < keys.length(); i++) {
            JSONObject key = keys.getJSONObject(i);
            if (!kid.equals(key.optString("kid")) || !"RSA".equals(key.optString("kty"))) continue;
            if (key.has("use") && !"sig".equals(key.optString("use"))) continue;
            if (key.has("alg") && !"RS256".equals(key.optString("alg"))) continue;
            PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                    new BigInteger(1, decode(key.getString("n"))), new BigInteger(1, decode(key.getString("e")))));
            Signature signature = Signature.getInstance("SHA256withRSA"); signature.initVerify(publicKey);
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            verified = signature.verify(decode(parts[2])); if (verified) break;
        }
        if (!verified) throw new IOException("Invalid OpenAI identity signature");
        JSONObject claims = new JSONObject(new String(decode(parts[1]), StandardCharsets.UTF_8));
        long now = System.currentTimeMillis() / 1000;
        if (!"https://auth.openai.com".equals(claims.optString("iss")) || !nonce.equals(claims.optString("nonce"))
                || claims.optString("sub").isEmpty() || !claims.has("exp") || claims.getLong("exp") <= now - 5
                || !claims.has("iat") || claims.getLong("iat") > now + 5
                || (claims.has("nbf") && claims.getLong("nbf") > now + 5)) throw new IOException("Invalid OpenAI identity claims");
        Object aud = claims.opt("aud"); boolean audience = clientId.equals(aud);
        if (aud instanceof JSONArray) {
            JSONArray values = (JSONArray) aud;
            for (int i = 0; i < values.length(); i++) audience |= clientId.equals(values.optString(i));
            if (values.length() > 1 && !clientId.equals(claims.optString("azp"))) audience = false;
        }
        if (!audience) throw new IOException("OpenAI identity audience mismatch");
        return claims;
    }
}
