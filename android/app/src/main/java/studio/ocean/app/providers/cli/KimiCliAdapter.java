package studio.ocean.app.providers.cli;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

/**
 * Adapter for Kimi Code CLI ('kimi').
 * Bridges managed service session and device-code login (PDF 5 §6.2, §12).
 * Documented command: 'kimi -p "prompt" --output-format stream-json'.
 */
public final class KimiCliAdapter extends OfficialCliAdapter {

    public KimiCliAdapter(File searchDirectory) {
        super("kimi", searchDirectory);
    }

    public String getVersion() {
        ExecutionResult res = executeSync(Collections.singletonList("--version"), null, 5);
        if (res.isSuccess() && !res.stdout.isEmpty()) {
            return res.stdout.split("\n")[0].trim();
        }
        return null;
    }

    public boolean isSessionAuthenticated() {
        // Runs a non-interactive auth probe to check session authentication
        ExecutionResult res = executeSync(Arrays.asList("whoami"), null, 5);
        if (!res.isSuccess()) {
            String combined = (res.stdout + " " + res.stderr).toLowerCase();
            return !combined.contains("login required") && !combined.contains("unauthorized") && !combined.contains("not logged in");
        }
        return true;
    }

    public void runHeadless(String prompt, StreamCallback callback) {
        executeStreaming(Arrays.asList("-p", prompt, "--output-format", "stream-json"), null, callback);
    }
}
