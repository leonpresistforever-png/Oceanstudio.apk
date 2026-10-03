package studio.ocean.app.providers.auth;
import org.junit.Test;
import org.json.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.Assert.*;
public class OpenAiIdentityVerifierTest {
    static String enc(byte[] v) { return Base64.getUrlEncoder().withoutPadding().encodeToString(v); }
    @Test public void validatesSignatureIdentityAndTransaction() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA"); gen.initialize(2048); KeyPair pair = gen.generateKeyPair();
        RSAPublicKey pub = (RSAPublicKey) pair.getPublic();
        JSONObject keys = new JSONObject().put("keys", new JSONArray().put(new JSONObject().put("kid", "test").put("kty", "RSA")
            .put("n", enc(pub.getModulus().toByteArray())).put("e", enc(pub.getPublicExponent().toByteArray()))));
        long now = System.currentTimeMillis()/1000;
        JSONObject claims = new JSONObject().put("iss", "https://auth.openai.com").put("sub", "account").put("aud", "client")
            .put("nonce", "nonce").put("iat", now).put("exp", now+60);
        String signed = enc("{\"alg\":\"RS256\",\"kid\":\"test\"}".getBytes(StandardCharsets.UTF_8))+"."+enc(claims.toString().getBytes(StandardCharsets.UTF_8));
        Signature sig = Signature.getInstance("SHA256withRSA"); sig.initSign(pair.getPrivate()); sig.update(signed.getBytes(StandardCharsets.US_ASCII));
        String token = signed+"."+enc(sig.sign());
        assertEquals("account", OpenAiIdentityVerifier.verifyWithKeys(token,"client","nonce",keys).getString("sub"));
        for (String[] args : new String[][]{{token,"wrong","nonce"},{token,"client","wrong"},{signed+".AA","client","nonce"}}) {
            try { OpenAiIdentityVerifier.verifyWithKeys(args[0],args[1],args[2],keys); fail("invalid token accepted"); }
            catch (Exception expected) {}
        }
    }
}
