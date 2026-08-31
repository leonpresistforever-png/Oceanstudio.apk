package studio.ocean.app.terminal;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import java.nio.charset.StandardCharsets;
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
        try{
            byte[] command="echo OCEAN_PTY_OK\rexit\r".getBytes(StandardCharsets.UTF_8);
            assertTrue("write command",NativePty.write(handle,command,command.length)==command.length);
            byte[] buffer=new byte[4096];StringBuilder output=new StringBuilder();long deadline=System.currentTimeMillis()+10000;
            while(System.currentTimeMillis()<deadline&&!output.toString().contains("OCEAN_PTY_OK")){
                int count=NativePty.read(handle,buffer);
                if(count>0)output.append(new String(buffer,0,count,StandardCharsets.UTF_8));else if(count<0)break;
            }
            assertTrue("actual PTY output: "+output,output.toString().contains("OCEAN_PTY_OK"));
        }finally{NativePty.close(handle);NativePty.destroy(handle);}
    }
}
