package studio.ocean.app.terminal;

import android.system.Os;
import android.system.StructStat;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import studio.ocean.app.OceanPaths;

/** Device-side validation performed before the native PTY receives a shell. */
public final class OceanRuntimeValidator {
    public static final class Result {
        public final boolean valid;
        public final String details;
        Result(boolean valid, String details) { this.valid=valid; this.details=details; }
        public void requireValid() throws IOException { if (!valid) throw new IOException(details); }
    }
    private OceanRuntimeValidator() {}

    public static Result validate(OceanPaths paths) {
        List<String> failures = new ArrayList<>();
        requireDirectory(paths.home(), failures);
        requireDirectory(paths.prefix(), failures);
        requireDirectory(new File(paths.prefix(), "bin"), failures);
        requireDirectory(new File(paths.prefix(), "lib"), failures);
        requireDirectory(paths.temp(), failures);
        File shell = new File(paths.prefix(), "bin/bash");
        if (!shell.isFile()) failures.add("bash is not a regular file: " + shell);
        else {
            if (!shell.canExecute()) failures.add("bash is not executable: " + shell);
            try {
                StructStat stat=Os.stat(shell.getAbsolutePath());
                TerminalStartupLog.stage("07", "bash size="+stat.st_size+" mode="+Integer.toOctalString(stat.st_mode));
            } catch(Exception error) { failures.add("bash stat failed: "+error); }
            validateAarch64Elf(shell, failures);
        }
        for (String name : new String[]{"apt","dpkg","pkg"}) {
            File executable=new File(paths.prefix(),"bin/"+name);
            if (!executable.isFile() || !executable.canExecute()) failures.add(name+" missing or not executable");
        }
        String details=failures.isEmpty()?"Ocean runtime validation passed":String.join("; ",failures);
        TerminalStartupLog.stage("08", details);
        return new Result(failures.isEmpty(), details);
    }

    private static void requireDirectory(File file,List<String> failures){
        if(!file.isDirectory() || !file.canRead() || !file.canWrite()) failures.add("directory unavailable: "+file);
    }
    private static void validateAarch64Elf(File file,List<String> failures){
        byte[] h=new byte[64];
        try(FileInputStream in=new FileInputStream(file)){
            if(in.read(h)!=h.length)throw new IOException("short ELF header");
            if(h[0]!=0x7f||h[1]!='E'||h[2]!='L'||h[3]!='F')throw new IOException("not ELF");
            if(h[4]!=2)throw new IOException("not ELF64");
            int machine=(h[18]&255)|((h[19]&255)<<8);
            if(machine!=183)throw new IOException("ELF machine "+machine+" is not AArch64");
        }catch(Exception error){failures.add("bash ELF validation failed: "+error.getMessage());}
    }
}
