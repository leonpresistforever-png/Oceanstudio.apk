package studio.ocean.app.runtime;

import android.content.Context;
import android.content.Intent;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import studio.ocean.app.OceanPaths;

/** Records listener ports seen in terminal output and broadcasts scan updates. */
public final class RuntimePortHints {
    public static final String ACTION_PORTS_CHANGED = "studio.ocean.app.RUNTIME_PORTS_CHANGED";

    private static final Pattern[] PATTERNS = {
            Pattern.compile("(?:listening|listen|serving|running)\\s+(?:on|at)?\\s+(?:https?://)?(?:127\\.0\\.0\\.1|localhost|\\[::1\\]|0\\.0\\.0\\.0):(\\d{2,5})", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:port|PORT)\\s*[=:]\\s*(\\d{2,5})"),
            Pattern.compile("localhost:(\\d{2,5})"),
            Pattern.compile("127\\.0\\.0\\.1:(\\d{2,5})")
    };

    private RuntimePortHints() {}

    public static void observeTerminalOutput(Context context, String chunk) {
        if (context == null || chunk == null || chunk.isEmpty()) return;
        Set<Integer> found = new LinkedHashSet<>();
        for (Pattern pattern : PATTERNS) {
            Matcher matcher = pattern.matcher(chunk);
            while (matcher.find()) {
                try {
                    int port = Integer.parseInt(matcher.group(1));
                    if (port > 0 && port <= 65535) found.add(port);
                } catch (NumberFormatException ignored) { }
            }
        }
        if (found.isEmpty()) return;
        if (record(context, found)) {
            context.getApplicationContext().sendBroadcast(new Intent(ACTION_PORTS_CHANGED));
        }
    }

    static boolean record(Context context, Set<Integer> ports) {
        File hints = hintFile(context);
        Set<Integer> existing = read(hints);
        boolean changed = false;
        for (int port : ports) {
            if (existing.add(port)) changed = true;
        }
        if (!changed) return false;
        write(hints, existing);
        return true;
    }

    static File hintFile(Context context) {
        return new File(new File(new OceanPaths(context).home(), ".ocean"), "runtime-port-hints");
    }

    static Set<Integer> read(File hints) {
        Set<Integer> ports = new LinkedHashSet<>();
        if (!hints.isFile()) return ports;
        try (BufferedReader reader = new BufferedReader(new FileReader(hints))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                try {
                    int port = Integer.parseInt(line);
                    if (port > 0 && port <= 65535) ports.add(port);
                } catch (NumberFormatException ignored) { }
            }
        } catch (IOException ignored) { }
        return ports;
    }

    private static void write(File hints, Set<Integer> ports) {
        try {
            if (!hints.getParentFile().exists() && !hints.getParentFile().mkdirs()) return;
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(hints, StandardCharsets.UTF_8, false))) {
                writer.write("# Ocean auto-detected local listener ports\n");
                for (int port : ports) writer.write(Integer.toString(port) + "\n");
            }
        } catch (IOException ignored) { }
    }
}
