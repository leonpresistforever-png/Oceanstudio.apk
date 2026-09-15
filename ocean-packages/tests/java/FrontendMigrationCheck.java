package studio.ocean.app.terminal;
import java.io.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.util.Arrays;
public final class FrontendMigrationCheck {
 static byte[] legacy, replacement;
 static Path root() throws Exception { Path p=Files.createTempDirectory("ocean-frontend-test-");Files.createDirectories(p.resolve("bin"));Files.write(p.resolve("bin/pkg"),legacy);return p; }
 static boolean migrate(Path p) throws Exception {return OceanPackageFrontend.prepare(p.toFile(),new ByteArrayInputStream(replacement));}
 static void check(boolean value) {if(!value)throw new AssertionError();}
 public static void main(String[] args) throws Exception {
  legacy=Files.readAllBytes(Path.of(args[0]));replacement=Files.readAllBytes(Path.of(args[1]));
  Path p=root();check(migrate(p));check(Arrays.equals(replacement,Files.readAllBytes(p.resolve("bin/pkg"))));check(Arrays.equals(legacy,Files.readAllBytes(p.resolve("var/lib/dpkg/ocean-pkg-1.1.0.backup")))) ;check(Files.isExecutable(p.resolve("bin/pkg")));check(!migrate(p));
  p=root();Files.writeString(p.resolve("bin/pkg"),"custom");check(!migrate(p));check(Files.readString(p.resolve("bin/pkg")).equals("custom"));
  p=root();Files.createDirectories(p.resolve("var/run/ocean-pkg.lock.d"));check(!migrate(p));check(Arrays.equals(legacy,Files.readAllBytes(p.resolve("bin/pkg"))));
  p=root();Files.move(p.resolve("bin/pkg"),p.resolve("bin/original"));Files.createSymbolicLink(p.resolve("bin/pkg"),Path.of("original"));check(!migrate(p));check(Files.isSymbolicLink(p.resolve("bin/pkg")));
  for(String name:new String[]{"lock","lock-frontend"}) {p=root();Files.createDirectories(p.resolve("var/lib/dpkg"));try(FileChannel c=FileChannel.open(p.resolve("var/lib/dpkg/"+name),StandardOpenOption.CREATE,StandardOpenOption.WRITE);FileLock held=c.lock()){check(!migrate(p));check(Arrays.equals(legacy,Files.readAllBytes(p.resolve("bin/pkg"))));}}
  System.out.println("PASS: known upgrade, backup/idempotence, custom frontend, wrapper lock, symlink and both dpkg locks");
 }
}
