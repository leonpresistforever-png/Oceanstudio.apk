package studio.ocean.app.terminal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Device-side proof that production output is emulated rather than rendered as raw ANSI. */
@RunWith(AndroidJUnit4.class)
public final class OceanTerminalEmulatorTest {
    @Test public void ansiPromptUpdatesScreenWithoutLiteralEscapes() {
        AtomicReference<String> transcript = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            OceanEmulatorSession session = new OceanEmulatorSession();
            session.updateSize(80, 24);
            byte[] prompt = "\u001b[?2004h\u001b[0;32m~\u001b[0m \u001b[0;97m$\u001b[0m ".getBytes(StandardCharsets.UTF_8);
            session.feed(prompt, prompt.length);
            transcript.set(session.getTranscriptText());
            session.finish();
        });
        assertTrue(transcript.get().contains("~ $"));
        assertFalse(transcript.get().contains("[?2004h"));
        assertFalse(transcript.get().contains("[0;32m"));
    }
}
