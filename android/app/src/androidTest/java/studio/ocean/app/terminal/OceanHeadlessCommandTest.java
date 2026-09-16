package studio.ocean.app.terminal;

import static org.junit.Assert.*;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Device regression checks for the same service used by the model, with no terminal Activity. */
@RunWith(AndroidJUnit4.class)
public final class OceanHeadlessCommandTest {
    @Test public void fastCommandPreservesOutputAndActualExitCode() throws Exception {
        withService(service -> {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            CountDownLatch done = new CountDownLatch(1); AtomicInteger exit = new AtomicInteger(-1), callbacks = new AtomicInteger();
            AtomicReference<Throwable> failure = new AtomicReference<>();
            service.requestCommand("printf 'OCEAN_HEADLESS_OK\\n'; i=0; while [ $i -lt 2000 ]; do printf 'line-%s\\n' \"$i\"; i=$((i+1)); done; exit 7", null, 120,
                    new OceanTerminalRuntimeService.CommandCallback() {
                        public void onOutput(byte[] bytes, int length) { output.write(bytes, 0, length); }
                        public void onExit(int code) { exit.set(code); callbacks.incrementAndGet(); done.countDown(); }
                        public void onFailure(Throwable error) { failure.set(error); done.countDown(); }
                    });
            assertTrue("command finished without opening terminal", done.await(130, TimeUnit.SECONDS));
            assertNull(failure.get()); assertEquals(7, exit.get()); assertEquals(1, callbacks.get());
            String text = new String(output.toByteArray(), StandardCharsets.UTF_8);
            assertTrue(text.contains("OCEAN_HEADLESS_OK")); assertTrue("tail output drained", text.contains("line-1999"));
        });
    }

    @Test public void timeoutStopsCommandAndHeadlessSessionIsNotInteractiveSession() throws Exception {
        withService(service -> {
            CountDownLatch output = new CountDownLatch(1), done = new CountDownLatch(1);
            AtomicInteger exit = new AtomicInteger(-1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            OceanTerminalRuntimeService.CommandHandle command = service.requestCommand("printf 'READY\\n'; sleep 30", null, 3,
                    new OceanTerminalRuntimeService.CommandCallback() {
                        public void onOutput(byte[] bytes, int length) { output.countDown(); }
                        public void onExit(int code) { exit.set(code); done.countDown(); }
                        public void onFailure(Throwable error) { failure.set(error); done.countDown(); }
                    });
            assertTrue(output.await(3, TimeUnit.SECONDS));
            assertNull("headless command must not be returned to a terminal Activity", service.firstRunning());
            assertTrue(done.await(10, TimeUnit.SECONDS)); assertNull(failure.get()); assertEquals(124, exit.get()); assertTrue(command.timedOut());
        });
    }

    private interface Check { void run(OceanTerminalRuntimeService service) throws Exception; }
    private void withService(Check check) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        new OceanBootstrapInstaller(context).install();
        CountDownLatch bound = new CountDownLatch(1); AtomicReference<OceanTerminalRuntimeService> service = new AtomicReference<>();
        ServiceConnection connection = new ServiceConnection() {
            public void onServiceConnected(ComponentName name, IBinder binder) { service.set(((OceanTerminalRuntimeService.LocalBinder) binder).service()); bound.countDown(); }
            public void onServiceDisconnected(ComponentName name) {}
        };
        assertTrue(context.bindService(new Intent(context, OceanTerminalRuntimeService.class), connection, Context.BIND_AUTO_CREATE));
        try { assertTrue(bound.await(10, TimeUnit.SECONDS)); check.run(service.get()); }
        finally { context.unbindService(connection); }
    }
}
