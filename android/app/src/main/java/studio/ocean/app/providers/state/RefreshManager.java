package studio.ocean.app.providers.state;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import studio.ocean.app.providers.auth.DirectAuthAdapter;
import studio.ocean.app.providers.model.ProviderDescriptor;

/**
 * Manages atomic credential rotation and proactive token refresh (Directive 2026-10-02 §11, §13).
 * Strictly guarantees that working refresh tokens are NEVER overwritten before replacement is verified.
 */
public final class RefreshManager {

    private static final String TAG = "RefreshManager";
    private static final long DEFAULT_REFRESH_THRESHOLD_MS = 5 * 60 * 1000L; // 5 minutes

    private final Context context;
    private final CredentialVault vault;

    public RefreshManager(@NonNull Context context, @NonNull CredentialVault vault) {
        this.context = context.getApplicationContext();
        this.vault = vault;
    }

    public RefreshManager(@NonNull Context context) {
        this(context, new CredentialVault(context));
    }

    /**
     * Refreshes a provider's credential if expiring soon or expired, using atomic rotation.
     * Returns true if refresh succeeded or was not needed; false if refresh failed and reauth is required.
     */
    public synchronized boolean refreshIfNeeded(@NonNull String providerId,
                                                @NonNull DirectAuthAdapter adapter) {
        CredentialRecord record = vault.retrieveRecord(providerId);
        if (record == null) {
            return false;
        }

        if (!record.isExpiringSoon(DEFAULT_REFRESH_THRESHOLD_MS)) {
            return true; // Still fresh
        }

        if (record.refreshToken == null || record.refreshToken.isEmpty()) {
            Log.w(TAG, "Cannot refresh " + providerId + ": no refresh token in credential record");
            return false;
        }

        Log.i(TAG, "Proactively refreshing expiring token for provider: " + providerId + " (generation " + record.tokenGeneration + ")");

        try {
            DirectAuthAdapter.AuthResult result = adapter.refresh(record.refreshToken);
            if (result == null || !result.isSuccess || result.accessToken == null || result.accessToken.isEmpty()) {
                Log.e(TAG, "Refresh failed for provider " + providerId + ": " + (result != null ? result.error : "null result"));
                return false;
            }

            long newExpiry = result.expiresAtEpochMs != null ? result.expiresAtEpochMs : (System.currentTimeMillis() + 3600000L);
            String newRefreshToken = result.refreshToken != null && !result.refreshToken.isEmpty() ? result.refreshToken : record.refreshToken;

            CredentialRecord rotated = record.withRotatedTokens(result.accessToken, newRefreshToken, newExpiry);

            boolean rotatedOk = vault.rotateRecord(rotated);
            if (rotatedOk) {
                Log.i(TAG, "Successfully rotated credentials for " + providerId + " to generation " + rotated.tokenGeneration);
                return true;
            } else {
                Log.e(TAG, "Failed to commit rotated credentials to secure vault for " + providerId);
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception during token refresh for " + providerId, e);
            return false;
        }
    }
}
