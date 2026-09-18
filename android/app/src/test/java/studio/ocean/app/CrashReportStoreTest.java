package studio.ocean.app;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.io.File;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class CrashReportStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test public void reportSurvivesActualUncaughtExceptionAndProcessExit() throws Exception {
        File file = new File(temporary.getRoot(), "crash/report.txt");
        String separator = File.pathSeparator;
        String cp = new File(CrashReportStore.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                + separator + new File(CrashProcess.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        Process child = new ProcessBuilder(System.getProperty("java.home") + "/bin/java", "-cp", cp,
                CrashProcess.class.getName(), file.getAbsolutePath()).redirectErrorStream(true).start();
        try {
            assertTrue("Crash subprocess timed out", child.waitFor(10, TimeUnit.SECONDS));
            assertEquals(17, child.exitValue());
            String reopened = CrashReportStore.read(file);
            assertTrue(reopened.contains("RENDER_AGENT_RESPONSE"));
            assertTrue(reopened.contains("IllegalStateException: simulated agent crash"));
            assertTrue(reopened.contains("Caused by: java.lang.NullPointerException: missing view"));
            assertTrue(reopened.contains("CrashReportStoreTest.java:"));
            assertEquals(reopened, CrashReportStore.read(file));
        } finally { child.destroyForcibly(); }
    }

    @Test public void reopeningOversizedDiagnosticIsBounded() throws Exception {
        File file=temporary.newFile();
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)) { out.write(new byte[1024*1024]); }
        assertEquals(CrashReportStore.LIMIT,CrashReportStore.read(file).length());
    }

    public static class CrashProcess {
        public static void main(String[] args) {
            Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
                try { CrashReportStore.saveException(new File(args[0]),"Last step: RENDER_AGENT_RESPONSE",thread,error); }
                catch(Exception failure) { System.exit(18); }
                System.exit(17);
            });
            throw new IllegalStateException("simulated agent crash", new NullPointerException("missing view"));
        }
    }
}
