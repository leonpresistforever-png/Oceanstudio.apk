package studio.ocean.app.providers.cli;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Adapter for Google Antigravity CLI ('agy').
 * Bridges official account/subscription login and headless runs.
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
        // Runs a non-interactive auth probe: 'agy auth status' or check config
        ExecutionResult res = executeSync(Arrays.asList("auth", "status"), null, 5);
        return res.isSuccess() && !res.stdout.toLowerCase().contains("not logged in");
    }

    public void runHeadless(String prompt, StreamCallback callback) {
        executeStreaming(Arrays.asList("run", "--format", "json", prompt), null, callback);
    }
}
