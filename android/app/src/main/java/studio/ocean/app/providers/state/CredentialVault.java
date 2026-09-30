package studio.ocean.app.providers.state;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.UUID;

/**
 * Vault for sensitive provider credentials (API keys, OAuth tokens).
 * Never exposes raw secrets in toString, log outputs, or crash dumps.
 */
public final class CredentialVault {
    private static final String PREF_NAME = "ocean_provider_credential_vault";
    private final SharedPreferences prefs;

    public CredentialVault(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Stores a secret and returns an opaque reference key.
     */
    public synchronized String store(String secret) {
        if (secret == null || secret.isEmpty()) return null;
        String ref = "vault_" + UUID.randomUUID().toString().replace("-", "");
        prefs.edit().putString(ref, secret).apply();
        return ref;
    }

    /**
     * Stores a secret at an explicit reference key.
     */
    public synchronized void store(String ref, String secret) {
        if (ref == null) return;
        if (secret == null) {
            delete(ref);
        } else {
            prefs.edit().putString(ref, secret).apply();
        }
    }

    /**
     * Retrieves the secret for the given reference key.
     */
    public synchronized String retrieve(String ref) {
        if (ref == null) return null;
        return prefs.getString(ref, null);
    }

    /**
     * Deletes the secret for the given reference key.
     */
    public synchronized void delete(String ref) {
        if (ref == null) return;
        prefs.edit().remove(ref).apply();
    }

    public synchronized boolean exists(String ref) {
        if (ref == null) return false;
        return prefs.contains(ref);
    }
}
