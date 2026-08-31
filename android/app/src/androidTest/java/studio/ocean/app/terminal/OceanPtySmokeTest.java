package studio.ocean.app.terminal;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import studio.ocean.app.OceanPaths;

/** Device-only control and Ocean Bash checks using the exact production JNI PTY. */
@RunWith(AndroidJUnit4.class)
public final class OceanPtySmokeTest {
    @Test public void androidSystemShellSurvivesProductionPty() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        OceanPaths paths=new OceanPaths(context);paths.ensureDirectoryContract();
        assertPtyEcho(context,"/system/bin/sh",new String[]{"/system/bin/sh"},paths.home());
    }

    @Test public void oceanBashSurvivesProductionPty() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        new OceanBootstrapInstaller(context).install();
        OceanPaths paths=new OceanPaths(context);
        String bash=new File(paths.prefix(),"bin/bash").getAbsolutePath();
        assertPtyEcho(context,bash,new String[]{bash,"--noprofile","--norc"},paths.home());
    }

    private static void assertPtyEcho(Context context,String executable,String[] argv,File cwd) throws Exception {
        File log=new File(new File(context.getFilesDir(),"logs"),"pty-instrumentation.log");
        String[] environment=OceanEnvironment.create(context,executable);
        long handle=NativePty.create(executable,argv,environment,cwd.getAbsolutePath(),24,80,log.getAbsolutePath());
        assertTrue("PTY handle",handle!=0);
        CountDownLatch outputSeen=new CountDownLatch(1),exited=new CountDownLatch(1);StringBuilder output=new StringBuilder();AtomicInteger status=new AtomicInteger(-1);
        TerminalSession session=new TerminalSession(context,handle,s->{ });
        session.addListener(new TerminalSession.Listener(){public void onOutput(byte[] bytes,int length){synchronized(output){output.append(new String(bytes,0,length,StandardCharsets.UTF_8));if(output.indexOf("OCEAN_PTY_OK")>=0)outputSeen.countDown();}}public void onExit(int code){status.set(code);exited.countDown();}});
        session.startWorkers();
        assertTrue("write echo",session.write("echo OCEAN_PTY_OK\r")==TerminalSession.WriteResult.WRITTEN);
        assertTrue("actual PTY output",outputSeen.await(10,TimeUnit.SECONDS));
        assertTrue("write exit once",session.write("exit\r")==TerminalSession.WriteResult.WRITTEN);
        assertTrue("session exit callback",exited.await(10,TimeUnit.SECONDS));
        assertTrue("real child exit="+status.get(),status.get()==0);
        assertTrue("terminal state",session.state()==TerminalSession.State.EXITED);
        assertTrue("late write rejected",session.write("echo too-late\r")==TerminalSession.WriteResult.SESSION_ALREADY_EXITED);
    }
}
