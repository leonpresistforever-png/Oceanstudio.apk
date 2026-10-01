package studio.ocean.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.junit.Test;

/**
 * Unit test for HttpRequestEditor KeyValue validation (Directive Section 12.2).
 * Verifies 10 dynamic rows, duplicate key rejection, and empty key validation.
 */
public final class HttpRequestEditorValidationTest {

    @Test
    public void validTenRowsProduceCorrectJson() throws Exception {
        List<HttpRequestEditor.KeyValueRow> rows = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            rows.add(new HttpRequestEditor.KeyValueRow("id_" + i, "header_" + i, "value_" + i));
        }

        JSONArray result = HttpRequestEditor.validateRows(rows);
        assertEquals(10, result.length());
        for (int i = 0; i < 10; i++) {
            assertEquals("header_" + (i + 1), result.getJSONObject(i).getString("key"));
            assertEquals("value_" + (i + 1), result.getJSONObject(i).getString("value"));
        }
    }

    @Test
    public void completelyBlankRowsAreIgnored() throws Exception {
        List<HttpRequestEditor.KeyValueRow> rows = new ArrayList<>();
        rows.add(new HttpRequestEditor.KeyValueRow("1", "Content-Type", "application/json"));
        rows.add(new HttpRequestEditor.KeyValueRow("2", "", ""));
        rows.add(new HttpRequestEditor.KeyValueRow("3", "   ", ""));

        JSONArray result = HttpRequestEditor.validateRows(rows);
        assertEquals(1, result.length());
        assertEquals("Content-Type", result.getJSONObject(0).getString("key"));
    }

    @Test
    public void emptyKeyWithNonEmptyValueThrows() {
        List<HttpRequestEditor.KeyValueRow> rows = new ArrayList<>();
        rows.add(new HttpRequestEditor.KeyValueRow("1", "", "application/json"));

        try {
            HttpRequestEditor.validateRows(rows);
            fail("Should throw IllegalArgumentException on empty key with non-empty value");
        } catch (IllegalArgumentException e) {
            // Expected
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void duplicateKeyThrows() {
        List<HttpRequestEditor.KeyValueRow> rows = new ArrayList<>();
        rows.add(new HttpRequestEditor.KeyValueRow("1", "Authorization", "Bearer token1"));
        rows.add(new HttpRequestEditor.KeyValueRow("2", "Authorization", "Bearer token2"));

        try {
            HttpRequestEditor.validateRows(rows);
            fail("Should throw IllegalArgumentException on duplicate keys");
        } catch (IllegalArgumentException e) {
            // Expected
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }
    }
}
