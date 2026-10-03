#!/usr/bin/env python3
"""Execute actual JVM endpoint validation and loopback callback handling, without Android SDK."""
from pathlib import Path
import subprocess
import tempfile
import shutil

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'android/app/src/main/java/studio/ocean/app'
HARNESS = r'''
package studio.ocean.app.mcp;
import studio.ocean.app.OceanModelConfig;
import java.net.*;
import java.io.*;
import java.util.concurrent.*;
public class RuntimeAuthBoundaryCheck {
  static void require(boolean value) { if (!value) throw new AssertionError(); }
  public static void main(String[] args) throws Exception {
    OceanModelConfig local = new OceanModelConfig("local", "qwen-test", "", "http://127.0.0.1:8080/v1");
    require(local.endpoint().equals("http://127.0.0.1:8080/v1/chat/completions"));
    for (String invalid : new String[]{"http://example.com:8080/v1", "http://127.0.0.1/v1", "http://user@127.0.0.1:8080/v1", "http://127.0.0.1:8080/v1?x=1"}) {
      try { new OceanModelConfig("local", "qwen-test", "", invalid); throw new AssertionError(invalid); }
      catch (IllegalArgumentException expected) { }
    }
    try { new OceanModelConfig("openai", "test", "", "https://example.com/v1"); throw new AssertionError(); }
    catch (IllegalArgumentException expected) { }
    require(OAuthLoopbackReceiver.acceptedCallback("GET /callback?state=s&code=c HTTP/1.1", "http://127.0.0.1:1/callback", "s") != null);
    for (String request : new String[]{"GET /callback?state=wrong&code=c HTTP/1.1", "GET /favicon.ico?state=s&code=c HTTP/1.1", "POST /callback?state=s&code=c HTTP/1.1", "GET /callback?state=s&state=s&code=c HTTP/1.1", "GET http://evil.test/callback?state=s&code=c HTTP/1.1"}) {
      require(OAuthLoopbackReceiver.acceptedCallback(request, "http://127.0.0.1:1/callback", "s") == null);
    }
    CountDownLatch completion = new CountDownLatch(1);
    String[] result = new String[1];
    try (OAuthLoopbackReceiver receiver = new OAuthLoopbackReceiver()) {
      receiver.listen("expected", new OAuthLoopbackReceiver.Listener() {
        public void received(String callback) { result[0] = callback; completion.countDown(); }
        public void failed(String error) { result[0] = "ERROR"; completion.countDown(); }
      });
      HttpURLConnection bad = (HttpURLConnection) new URL(receiver.redirectUri() + "?state=wrong&code=x").openConnection(Proxy.NO_PROXY);
      require(bad.getResponseCode() == 400); bad.disconnect();
      require(completion.getCount() == 1);
      HttpURLConnection good = (HttpURLConnection) new URL(receiver.redirectUri() + "?state=expected&code=actual").openConnection(Proxy.NO_PROXY);
      require(good.getResponseCode() == 200); good.disconnect();
      require(completion.await(3, TimeUnit.SECONDS));
      require(result[0].contains("code=actual"));
    }
    System.out.println("PASS: local endpoint and real loopback callback boundaries");
  }
}
'''

with tempfile.TemporaryDirectory() as directory:
    work = Path(directory)
    harness = work / 'RuntimeAuthBoundaryCheck.java'
    harness.write_text(HARNESS)
    compiler = ['javac'] if shutil.which('javac') else ['java', 'com.sun.tools.javac.Main']
    subprocess.run(compiler + ['-d', directory, str(JAVA / 'OceanModelConfig.java'),
                    str(JAVA / 'mcp/OAuthLoopbackReceiver.java'), str(harness)], check=True)
    subprocess.run(['java', '-cp', directory, 'studio.ocean.app.mcp.RuntimeAuthBoundaryCheck'], check=True)
    syntax = work / 'JavaSyntaxCheck.java'
    syntax.write_text('''
import javax.tools.*;
import com.sun.source.util.JavacTask;
import java.util.*;
public class JavaSyntaxCheck {
  public static void main(String[] files) throws Exception {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
    try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, null, null)) {
      JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics, Arrays.asList("-proc:none"), null,
        manager.getJavaFileObjectsFromStrings(Arrays.asList(files)));
      task.parse();
      for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
        if (diagnostic.getKind() == Diagnostic.Kind.ERROR) throw new AssertionError(diagnostic.toString());
      }
    }
    System.out.println("PASS: Java syntax parsing (Android linking requires SDK)");
  }
}
''')
    subprocess.run(compiler + ['-d', directory, str(syntax)], check=True)
    subprocess.run(['java', '-cp', directory, 'JavaSyntaxCheck'] + [str(p) for p in JAVA.rglob('*.java')], check=True)
