package studio.ocean.app.providers.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Structured credential record stored securely in Android Keystore CredentialVault (Directive 2026-10-02 §13).
 * Encapsulates access, refresh, and ID tokens, scopes, and verification metadata.
 * Strictly guarantees fail-closed security and atomic token rotation.
 */
public final class CredentialRecord {

    @NonNull public final String providerId;
    @Nullable public final String accountSubject;
    @NonNull public final String accessToken;
    @Nullable public final String refreshToken;
    @Nullable public final String idToken;
    public final long expiresAtEpochMs;
    @NonNull public final List<String> scopes;
    @Nullable public final String issuer;
    @Nullable public final String clientId;
    @Nullable public final String accountDisplayName;
    @Nullable public final String planMetadata;
    public final long lastRefreshAtEpochMs;
    public final long lastVerifiedAtEpochMs;
    public final long tokenGeneration;

    public CredentialRecord(@NonNull String providerId,
                            @Nullable String accountSubject,
                            @NonNull String accessToken,
                            @Nullable String refreshToken,
                            @Nullable String idToken,
                            long expiresAtEpochMs,
                            @Nullable List<String> scopes,
                            @Nullable String issuer,
                            @Nullable String clientId,
                            @Nullable String accountDisplayName,
                            @Nullable String planMetadata,
                            long lastRefreshAtEpochMs,
                            long lastVerifiedAtEpochMs,
                            long tokenGeneration) {
        this.providerId = providerId;
        this.accountSubject = accountSubject;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.idToken = idToken;
        this.expiresAtEpochMs = expiresAtEpochMs;
        this.scopes = scopes != null ? Collections.unmodifiableList(new ArrayList<>(scopes)) : Collections.emptyList();
        this.issuer = issuer;
        this.clientId = clientId;
        this.accountDisplayName = accountDisplayName;
        this.planMetadata = planMetadata;
        this.lastRefreshAtEpochMs = lastRefreshAtEpochMs;
        this.lastVerifiedAtEpochMs = lastVerifiedAtEpochMs;
        this.tokenGeneration = tokenGeneration;
    }

    public boolean isExpired() {
        return expiresAtEpochMs > 0 && System.currentTimeMillis() >= expiresAtEpochMs;
    }

    public boolean isExpiringSoon(long thresholdMs) {
        return expiresAtEpochMs > 0 && (System.currentTimeMillis() + thresholdMs) >= expiresAtEpochMs;
    }

    /**
     * Creates a new generation record after a verified token refresh.
     */
    @NonNull
    public CredentialRecord withRotatedTokens(@NonNull String newAccessToken,
                                             @Nullable String newRefreshToken,
                                             long newExpiresAtEpochMs) {
        return new CredentialRecord(
                this.providerId,
                this.accountSubject,
                newAccessToken,
                newRefreshToken != null ? newRefreshToken : this.refreshToken,
                this.idToken,
                newExpiresAtEpochMs,
                this.scopes,
                this.issuer,
                this.clientId,
                this.accountDisplayName,
                this.planMetadata,
                System.currentTimeMillis(),
                this.lastVerifiedAtEpochMs,
                this.tokenGeneration + 1
        );
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("providerId", providerId);
        if (accountSubject != null) obj.put("accountSubject", accountSubject);
        obj.put("accessToken", accessToken);
        if (refreshToken != null) obj.put("refreshToken", refreshToken);
        if (idToken != null) obj.put("idToken", idToken);
        obj.put("expiresAtEpochMs", expiresAtEpochMs);
        JSONArray scArr = new JSONArray();
        for (String s : scopes) scArr.put(s);
        obj.put("scopes", scArr);
        if (issuer != null) obj.put("issuer", issuer);
        if (clientId != null) obj.put("clientId", clientId);
        if (accountDisplayName != null) obj.put("accountDisplayName", accountDisplayName);
        if (planMetadata != null) obj.put("planMetadata", planMetadata);
        obj.put("lastRefreshAtEpochMs", lastRefreshAtEpochMs);
        obj.put("lastVerifiedAtEpochMs", lastVerifiedAtEpochMs);
        obj.put("tokenGeneration", tokenGeneration);
        return obj;
    }

    @NonNull
    public static CredentialRecord fromJson(@NonNull JSONObject obj) {
        String providerId = obj.optString("providerId", "");
        String accountSubject = obj.has("accountSubject") ? obj.optString("accountSubject") : null;
        String accessToken = obj.optString("accessToken", "");
        String refreshToken = obj.has("refreshToken") ? obj.optString("refreshToken") : null;
        String idToken = obj.has("idToken") ? obj.optString("idToken") : null;
        long expiresAtEpochMs = obj.optLong("expiresAtEpochMs", 0L);

        List<String> scopes = new ArrayList<>();
        JSONArray scArr = obj.optJSONArray("scopes");
        if (scArr != null) {
            for (int i = 0; i < scArr.length(); i++) {
                scopes.add(scArr.optString(i));
            }
        }

        String issuer = obj.has("issuer") ? obj.optString("issuer") : null;
        String clientId = obj.has("clientId") ? obj.optString("clientId") : null;
        String accountDisplayName = obj.has("accountDisplayName") ? obj.optString("accountDisplayName") : null;
        String planMetadata = obj.has("planMetadata") ? obj.optString("planMetadata") : null;
        long lastRefreshAtEpochMs = obj.optLong("lastRefreshAtEpochMs", 0L);
        long lastVerifiedAtEpochMs = obj.optLong("lastVerifiedAtEpochMs", 0L);
        long tokenGeneration = obj.optLong("tokenGeneration", 1L);

        return new CredentialRecord(
                providerId,
                accountSubject,
                accessToken,
                refreshToken,
                idToken,
                expiresAtEpochMs,
                scopes,
                issuer,
                clientId,
                accountDisplayName,
                planMetadata,
                lastRefreshAtEpochMs,
                lastVerifiedAtEpochMs,
                tokenGeneration
        );
    }
}
