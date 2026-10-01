package studio.ocean.app.providers.cli;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

/**
 * Adapter for Claude Code CLI ('claude').
 */
public final class ClaudeCodeCliAdapter extends OfficialCliAdapter {

    public ClaudeCodeCliAdapter(File searchDirectory) {
        super("claude", searchDirectory);
    }

    public String getVersion() {
        ExecutionResult res = executeSync(Collections.singletonList("--version"), null, 5);
        if (res.isSuccess() && !res.stdout.isEmpty()) {
            return res.stdout.split("\n")[0].trim();
        }
        return null;
    }

    public boolean isSessionAuthenticated() {
        ExecutionResult res = executeSync(Arrays.asList("auth", "status"), null, 5);
        return res.isSuccess() && !res.stdout.toLowerCase().contains("not authenticated");
    }

    public boolean hasApiKeyPrecedence() {
        String key = System.getenv("ANTHROPIC_API_KEY");
        return key != null && !key.trim().isEmpty();
    }

    @Override
    public void runHeadless(String prompt, StreamCallback callback) {
        java.util.Map<String, String> env = new java.util.HashMap<>();
        // Sanitize child environment to prevent silent API key billing over subscription (Audit §4.4, §10.2)
        env.put("ANTHROPIC_API_KEY", "");
        executeStreaming(Arrays.asList("-p", prompt, "--output-format", "stream-json"), env, callback);
    }
}
