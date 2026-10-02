package studio.ocean.app.providers.auth;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Immutable record representing an active or completed OAuth authorization transaction (Directive 2026-10-02 §12).
 * Persisted across Android activity recreation and process death to prevent orphaning browser callbacks.
 * Enforces one-time consumption (replay prevention) and strict expiry.
 */
public final class AuthTransaction {

    public static final String PHASE_INITIALIZED = "INITIALIZED";
    public static final String PHASE_BROWSER_ACTIVE = "BROWSER_ACTIVE";
    public static final String PHASE_CODE_RECEIVED = "CODE_RECEIVED";
    public static final String PHASE_VERIFYING = "VERIFYING";
    public static final String PHASE_COMPLETED = "COMPLETED";
    public static final String PHASE_CANCELLED = "CANCELLED";
    public static final String PHASE_FAILED = "FAILED";

    @NonNull public final String id;
    @NonNull public final String providerId;
    @NonNull public final String state;
    @Nullable public final String nonce;
    @Nullable public final String codeVerifier;
    @Nullable public final String codeChallenge;
    @NonNull public final String redirectDescriptor;
    @Nullable public final String clientRegistrationRef;
    @NonNull public final List<String> requestedScopes;
    public final long createdAtEpochMs;
    public final long expiresAtEpochMs;
    @NonNull public final String phase;
    @Nullable public final String browserSessionId;
    public final boolean replayConsumed;

    public AuthTransaction(@NonNull String id,
                           @NonNull String providerId,
                           @NonNull String state,
                           @Nullable String nonce,
                           @Nullable String codeVerifier,
                           @Nullable String codeChallenge,
                           @NonNull String redirectDescriptor,
                           @Nullable String clientRegistrationRef,
                           @Nullable List<String> requestedScopes,
                           long createdAtEpochMs,
                           long expiresAtEpochMs,
                           @NonNull String phase,
                           @Nullable String browserSessionId,
                           boolean replayConsumed) {
        this.id = id;
        this.providerId = providerId;
        this.state = state;
        this.nonce = nonce;
        this.codeVerifier = codeVerifier;
        this.codeChallenge = codeChallenge;
        this.redirectDescriptor = redirectDescriptor;
        this.clientRegistrationRef = clientRegistrationRef;
        this.requestedScopes = requestedScopes != null ? Collections.unmodifiableList(new ArrayList<>(requestedScopes)) : Collections.emptyList();
        this.createdAtEpochMs = createdAtEpochMs;
        this.expiresAtEpochMs = expiresAtEpochMs;
        this.phase = phase;
        this.browserSessionId = browserSessionId;
        this.replayConsumed = replayConsumed;
    }

    public boolean isExpired() {
        return expiresAtEpochMs > 0 && System.currentTimeMillis() >= expiresAtEpochMs;
    }

    @NonNull
    public AuthTransaction withPhase(@NonNull String newPhase) {
        return new AuthTransaction(
                this.id,
                this.providerId,
                this.state,
                this.nonce,
                this.codeVerifier,
                this.codeChallenge,
                this.redirectDescriptor,
                this.clientRegistrationRef,
                this.requestedScopes,
                this.createdAtEpochMs,
                this.expiresAtEpochMs,
                newPhase,
                this.browserSessionId,
                this.replayConsumed
        );
    }

    @NonNull
    public AuthTransaction withConsumed() {
        return new AuthTransaction(
                this.id,
                this.providerId,
                this.state,
                this.nonce,
                this.codeVerifier,
                this.codeChallenge,
                this.redirectDescriptor,
                this.clientRegistrationRef,
                this.requestedScopes,
                this.createdAtEpochMs,
                this.expiresAtEpochMs,
                this.phase,
                this.browserSessionId,
                true // Replay consumed
        );
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("id", id);
        obj.put("providerId", providerId);
        obj.put("state", state);
        if (nonce != null) obj.put("nonce", nonce);
        if (codeVerifier != null) obj.put("codeVerifier", codeVerifier);
        if (codeChallenge != null) obj.put("codeChallenge", codeChallenge);
        obj.put("redirectDescriptor", redirectDescriptor);
        if (clientRegistrationRef != null) obj.put("clientRegistrationRef", clientRegistrationRef);
        JSONArray sc = new JSONArray();
        for (String s : requestedScopes) sc.put(s);
        obj.put("requestedScopes", sc);
        obj.put("createdAtEpochMs", createdAtEpochMs);
        obj.put("expiresAtEpochMs", expiresAtEpochMs);
        obj.put("phase", phase);
        if (browserSessionId != null) obj.put("browserSessionId", browserSessionId);
        obj.put("replayConsumed", replayConsumed);
        return obj;
    }

    @NonNull
    public static AuthTransaction fromJson(@NonNull JSONObject obj) {
        String id = obj.optString("id", "");
        String providerId = obj.optString("providerId", "");
        String state = obj.optString("state", "");
        String nonce = obj.has("nonce") ? obj.optString("nonce") : null;
        String codeVerifier = obj.has("codeVerifier") ? obj.optString("codeVerifier") : null;
        String codeChallenge = obj.has("codeChallenge") ? obj.optString("codeChallenge") : null;
        String redirectDescriptor = obj.optString("redirectDescriptor", "");
        String clientRegistrationRef = obj.has("clientRegistrationRef") ? obj.optString("clientRegistrationRef") : null;

        List<String> scopes = new ArrayList<>();
        JSONArray sc = obj.optJSONArray("requestedScopes");
        if (sc != null) {
            for (int i = 0; i < sc.length(); i++) scopes.add(sc.optString(i));
        }

        long createdAt = obj.optLong("createdAtEpochMs", 0L);
        long expiresAt = obj.optLong("expiresAtEpochMs", 0L);
        String phase = obj.optString("phase", PHASE_INITIALIZED);
        String browserSessionId = obj.has("browserSessionId") ? obj.optString("browserSessionId") : null;
        boolean replayConsumed = obj.optBoolean("replayConsumed", false);

        return new AuthTransaction(
                id, providerId, state, nonce, codeVerifier, codeChallenge,
                redirectDescriptor, clientRegistrationRef, scopes,
                createdAt, expiresAt, phase, browserSessionId, replayConsumed
        );
    }
}
