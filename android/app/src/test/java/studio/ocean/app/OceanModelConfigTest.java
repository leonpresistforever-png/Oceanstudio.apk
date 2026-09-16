package studio.ocean.app;

import static org.junit.Assert.*;
import org.junit.Test;

public final class OceanModelConfigTest {
    @Test public void rejectsProviderAsModelBeforeNetworkRequest() {
        for (String model : new String[]{"google", "models/google", "gemini", "", " google "}) {
            try { new OceanModelConfig("google", model, "test-key", "https://generativelanguage.googleapis.com"); fail(model); }
            catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("model ID")); }
        }
    }
    @Test public void googleNormalizesModelPrefixAndApiVersionWithoutKeyInUrl() {
        OceanModelConfig config = new OceanModelConfig("google", "models/gemini-2.5-flash", "secret", "https://generativelanguage.googleapis.com/v1beta/");
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent", config.endpoint());
        assertFalse(config.endpoint().contains("secret"));
    }
    @Test public void respectsProviderBaseUrlsAndCustomModelIds() {
        assertEquals("https://proxy.example/v1/messages", new OceanModelConfig("anthropic", "claude-test", "key", "https://proxy.example").endpoint());
        assertEquals("https://proxy.example/v1/chat/completions", new OceanModelConfig("custom", "org/model", "key", "https://proxy.example/v1").endpoint());
        assertEquals("https://proxy.example/v1/chat/completions", new OceanModelConfig("custom", "model", "key", "https://proxy.example/v1/chat/completions").endpoint());
    }
    @Test public void rejectsAmbiguousEndpointsAndModelPathInjection() {
        for (String url : new String[]{"http://host.example", "https://user:pass@host.example", "https://host.example?key=secret", "https://host.example#frag"}) {
            try { new OceanModelConfig("google", "gemini-2.5-flash", "key", url); fail(url); } catch (IllegalArgumentException expected) {}
        }
        try { new OceanModelConfig("google", "x?key=other", "key", "https://example.com"); fail(); } catch (IllegalArgumentException expected) {}
    }
    @Test public void naturalLanguageRunIsNotMistakenForShellCode() {
        assertNull(OceanAgentRequests.explicitCommand("Run terminal commands pip"));
        assertNull(OceanAgentRequests.explicitCommand("run terminal commands pip"));
        assertNull(OceanAgentRequests.explicitCommand("run a check on my project"));
        assertEquals("pip --version", OceanAgentRequests.explicitCommand("$ pip --version"));
        assertEquals("pwd", OceanAgentRequests.explicitCommand("exec pwd"));
        assertTrue(OceanAgentRequests.opensTerminal("Open up ocean os terminal"));
        assertTrue(OceanAgentRequests.opensTerminal("Please open terminal."));
        assertFalse(OceanAgentRequests.opensTerminal("Explain how to open terminal"));
    }
}
