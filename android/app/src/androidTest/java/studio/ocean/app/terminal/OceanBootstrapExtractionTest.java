package studio.ocean.app.terminal;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.system.Os;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.File;
import org.junit.Test;
import org.junit.runner.RunWith;
import studio.ocean.app.OceanPaths;

/** Device-only proof that the production zstd/JNI extractor installs the bundled runtime. */
@RunWith(AndroidJUnit4.class)
public final class OceanBootstrapExtractionTest {
    @Test public void bundledBootstrapExtractsWithAndroidArm64Jni() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        OceanPaths paths=new OceanPaths(context);
        File stale=new File(paths.root(),".ocean-bootstrap-staging");
        delete(stale);
        delete(paths.prefix());
        delete(paths.runtimeMarker());

        // Reproduce the failed installer left behind by the previous APK: a fixed staging
        // tree with restrictive archive modes and a symlink that must be unlinked, not followed.
        File staleBin=new File(stale,"usr/bin");
        assertTrue("create stale bin",staleBin.mkdirs());
        File staleExecutable=new File(staleBin,"old-tool");
        assertTrue("create stale executable",staleExecutable.createNewFile());
        Os.chmod(staleExecutable.getAbsolutePath(),0500);
        File outside=new File(paths.root(),"staging-delete-sentinel");
        delete(outside);assertTrue("create sentinel",outside.createNewFile());
        Os.symlink(outside.getAbsolutePath(),new File(staleBin,"outside-link").getAbsolutePath());
        Os.chmod(staleBin.getAbsolutePath(),0500);
        Os.chmod(new File(stale,"usr").getAbsolutePath(),0500);

        new OceanBootstrapInstaller(context).install();
        File bash=new File(paths.prefix(),"bin/bash");
        assertTrue("runtime marker",OceanRuntimeState.isInstalled(context));
        assertTrue("bash exists",bash.isFile());
        assertTrue("bash executable mode",bash.canExecute());
        assertTrue("stale staging is nonblocking",!stale.exists());
        assertTrue("symlink target was not followed",outside.isFile());
        delete(outside);
    }
    private static void delete(File file){
        if(!file.exists())return;
        File[] children=file.listFiles();if(children!=null)for(File child:children)delete(child);
        assertTrue("delete "+file,file.delete());
    }
}
