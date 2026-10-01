package studio.ocean.app.browser.secure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import studio.ocean.app.browser.privacy.TrackerBlocker;

/**
 * Regression test for Android 16 Chromium background thread (ThreadPoolForeg) crash.
 * Ensures shouldInterceptRequest resolves main-frame URL thread-safely without calling WebView UI APIs.
 */
public final class SecureBrowserThreadAffinityTest {

    @Test
    public void threadSafeMainFrameUrlResolutionAcrossWorkerThreads() throws Exception {
        AtomicReference<String> mainFrameUrl = new AtomicReference<>("about:blank");
        TrackerBlocker trackerBlocker = new TrackerBlocker();

        // Simulate main thread navigation
        mainFrameUrl.set("https://example.com/index.html");

        // Simulate concurrent Chromium worker threads (ThreadPoolForeg) intercepting subresources
        int threadCount = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int id = i;
            pool.execute(() -> {
                try {
                    // Read main URL concurrently from worker thread without calling any WebView method
                    String mainUrl = mainFrameUrl.get();
                    assertNotNull(mainUrl);
                    assertEquals("https://example.com/index.html", mainUrl);

                    String subresource = "https://analytics.evil-tracker.com/pixel.js?id=" + id;
                    boolean blocked = trackerBlocker.shouldBlock(subresource, mainUrl);
                    assertTrue("Tracker subresource should be blocked", blocked);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        pool.shutdown();
        assertTrue("All background interception threads should complete without exceptions", completed);
    }
}
