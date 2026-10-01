package studio.ocean.app.providers.cli;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

/**
 * Adapter for OpenAI / Codex CLI ('codex').
 * Documented command: 'codex login status' and 'codex exec --json' (PDF 5 §6.2, §12).
 */
public final class CodexCliAdapter extends OfficialCliAdapter {

    public CodexCliAdapter(File searchDirectory) {
        super("codex", searchDirectory);
    }

    public String getVersion() {
        ExecutionResult res = executeSync(Collections.singletonList("--version"), null, 5);
        if (res.isSuccess() && !res.stdout.isEmpty()) {
            return res.stdout.split("\n")[0].trim();
        }
        return null;
    }

    public boolean isSessionAuthenticated() {
        // Runs official auth status command: 'codex login status'
        ExecutionResult res = executeSync(Arrays.asList("login", "status"), null, 5);
        if (!res.isSuccess()) {
            String combined = (res.stdout + " " + res.stderr).toLowerCase();
            return !combined.contains("not logged in") && !combined.contains("unauthorized") && !combined.contains("login required");
        }
        return true;
    }

    public void runHeadless(String prompt, StreamCallback callback) {
        executeStreaming(Arrays.asList("exec", "--json", prompt), null, callback);
    }
}
