package studio.ocean.app.providers.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import studio.ocean.app.providers.model.AuthStrategy;
import studio.ocean.app.providers.model.ProviderDescriptor;

public final class AuthPreflightTest {

    @Test
    public void validateRedirectUriBansLoopback() {
        // Mobile loopback redirects must fail preflight to prevent Access Blocked / redirect_uri_mismatch
        AuthPreflight.PreflightResult res1 = AuthPreflight.validateRedirectUri("http://127.0.0.1:8080/callback");
        assertFalse(res1.isReady);
        assertEquals("redirect_uri_mismatch", res1.failureTitle);
        assertTrue(res1.failureMessage.contains("Loopback"));

        AuthPreflight.PreflightResult res2 = AuthPreflight.validateRedirectUri("http://localhost:3000/auth");
        assertFalse(res2.isReady);
        assertEquals("redirect_uri_mismatch", res2.failureTitle);
    }

    @Test
    public void validateRedirectUriAcceptsValidSchemes() {
        AuthPreflight.PreflightResult resHttps = AuthPreflight.validateRedirectUri("https://ocean.studio/auth/callback");
        assertTrue(resHttps.isReady);

        AuthPreflight.PreflightResult resOcean = AuthPreflight.validateRedirectUri("ocean://oauth2/callback");
        assertTrue(resOcean.isReady);
    }

    @Test
    public void validateApiKeyConfigCatchesInvalidKeys() {
        AuthPreflight.PreflightResult empty = AuthPreflight.validateApiKeyConfig("openai", "gpt-4o", "", null);
        assertFalse(empty.isReady);
        assertTrue(empty.failureMessage.contains("valid API key"));

        AuthPreflight.PreflightResult badGoogle = AuthPreflight.validateApiKeyConfig("google", "gemini-2.5-flash", "sk-ant-12345", null);
        assertFalse(badGoogle.isReady);
        assertTrue(badGoogle.failureMessage.contains("AIza"));

        AuthPreflight.PreflightResult goodGoogle = AuthPreflight.validateApiKeyConfig("google", "gemini-2.5-flash", "AIzaSyDummyValidFormatKey", null);
        assertTrue(goodGoogle.isReady);
    }

    @Test
    public void validateRejectsUnsupportedStrategyWithoutBrowserLaunch() {
        AuthPreflight preflight = new AuthPreflight(null);
        ProviderDescriptor provider = studio.ocean.app.providers.ProviderRegistry.find(
                studio.ocean.app.providers.ProviderRegistry.ID_GROQ);

        // Official OAuth is not supported on Groq, must fail preflight cleanly
        AuthPreflight.PreflightResult result = preflight.validate(provider, AuthStrategy.OFFICIAL_OAUTH, null, null);
        assertFalse(result.isReady);
        assertEquals("Strategy Not Supported", result.failureTitle);
        assertNotNull(result.recommendedAlternative);
    }
}
