package studio.ocean.app.providers.state;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;
import studio.ocean.app.providers.model.*;

/** Exercises the actual JSON store and atomic files used by separate app components. */
public class ProviderConnectionStoreTest {
    private static ProviderConnection record(String id, ConnectionStatus status, long validated) {
        return new ProviderConnection(id, "local", "", AuthStrategy.LOCAL, status, "", "model", null,
                null, Collections.emptyList(), null, QuotaSnapshot.unknown("", "test"),
                Collections.emptyList(), validated);
    }
    @Test public void readerObservesAnotherComponentsConnectAndDisconnect() throws Exception {
        File directory = Files.createTempDirectory("ocean-connections").toFile();
        ProviderConnectionStore reader = new ProviderConnectionStore(directory);
        ProviderConnectionStore writer = new ProviderConnectionStore(directory);
        assertTrue(reader.listAll().isEmpty());
        writer.save(record("local_connection", ConnectionStatus.CONNECTED, 10));
        assertEquals("local_connection", reader.listAll().get(0).id);
        writer.delete("local_connection");
        assertTrue(reader.listAll().isEmpty());
    }
    @Test public void independentWritersPreserveEachOthersRecords() throws Exception {
        File directory = Files.createTempDirectory("ocean-connections").toFile();
        ProviderConnectionStore first = new ProviderConnectionStore(directory);
        ProviderConnectionStore second = new ProviderConnectionStore(directory);
        first.listAll(); second.listAll();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> a = workers.submit(() -> { try { start.await(); } catch (InterruptedException e) { throw new RuntimeException(e); }
                first.save(record("a", ConnectionStatus.CONNECTED, 10)); });
            Future<?> b = workers.submit(() -> { try { start.await(); } catch (InterruptedException e) { throw new RuntimeException(e); }
                second.save(record("b", ConnectionStatus.CONNECTED, 20)); });
            start.countDown(); a.get(5, TimeUnit.SECONDS); b.get(5, TimeUnit.SECONDS);
            assertEquals(2, first.listAll().size());
            assertEquals(2, second.listAll().size());
            assertNotNull(new ProviderConnectionStore(directory).get("a"));
            assertNotNull(new ProviderConnectionStore(directory).get("b"));
        } finally { workers.shutdownNow(); }
    }
    @Test public void verifiedConnectionPrecedesAnOlderFailedSetup() throws Exception {
        File directory = Files.createTempDirectory("ocean-connections").toFile();
        ProviderConnectionStore store = new ProviderConnectionStore(directory);
        store.save(record("failed", ConnectionStatus.ERROR, 20));
        store.save(record("connected", ConnectionStatus.CONNECTED, 10));
        assertEquals("connected", store.listByProvider("local").get(0).id);
        assertEquals("connected", store.findByProviderId("local").id);
    }
}
