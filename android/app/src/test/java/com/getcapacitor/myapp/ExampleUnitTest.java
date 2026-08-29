package studio.ocean.app;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {

    @Test
    public void addition_isCorrect() throws Exception {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void developmentAuthBypassCannotActivateOutsideDebug() {
        assertFalse(!BuildConfig.DEBUG && BuildConfig.OCEAN_DEV_AUTH_BYPASS);
    }
}
