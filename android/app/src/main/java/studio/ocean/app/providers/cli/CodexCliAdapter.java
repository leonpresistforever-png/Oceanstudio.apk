package studio.ocean.app.providers.cli;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

/**
 * Adapter for OpenAI / Codex CLI ('codex').
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
        ExecutionResult res = executeSync(Arrays.asList("auth", "check"), null, 5);
        return res.isSuccess() && !res.stdout.toLowerCase().contains("unauthorized");
    }

    public void runHeadless(String prompt, StreamCallback callback) {
        executeStreaming(Arrays.asList("exec", "--json", prompt), null, callback);
    }
}
