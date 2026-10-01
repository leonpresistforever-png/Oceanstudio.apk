package studio.ocean.app.providers.cli;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Adapter for Google Antigravity CLI ('agy').
 * Bridges official account/subscription login and headless runs (PDF 5 §6.2, §12).
 * Documented command: 'agy -p "prompt" --output-format json' / 'stream-json'.
 */
public final class AntigravityCliAdapter extends OfficialCliAdapter {

    public AntigravityCliAdapter(File searchDirectory) {
        super("agy", searchDirectory);
    }

    public String getVersion() {
        ExecutionResult res = executeSync(Collections.singletonList("--version"), null, 5);
        if (res.isSuccess() && !res.stdout.isEmpty()) {
            return res.stdout.split("\n")[0].trim();
        }
        return null;
    }

    public boolean isSessionAuthenticated() {
        // Runs a non-interactive lightweight probe to check session authentication
        ExecutionResult res = executeSync(Arrays.asList("-p", "ping", "--output-format", "json"), null, 5);
        if (!res.isSuccess()) {
            String combined = (res.stdout + " " + res.stderr).toLowerCase();
            return !combined.contains("not logged in") && !combined.contains("unauthenticated") && !combined.contains("login required");
        }
        return true;
    }

    public void runHeadless(String prompt, StreamCallback callback) {
        executeStreaming(Arrays.asList("-p", prompt, "--output-format", "stream-json"), null, callback);
    }
}
