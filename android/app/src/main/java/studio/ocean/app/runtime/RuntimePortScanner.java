package studio.ocean.app.runtime;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Reads Linux TCP listener tables and returns only sockets owned by OceanStudio's UID. */
public final class RuntimePortScanner {
    private RuntimePortScanner() {}

    public static List<Integer> scan() {
        Set<Integer> ports = new LinkedHashSet<>();
        int uid = android.os.Process.myUid();
        read("/proc/self/net/tcp", uid, ports);
        read("/proc/self/net/tcp6", uid, ports);
        List<Integer> sorted = new ArrayList<>(ports);
        Collections.sort(sorted);
        return sorted;
    }

    private static void read(String path, int uid, Set<Integer> ports) {
        try (Reader reader = new FileReader(path)) { ports.addAll(parse(reader, uid)); }
        catch (IOException ignored) { }
    }

    static List<Integer> parse(Reader source, int expectedUid) throws IOException {
        Set<Integer> ports = new LinkedHashSet<>();
        BufferedReader reader = new BufferedReader(source);
        String line;
        while ((line = reader.readLine()) != null) {
            String[] fields = line.trim().split("\\s+");
            if (fields.length < 8 || !"0A".equals(fields[3])) continue;
            String[] local = fields[1].split(":", 2);
            if (local.length != 2) continue;
            try {
                int uid = Integer.parseInt(fields[7]);
                int port = Integer.parseInt(local[1], 16);
                if (uid == expectedUid && port > 0 && port <= 65535) ports.add(port);
            } catch (NumberFormatException ignored) { }
        }
        List<Integer> result = new ArrayList<>(ports);
        Collections.sort(result);
        return result;
    }

    public static String kind(int port) {
        if (port == 6080 || port == 5800 || port == 6901) return "noVNC / remote desktop";
        if (port == 3000 || port == 5173 || port == 8000 || port == 8080 || port == 8888) return "Web development server";
        return "Local HTTP service";
    }
}
