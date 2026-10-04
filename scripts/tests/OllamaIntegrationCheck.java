import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.concurrent.*;
import studio.ocean.app.models.local.ManagedLocalRuntime;
import studio.ocean.app.models.local.OllamaClient;
import studio.ocean.app.models.local.LocalGenerationSettings;

public final class OllamaIntegrationCheck {
    public static void main(String[] args) throws Exception {
        File executable = new File(args[0]), backend = new File(args[1]), model = new File(args[2]);
        Path work = Files.createTempDirectory("ocean-real-ollama-");
        Path helpers = work.resolve("build/lib/ollama"); Files.createDirectories(helpers);
        Files.createSymbolicLink(helpers.resolve("llama-server"), backend.toPath().toAbsolutePath());
        int port;
        try (ServerSocket available = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) { port = available.getLocalPort(); }
        ManagedLocalRuntime runtime = new ManagedLocalRuntime();
        File log = work.resolve("server.log").toFile();
        ProcessBuilder process = new ProcessBuilder(executable.getAbsolutePath(), "serve").directory(work.toFile());
        process.environment().put("HOME", work.toString());
        process.environment().put("OLLAMA_HOST", "127.0.0.1:" + port);
        process.environment().put("OLLAMA_MODELS", work.resolve("models").toString());
        process.environment().put("OLLAMA_NO_CLOUD", "true");
        process.environment().put("OLLAMA_MAX_LOADED_MODELS", "1");
        process.environment().put("OLLAMA_NUM_PARALLEL", "1");
        OllamaClient client = new OllamaClient(port);
        try {
            for (int restart = 0; restart < 2; restart++) {
                runtime.start(process, log, diagnostic -> { });
                long deadline = System.currentTimeMillis() + 60000;
                while (true) {
                    if (!runtime.isRunning()) throw new AssertionError(runtime.lastExit());
                    try { client.version(); break; }
                    catch (Exception starting) { if (System.currentTimeMillis() > deadline) throw starting; Thread.sleep(200); }
                }
                String alias = client.importModel("stories", model, 512);
                int trainingContext = OllamaClient.trainedContext(client.show(alias));
                if (trainingContext != 128) throw new AssertionError("Official fixture training context changed: " + trainingContext);
                LocalGenerationSettings settings = new LocalGenerationSettings(512, 16, .2f, .8f, 12, 1.1f, 1, true, false, trainingContext);
                client.load(alias, settings);
                if (client.infer(alias, settings).isEmpty()) throw new AssertionError("No real generated text");
                if (client.loadedContext(alias) != settings.context) throw new AssertionError("Native context setting was not applied");
                LocalGenerationSettings smaller = new LocalGenerationSettings(64, 16, .2f, .8f, 12, 1.1f, 1, true, false, 64);
                client.unload(alias); client.load(alias, smaller); client.infer(alias, smaller);
                if (client.loadedContext(alias) != 64) throw new AssertionError("Explicit smaller context was not applied");
                client.unload(alias); client.load(alias, settings);
                if (client.show(alias).optJSONArray("capabilities") == null) throw new AssertionError("Runtime did not report capabilities");
                Thread.sleep(5500); // Survive the reported four-second disconnect window.
                if (!runtime.isRunning() || client.infer(alias, settings).isEmpty()) throw new AssertionError("Connection did not survive");
                client.unload(alias); client.load(alias, settings); client.infer(alias, settings);
                runtime.stop();
                if (runtime.isRunning()) throw new AssertionError("Owned server did not stop");
            }
            System.out.println("PASS: official Ollama startup, GGUF import, actual inference, sustained connection and reconnect");
        } catch (Exception | AssertionError error) {
            System.err.println(ManagedLocalRuntime.tail(log)); throw error;
        } finally { runtime.stop(); }
    }
}
