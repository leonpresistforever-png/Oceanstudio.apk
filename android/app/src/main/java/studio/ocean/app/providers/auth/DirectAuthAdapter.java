package studio.ocean.app.providers.auth;

import android.content.Context;
import android.net.Uri;
import java.util.List;
import studio.ocean.app.providers.model.ModelDescriptor;
import studio.ocean.app.providers.model.QuotaSnapshot;

/**
 * Standard contract for Direct Connect authentication and account inspection (Directive 3 §5.2).
 */
public interface DirectAuthAdapter {

    final class Availability {
        public final boolean isAvailable;
        public final String reasonUnavailable;
        public final boolean requiresRiskWarning;
        public final String riskWarningTitle;
        public final String riskWarningMessage;

        public Availability(boolean isAvailable, String reasonUnavailable, boolean requiresRiskWarning,
                            String riskWarningTitle, String riskWarningMessage) {
            this.isAvailable = isAvailable;
            this.reasonUnavailable = reasonUnavailable;
            this.requiresRiskWarning = requiresRiskWarning;
            this.riskWarningTitle = riskWarningTitle;
            this.riskWarningMessage = riskWarningMessage;
        }

        public static Availability available() {
            return new Availability(true, null, false, null, null);
        }

        public static Availability withRiskWarning(String title, String message) {
            return new Availability(true, null, true, title, message);
        }

        public static Availability unavailable(String reason) {
            return new Availability(false, reason, false, null, null);
        }
    }

    final class AuthRequest {
        public final String transactionId;
        public final String state;
        public final String codeVerifier;
        public final String codeChallenge;
        public final String redirectUri;
        public final List<String> scopes;

        public AuthRequest(String transactionId, String state, String codeVerifier,
                           String codeChallenge, String redirectUri, List<String> scopes) {
            this.transactionId = transactionId;
            this.state = state;
            this.codeVerifier = codeVerifier;
            this.codeChallenge = codeChallenge;
            this.redirectUri = redirectUri;
            this.scopes = scopes;
        }
    }

    final class AuthStartResult {
        public final boolean isDeviceCode;
        public final String authorizationUrl;
        public final String userCode;
        public final String verificationUri;
        public final int pollIntervalSeconds;
        public final int expiresInSeconds;

        private AuthStartResult(boolean isDeviceCode, String authorizationUrl, String userCode,
                                String verificationUri, int pollIntervalSeconds, int expiresInSeconds) {
            this.isDeviceCode = isDeviceCode;
            this.authorizationUrl = authorizationUrl;
            this.userCode = userCode;
            this.verificationUri = verificationUri;
            this.pollIntervalSeconds = pollIntervalSeconds;
            this.expiresInSeconds = expiresInSeconds;
        }

        public static AuthStartResult browser(String authorizationUrl) {
            return new AuthStartResult(false, authorizationUrl, null, null, 0, 0);
        }

        public static AuthStartResult deviceCode(String userCode, String verificationUri,
                                                 int interval, int expiresIn) {
            return new AuthStartResult(true, null, userCode, verificationUri, interval, expiresIn);
        }
    }

    final class AuthResult {
        public final boolean isSuccess;
        public final String accessToken;
        public final String refreshToken;
        public final Long expiresAtEpochMs;
        public final String accountId;
        public final String displayName;
        public final String planTier;
        public final String error;

        public AuthResult(boolean isSuccess, String accessToken, String refreshToken,
                          Long expiresAtEpochMs, String accountId, String displayName,
                          String planTier, String error) {
            this.isSuccess = isSuccess;
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
            this.expiresAtEpochMs = expiresAtEpochMs;
            this.accountId = accountId;
            this.displayName = displayName;
            this.planTier = planTier;
            this.error = error;
        }

        public static AuthResult success(String accessToken, String refreshToken,
                                         Long expiresAtEpochMs, String accountId,
                                         String displayName, String planTier) {
            return new AuthResult(true, accessToken, refreshToken, expiresAtEpochMs,
                    accountId, displayName, planTier, null);
        }

        public static AuthResult failure(String error) {
            return new AuthResult(false, null, null, null, null, null, null, error);
        }
    }

    Availability preflight(Context context);
    AuthStartResult start(AuthRequest request) throws Exception;
    AuthResult handleCallback(Uri callback, AuthRequest originalRequest) throws Exception;
    AuthResult pollDeviceCode(String transactionId) throws Exception;
    AuthResult refresh(String refreshToken) throws Exception;
    List<ModelDescriptor> discoverModels(String accessToken) throws Exception;
    QuotaSnapshot fetchQuota(String accessToken) throws Exception;
    boolean probe(String accessToken, String model) throws Exception;
    void logout(String accessToken);
}
