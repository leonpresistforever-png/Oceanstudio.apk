package studio.ocean.app.models.local;

import java.io.File;
import java.nio.file.Files;
import java.util.concurrent.*;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ManagedLocalRuntimeTest {
    @Test public void lateOldExitCannotClearReplacement() throws Exception {
        ManagedLocalRuntime runtime = new ManagedLocalRuntime();
        File directory = Files.createTempDirectory("ocean-runtime-test").toFile();
        try {
            java.util.concurrent.atomic.AtomicInteger exits = new java.util.concurrent.atomic.AtomicInteger();
            runtime.start(new ProcessBuilder("sh", "-c", "echo old; exec sleep 20"), new File(directory,"old.log"), x -> exits.incrementAndGet());
            runtime.start(new ProcessBuilder("sh", "-c", "echo new; exec sleep 20"), new File(directory,"new.log"), x -> exits.incrementAndGet());
            Thread.sleep(150);
            assertTrue(runtime.isRunning());
            assertNull(runtime.lastExit());
            assertEquals(0, exits.get());
            runtime.stop(); assertFalse(runtime.isRunning());
        } finally { runtime.stop(); }
    }
    @Test public void unexpectedExitRetainsRealCodeAndOutput() throws Exception {
        ManagedLocalRuntime runtime = new ManagedLocalRuntime();
        File directory = Files.createTempDirectory("ocean-runtime-exit").toFile();
        CountDownLatch exit = new CountDownLatch(1);
        runtime.start(new ProcessBuilder("sh", "-c", "echo actual-diagnostic; exit 17"), new File(directory,"server.log"), x -> exit.countDown());
        assertTrue(exit.await(5, TimeUnit.SECONDS));
        assertFalse(runtime.isRunning());
        assertTrue(runtime.lastExit().contains("17"));
        assertTrue(runtime.lastExit().contains("actual-diagnostic"));
    }
}
