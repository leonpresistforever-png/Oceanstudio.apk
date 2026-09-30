package studio.ocean.app.terminal;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Rewrites host shebangs so scripts run inside Ocean's prefix without /usr/bin/env. */
public final class OceanShebangPolicy {
    public static final String PREFIX = OceanBootstrapInstaller.BUILD_PREFIX;

    private OceanShebangPolicy() {}

    public static byte[] rewriteIfNeeded(byte[] content) {
        if (content == null || content.length < 2 || content[0] != '#' || content[1] != '!') {
            return content;
        }
        int end = 0;
        while (end < content.length && content[end] != '\n') end++;
        String line = new String(content, 0, end, StandardCharsets.UTF_8);
        String rewritten = rewriteShebangLine(line);
        if (rewritten == null) return content;
        byte[] head = rewritten.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[head.length + (content.length - end)];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(content, end, out, head.length, content.length - end);
        return out;
    }

    /** @return rewritten shebang line, or null when no change is required */
    public static String rewriteShebangLine(String line) {
        if (line == null || !line.startsWith("#!")) return null;
        String body = line.substring(2).trim();
        if (body.isEmpty()) return null;

        String[] parts = body.split("\\s+");
        if (parts.length == 0) return null;

        int index = 0;
        if ("-S".equals(parts[0]) && parts.length > 1) index = 1;
        if (index >= parts.length) return null;

        String interpreter;
        String tail;
        if (isEnvHelper(parts[index])) {
            int commandIndex = index + 1;
            if (commandIndex < parts.length && "-S".equals(parts[commandIndex])) commandIndex++;
            if (commandIndex >= parts.length) return null;
            interpreter = parts[commandIndex];
            tail = tailArguments(parts, commandIndex + 1);
        } else {
            interpreter = parts[index];
            tail = tailArguments(parts, index + 1);
        }

        String mapped = mapInterpreter(interpreter);
        if (mapped == null) return null;
        String rewritten = "#!" + PREFIX + "/bin/" + mapped;
        if (!tail.isEmpty()) rewritten += " " + tail;
        return rewritten.equals(line) ? null : rewritten;
    }

    private static String tailArguments(String[] parts, int from) {
        if (from >= parts.length) return "";
        StringBuilder builder = new StringBuilder();
        for (int i = from; i < parts.length; i++) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(parts[i]);
        }
        return builder.toString();
    }

    private static boolean isEnvHelper(String token) {
        if (token == null) return false;
        return "env".equals(token) || token.endsWith("/env") || token.contains("/usr/bin/env");
    }

    private static String mapInterpreter(String interpreter) {
        if (interpreter == null || interpreter.isEmpty()) return null;
        String name = interpreter;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.toLowerCase(Locale.US);
        switch (name) {
            case "bash":
            case "sh":
            case "dash":
            case "zsh":
            case "python":
            case "python3":
            case "node":
            case "nodejs":
            case "perl":
            case "ruby":
            case "php":
                return name.equals("nodejs") ? "node" : name;
            default:
                if (interpreter.startsWith("/bin/") || interpreter.startsWith("/usr/bin/")) {
                    return name;
                }
                return null;
        }
    }
}
