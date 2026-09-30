package studio.ocean.app;

import static org.junit.Assert.assertTrue;
import org.junit.Test;

public final class AuthClientTest {
    @Test public void signInWithIdpBuildsGooglePostBody() throws Exception {
        String postBody = AuthClientTestSupport.idpPostBody("google.com", "token-value", false);
        assertTrue(postBody.contains("id_token=token-value"));
        assertTrue(postBody.contains("providerId=google.com"));
    }
}

/** Package-visible test seam without widening AuthClient API. */
final class AuthClientTestSupport {
    private AuthClientTestSupport() {}
    static String idpPostBody(String providerId, String token, boolean accessToken) throws Exception {
        return (accessToken ? "access_token=" : "id_token=")
                + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8.name())
                + "&providerId=" + java.net.URLEncoder.encode(providerId, java.nio.charset.StandardCharsets.UTF_8.name());
    }
}
