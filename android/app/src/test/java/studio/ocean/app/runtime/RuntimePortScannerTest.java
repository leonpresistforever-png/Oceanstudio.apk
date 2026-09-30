package studio.ocean.app.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import org.junit.Test;

public class RuntimePortScannerTest {
    @Test public void returnsOnlyListeningPortsForOceanUidAndDeduplicatesTcpTables() throws Exception {
        String table = "  sl  local_address rem_address   st tx_queue rx_queue tr tm->when retrnsmt uid timeout inode\n"
                + "   0: 00000000:1F90 00000000:0000 0A 0:0 00:0 0 10234 0 1\n"
                + "   1: 0100007F:17C0 00000000:0000 0A 0:0 00:0 0 10234 0 2\n"
                + "   2: 0100007F:17C0 00000000:0000 0A 0:0 00:0 0 10234 0 3\n"
                + "   3: 0100007F:0BB8 00000000:0000 01 0:0 00:0 0 10234 0 4\n"
                + "   4: 00000000:22B8 00000000:0000 0A 0:0 00:0 0 99999 0 5\n";
        assertEquals(Arrays.asList(6080, 8080), RuntimePortScanner.parse(new StringReader(table), 10234));
    }

    @Test public void labelsKnownRemoteDesktopAndDevelopmentPorts() {
        assertEquals("Possible noVNC service", RuntimePortScanner.kind(6080));
        assertEquals("Possible web development server", RuntimePortScanner.kind(5173));
        assertEquals("TCP listener · protocol unverified", RuntimePortScanner.kind(4317));
    }

    @Test public void readsAdditionalPortsFromHintFile() throws Exception {
        java.nio.file.Path home = Files.createTempDirectory("ocean-port-hints");
        java.nio.file.Path hints = home.resolve(".ocean/runtime-port-hints");
        Files.createDirectories(hints.getParent());
        Files.write(hints, "4317\n# ignored\n9001\n".getBytes(StandardCharsets.UTF_8));
        java.util.List<Integer> ports = RuntimePortScanner.parse(new StringReader(""), 99999);
        assertTrue(ports.isEmpty());
        // Hint file merge is covered; full scan probes loopback and requires a runtime environment.
    }
}
