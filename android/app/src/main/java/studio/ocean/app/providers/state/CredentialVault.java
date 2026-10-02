package studio.ocean.app.providers.state;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/**
 * Android Keystore-backed authenticated encryption vault for sensitive provider credentials (Directive 2026-10-02 §13).
 *
 * Strictly enforces:
 *  1. Android Keystore master key with AES-GCM (random 12-byte IV per record, 128-bit authentication tag).
 *  2. Zero deterministic fallback keys (no hardcoded secrets or backdoors).
 *  3. Zero Base64 fallbacks (Base64 is encoding, not encryption).
 *  4. Fail-closed architecture: if Keystore or encryption is unavailable, never persist credentials.
 *  5. Atomic token rotation: verifies decryption and schema parse of new generation before committing.
 *  6. Token and secret redaction in all log channels.
 */
public final class CredentialVault {

    private static final String TAG = "CredentialVault";
    private static final String PREF_NAME = "ocean_provider_credential_vault";
    private static final String KEY_ALIAS = "ocean_credential_vault_master_key";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final String VERSION_PREFIX = "v2_gcm:";
    private static final String RECORD_PREFIX = "record_";

    private final SharedPreferences prefs;
    private SecretKey secretKey;

    public CredentialVault(@NonNull Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        initMasterKey();
    }

    @VisibleForTesting
    public CredentialVault(@NonNull Context context, @Nullable SecretKey testKey) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        this.secretKey = testKey;
    }

    private synchronized void initMasterKey() {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
            keyStore.load(null);
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
                keyGenerator.init(new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build());
                this.secretKey = keyGenerator.generateKey();
            } else {
                this.secretKey = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
            }
        } catch (Throwable t) {
            // Directive 2026-10-02 §13: Fail closed. Do NOT generate a deterministic key or use Base64 fallback.
            Log.e(TAG, "Android Keystore master key unavailable. Vault will fail closed.", t);
            this.secretKey = null;
        }
    }

    public synchronized boolean isAvailable() {
        return this.secretKey != null;
    }

    /**
     * Stores a secret with authenticated encryption and returns an opaque reference key.
     * Throws SecurityException if Keystore is unavailable (fail closed).
     */
    @Nullable
    public synchronized String store(@Nullable String secret) {
        if (secret == null || secret.isEmpty()) return null;
        String ref = "vault_" + UUID.randomUUID().toString().replace("-", "");
        store(ref, secret);
        return ref;
    }

    /**
     * Encrypts and stores a secret at an explicit reference key.
     * Throws SecurityException if Keystore is unavailable or cipher fails (fail closed).
     */
    public synchronized void store(@NonNull String ref, @Nullable String secret) {
        if (ref == null) return;
        if (secret == null) {
            delete(ref);
            return;
        }

        if (secretKey == null) {
            throw new SecurityException("Android Keystore master key unavailable; failing closed per Directive §13.");
        }

        try {
            String encrypted = encrypt(secret);
            prefs.edit().putString(ref, encrypted).apply();
        } catch (Exception e) {
            // Fail closed: never write Base64 or plaintext fallback
            throw new SecurityException("Authenticated encryption failed for ref: " + ref + "; failing closed.", e);
        }
    }

    /**
     * Retrieves and decrypts the secret for the given reference key.
     * Returns null if key is missing, corrupted, or decryption fails.
     */
    @Nullable
    public synchronized String retrieve(@Nullable String ref) {
        if (ref == null) return null;
        String stored = prefs.getString(ref, null);
        if (stored == null) return null;

        if (secretKey == null) {
            Log.w(TAG, "Cannot decrypt credential: master key unavailable.");
            return null;
        }

        if (stored.startsWith(VERSION_PREFIX)) {
            try {
                return decrypt(stored);
            } catch (Exception e) {
                Log.e(TAG, "Authenticated decryption failed for ref: " + ref);
                return null;
            }
        }

        // Legacy format check (v1:iv:ciphertext)
        if (stored.startsWith("v1:")) {
            try {
                return decryptV1(stored);
            } catch (Exception e) {
                Log.e(TAG, "Legacy decryption failed for ref: " + ref);
                return null;
            }
        }

        // Unrecognized / unencrypted content: delete to prevent leakage and fail closed
        Log.w(TAG, "Unauthenticated credential format detected for ref: " + ref + "; purging unverified entry.");
        delete(ref);
        return null;
    }

    /**
     * Stores a structured CredentialRecord securely under providerId.
     */
    public synchronized void storeRecord(@NonNull CredentialRecord record) {
        if (record == null) return;
        try {
            String json = record.toJson().toString();
            store(RECORD_PREFIX + record.providerId, json);
        } catch (Exception e) {
            throw new SecurityException("Failed to serialize and securely store CredentialRecord for " + record.providerId, e);
        }
    }

    /**
     * Retrieves and decrypts a structured CredentialRecord for providerId.
     */
    @Nullable
    public synchronized CredentialRecord retrieveRecord(@Nullable String providerId) {
        if (providerId == null) return null;
        String json = retrieve(RECORD_PREFIX + providerId);
        if (json == null || json.isEmpty()) return null;
        try {
            JSONObject obj = new JSONObject(json);
            return CredentialRecord.fromJson(obj);
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse CredentialRecord for provider: " + providerId, e);
            return null;
        }
    }

    /**
     * Performs atomic token rotation:
     * 1. Encrypts and parses the new CredentialRecord in memory.
     * 2. Writes the new generation ciphertext.
     * 3. Verifies retrieval before completing rotation.
     */
    public synchronized boolean rotateRecord(@NonNull CredentialRecord newRecord) {
        if (newRecord == null) return false;
        try {
            // Step 1: Pre-test serialization
            String json = newRecord.toJson().toString();
            String encrypted = encrypt(json);
            String testDecrypt = decrypt(encrypted);
            JSONObject parsed = new JSONObject(testDecrypt);
            CredentialRecord verified = CredentialRecord.fromJson(parsed);

            if (verified.tokenGeneration <= 0) {
                throw new IllegalStateException("Rotated record tokenGeneration must be positive");
            }

            // Step 2: Atomic commit to SharedPreferences
            prefs.edit().putString(RECORD_PREFIX + newRecord.providerId, encrypted).apply();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Atomic credential rotation failed for provider: " + newRecord.providerId, e);
            return false;
        }
    }

    /**
     * Deletes the secret for the given reference key.
     */
    public synchronized void delete(@Nullable String ref) {
        if (ref == null) return;
        prefs.edit().remove(ref).apply();
    }

    public synchronized void deleteRecord(@Nullable String providerId) {
        if (providerId == null) return;
        delete(RECORD_PREFIX + providerId);
    }

    public synchronized boolean exists(@Nullable String ref) {
        if (ref == null) return false;
        return prefs.contains(ref);
    }

    public synchronized boolean hasRecord(@Nullable String providerId) {
        if (providerId == null) return false;
        return exists(RECORD_PREFIX + providerId);
    }

    private String encrypt(String plainText) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] iv = cipher.getIV();
        byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        String ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP);
        String cipherB64 = Base64.encodeToString(cipherText, Base64.NO_WRAP);

        return VERSION_PREFIX + ivB64 + ":" + cipherB64;
    }

    private String decrypt(String encryptedPayload) throws Exception {
        String data = encryptedPayload.substring(VERSION_PREFIX.length());
        int colon = data.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("Malformed ciphertext payload: missing delimiter");
        }

        byte[] iv = Base64.decode(data.substring(0, colon), Base64.NO_WRAP);
        byte[] cipherText = Base64.decode(data.substring(colon + 1), Base64.NO_WRAP);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

        byte[] plainBytes = cipher.doFinal(cipherText);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }

    private String decryptV1(String encryptedPayload) throws Exception {
        String data = encryptedPayload.substring("v1:".length());
        int colon = data.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("Malformed v1 ciphertext payload: missing delimiter");
        }

        byte[] iv = Base64.decode(data.substring(0, colon), Base64.NO_WRAP);
        byte[] cipherText = Base64.decode(data.substring(colon + 1), Base64.NO_WRAP);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

        byte[] plainBytes = cipher.doFinal(cipherText);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }

    /**
     * Utility method to redact sensitive tokens in log outputs.
     */
    @NonNull
    public static String redact(@Nullable String sensitive) {
        if (sensitive == null || sensitive.isEmpty()) return "[EMPTY]";
        if (sensitive.length() <= 8) return "[REDACTED]";
        return sensitive.substring(0, 4) + "..." + sensitive.substring(sensitive.length() - 4);
    }
}
