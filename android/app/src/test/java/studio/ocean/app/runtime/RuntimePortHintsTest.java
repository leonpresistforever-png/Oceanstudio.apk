package studio.ocean.app.runtime;

import static org.junit.Assert.assertTrue;
import org.junit.Test;

public final class RuntimePortHintsTest {
    @Test public void extractsPortsFromTypicalDevServerLines() {
        String sample = "vite v5.0.0 dev server running at:\n  ➜  Local:   http://localhost:5173/\n";
        java.util.Set<Integer> ports = new java.util.LinkedHashSet<>();
        for (java.util.regex.Pattern pattern : new java.util.regex.Pattern[] {
                java.util.regex.Pattern.compile("localhost:(\\d{2,5})") }) {
            java.util.regex.Matcher matcher = pattern.matcher(sample);
            while (matcher.find()) ports.add(Integer.parseInt(matcher.group(1)));
        }
        assertTrue(ports.contains(5173));
    }
}
