package studio.ocean.app.terminal;

import static org.junit.Assert.assertTrue;

import android.content.Context;
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
        delete(new File(paths.root(),".ocean-bootstrap-staging"));
        delete(paths.prefix());
        new OceanBootstrapInstaller(context).install();
        File bash=new File(paths.prefix(),"bin/bash");
        assertTrue("runtime marker",OceanRuntimeState.isInstalled(context));
        assertTrue("bash exists",bash.isFile());
        assertTrue("bash executable mode",bash.canExecute());
    }
    private static void delete(File file){
        if(!file.exists())return;
        File[] children=file.listFiles();if(children!=null)for(File child:children)delete(child);
        assertTrue("delete "+file,file.delete());
    }
}
