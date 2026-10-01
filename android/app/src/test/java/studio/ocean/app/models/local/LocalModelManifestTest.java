package studio.ocean.app.models.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public final class LocalModelManifestTest {

    @Test
    public void serializesAndDeserializesCorrectly() throws Exception {
        LocalModel model = new LocalModel(
                "smollm2-360m-instruct-q4", "SmolLM2 360M Instruct (Q4_K_M)",
                "SmolLM2", "GGUF", "Q4_K_M", 229345280L, 512, 2048,
                "llama.cpp", "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", "Apache 2.0", "ARM64"
        );
        model.state = LocalModel.State.INSTALLED;

        JSONObject json = model.toJson();
        assertNotNull(json);
        assertEquals("smollm2-360m-instruct-q4", json.getString("id"));
        assertEquals("INSTALLED", json.getString("state"));

        LocalModel restored = LocalModel.fromJson(json);
        assertNotNull(restored);
        assertEquals(model.id, restored.id);
        assertEquals(model.displayName, restored.displayName);
        assertEquals(model.family, restored.family);
        assertEquals(model.quantization, restored.quantization);
        assertEquals(model.sizeBytes, restored.sizeBytes);
        assertEquals(model.minRamMb, restored.minRamMb);
        assertEquals(LocalModel.State.INSTALLED, restored.state);
        assertTrue(restored.license.contains("Apache"));
    }
}
