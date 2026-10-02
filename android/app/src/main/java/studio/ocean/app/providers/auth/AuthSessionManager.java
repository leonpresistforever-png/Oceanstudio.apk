package studio.ocean.app.providers.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONObject;

/**
 * Manages OAuth transaction lifecycles and persistence across process death (Directive 2026-10-02 §11, §12).
 * Strictly guarantees that Activity recreation does not orphan valid callbacks,
 * duplicate callbacks are rejected as replays, and expired transactions are purged.
 */
public final class AuthSessionManager {

    private static final String TAG = "AuthSessionManager";
    private static final String PREF_NAME = "ocean_auth_transactions";
    private static final long DEFAULT_TTL_MS = 10 * 60 * 1000L; // 10 minutes

    private final SharedPreferences prefs;
    private final Map<String, AuthTransaction> memoryCache = new ConcurrentHashMap<>();

    public AuthSessionManager(@NonNull Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadPersistedTransactions();
    }

    private synchronized void loadPersistedTransactions() {
        try {
            Map<String, ?> all = prefs.getAll();
            long now = System.currentTimeMillis();
            SharedPreferences.Editor editor = prefs.edit();
            for (Map.Entry<String, ?> entry : all.entrySet()) {
                if (entry.getValue() instanceof String) {
                    try {
                        JSONObject obj = new JSONObject((String) entry.getValue());
                        AuthTransaction tx = AuthTransaction.fromJson(obj);
                        if (tx.isExpired()) {
                            editor.remove(entry.getKey());
                        } else {
                            memoryCache.put(tx.id, tx);
                        }
                    } catch (Exception e) {
                        editor.remove(entry.getKey());
                    }
                }
            }
            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed loading persisted transactions", e);
        }
    }

    /**
     * Creates, caches, and persists a fresh AuthTransaction.
     */
    @NonNull
    public synchronized AuthTransaction createTransaction(@NonNull String id,
                                                          @NonNull String providerId,
                                                          @NonNull String state,
                                                          @Nullable String nonce,
                                                          @Nullable String codeVerifier,
                                                          @Nullable String codeChallenge,
                                                          @NonNull String redirectDescriptor,
                                                          @Nullable String clientRegistrationRef,
                                                          @Nullable List<String> scopes,
                                                          @Nullable String browserSessionId) {
        long now = System.currentTimeMillis();
        long expiresAt = now + DEFAULT_TTL_MS;
        AuthTransaction tx = new AuthTransaction(
                id, providerId, state, nonce, codeVerifier, codeChallenge,
                redirectDescriptor, clientRegistrationRef, scopes,
                now, expiresAt, AuthTransaction.PHASE_INITIALIZED, browserSessionId, false
        );

        saveTransaction(tx);
        return tx;
    }

    public synchronized void saveTransaction(@NonNull AuthTransaction tx) {
        memoryCache.put(tx.id, tx);
        try {
            prefs.edit().putString(tx.id, tx.toJson().toString()).apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to persist AuthTransaction " + tx.id, e);
        }
    }

    @Nullable
    public synchronized AuthTransaction getTransaction(@Nullable String id) {
        if (id == null) return null;
        AuthTransaction tx = memoryCache.get(id);
        if (tx == null) {
            String stored = prefs.getString(id, null);
            if (stored != null) {
                try {
                    tx = AuthTransaction.fromJson(new JSONObject(stored));
                    memoryCache.put(id, tx);
                } catch (Exception ignored) {}
            }
        }
        if (tx != null && tx.isExpired()) {
            removeTransaction(id);
            return null;
        }
        return tx;
    }

    @Nullable
    public synchronized AuthTransaction getTransactionByState(@Nullable String state) {
        if (state == null) return null;
        for (AuthTransaction tx : memoryCache.values()) {
            if (state.equals(tx.state)) {
                if (tx.isExpired()) {
                    removeTransaction(tx.id);
                    return null;
                }
                return tx;
            }
        }
        // Fallback scan disk in case not loaded in memory cache
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (entry.getValue() instanceof String) {
                try {
                    JSONObject obj = new JSONObject((String) entry.getValue());
                    AuthTransaction tx = AuthTransaction.fromJson(obj);
                    if (state.equals(tx.state)) {
                        if (tx.isExpired()) {
                            removeTransaction(tx.id);
                            return null;
                        }
                        memoryCache.put(tx.id, tx);
                        return tx;
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    /**
     * Marks transaction as consumed to prevent replay attacks.
     * Throws IllegalStateException if the transaction was already consumed.
     */
    @NonNull
    public synchronized AuthTransaction consumeTransaction(@NonNull String id) {
        AuthTransaction tx = getTransaction(id);
        if (tx == null) {
            throw new IllegalArgumentException("Unknown or expired transaction: " + id);
        }
        if (tx.replayConsumed) {
            throw new IllegalStateException("Security violation: OAuth transaction " + id + " has already been consumed (replay attempt).");
        }

        AuthTransaction consumed = tx.withConsumed().withPhase(AuthTransaction.PHASE_CODE_RECEIVED);
        saveTransaction(consumed);
        return consumed;
    }

    public synchronized void updatePhase(@NonNull String id, @NonNull String phase) {
        AuthTransaction tx = getTransaction(id);
        if (tx != null) {
            saveTransaction(tx.withPhase(phase));
        }
    }

    public synchronized void removeTransaction(@Nullable String id) {
        if (id == null) return;
        memoryCache.remove(id);
        prefs.edit().remove(id).apply();
    }

    public synchronized void purgeExpired() {
        for (Map.Entry<String, AuthTransaction> entry : memoryCache.entrySet()) {
            if (entry.getValue().isExpired()) {
                removeTransaction(entry.getKey());
            }
        }
    }
}
