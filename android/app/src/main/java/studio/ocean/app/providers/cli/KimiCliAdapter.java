package studio.ocean.app.providers.cli;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

/**
 * Adapter for Kimi Code CLI ('kimi').
 * Bridges managed service session and device-code login.
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
        ExecutionResult res = executeSync(Arrays.asList("account", "status"), null, 5);
        return res.isSuccess() && !res.stdout.toLowerCase().contains("login required");
    }

    public void runHeadless(String prompt, StreamCallback callback) {
        executeStreaming(Arrays.asList("exec", "--json", prompt), null, callback);
    }
}
