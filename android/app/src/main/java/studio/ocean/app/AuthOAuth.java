package studio.ocean.app;

import android.content.Intent;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.OAuthProvider;

/** Google and GitHub sign-in through Firebase when the project API key is available. */
final class AuthOAuth {
    interface Listener {
        void onSuccess(String idToken, String email);
        default void onFailure(String message) { onFailure(null, message, null); }
        void onFailure(String title, String message, String details);
    }

    private final AppCompatActivity activity;
    private final AuthClient authClient;
    private final GoogleSignInClient googleClient;
    private final ActivityResultLauncher<Intent> googleLauncher;
    private Listener listener;

    AuthOAuth(AppCompatActivity activity, AuthClient authClient) {
        this.activity = activity;
        this.authClient = authClient;
        GoogleSignInOptions options = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(activity.getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleClient = GoogleSignIn.getClient(activity, options);
        googleLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), this::handleGoogleResult);
    }

    static boolean available(android.content.Context context) {
        return !AuthClient.resolveApiKey(context).isEmpty();
    }

    void resumePending(Listener listener) {
        this.listener = listener;
        Task<AuthResult> pending = FirebaseAuth.getInstance().getPendingAuthResult();
        if (pending == null) return;
        setAuthBusy(true);
        pending.addOnSuccessListener(result -> finishFirebase(result, listener))
                .addOnFailureListener(error -> fail(listener, AuthClient.formatError(error)));
    }

    void signInWithGoogle(Listener listener) {
        this.listener = listener;
        if (!available(activity)) {
            listener.onFailure(activity.getString(R.string.oauth_not_configured, "Google"));
            return;
        }
        setAuthBusy(true);
        googleClient.signOut().addOnCompleteListener(task -> googleLauncher.launch(googleClient.getSignInIntent()));
    }

    void signInWithGithub(Listener listener) {
        this.listener = listener;
        if (!available(activity)) {
            listener.onFailure(activity.getString(R.string.oauth_not_configured, "GitHub"));
            return;
        }
        setAuthBusy(true);
        OAuthProvider provider = OAuthProvider.newBuilder("github.com").build();
        FirebaseAuth auth = FirebaseAuth.getInstance();
        Task<AuthResult> pending = auth.getPendingAuthResult();
        if (pending != null) {
            pending.addOnSuccessListener(result -> finishFirebase(result, listener))
                    .addOnFailureListener(error -> fail(listener, AuthClient.formatError(error)));
            return;
        }
        auth.startActivityForSignInWithProvider(activity, provider)
                .addOnSuccessListener(result -> finishFirebase(result, listener))
                .addOnFailureListener(error -> {
                    String message = AuthClient.formatError(error);
                    if (message.toLowerCase(java.util.Locale.ROOT).contains("account exists")
                            || message.toLowerCase(java.util.Locale.ROOT).contains("provider")) {
                        fail(listener, activity.getString(R.string.oauth_provider_disabled, "GitHub"));
                    } else {
                        fail(listener, message);
                    }
                });
    }

    private void handleGoogleResult(ActivityResult result) {
        Listener target = listener;
        if (target == null) { setAuthBusy(false); return; }
        try {
            GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(result.getData())
                    .getResult(ApiException.class);
            String idToken = account == null ? null : account.getIdToken();
            if (idToken == null || idToken.isEmpty()) {
                fail(target, activity.getString(R.string.oauth_not_configured, "Google"));
                return;
            }
            authClient.signInWithIdp("google.com", idToken, false, wrap(target));
        } catch (ApiException error) {
            int code = error.getStatusCode();
            String title;
            String message;
            if (code == 12501 || code == 16) {
                title = activity.getString(R.string.auth_cancelled_title);
                message = activity.getString(R.string.auth_cancelled_msg);
            } else if (code == 7) {
                title = activity.getString(R.string.auth_network_title);
                message = activity.getString(R.string.auth_network_msg);
            } else if (code == 10) {
                title = activity.getString(R.string.auth_dev_error_title);
                message = activity.getString(R.string.auth_dev_error_msg);
            } else if (code == 12500) {
                title = activity.getString(R.string.auth_failed_title);
                message = activity.getString(R.string.auth_failed_msg);
            } else if (code == 4) {
                title = activity.getString(R.string.auth_no_account_title);
                message = activity.getString(R.string.auth_no_account_msg);
            } else {
                title = activity.getString(R.string.auth_error_title);
                message = activity.getString(R.string.auth_generic_msg);
            }
            try {
                CrashReportStore.record("AuthOAuth.Google", error);
            } catch (Exception ignored) {}
            fail(target, title, message, "ApiException: status=" + code + " " + error.getMessage());
        }
    }

    private AuthClient.Callback wrap(Listener listener) {
        return authResult -> activity.runOnUiThread(() -> {
            setAuthBusy(false);
            if (!authResult.success) fail(listener, activity.getString(R.string.auth_error_title),
                    authResult.message == null ? activity.getString(R.string.request_failed) : authResult.message,
                    null);
            else listener.onSuccess(authResult.token, authResult.email);
        });
    }

    private void finishFirebase(AuthResult result, Listener listener) {
        if (result == null || result.getUser() == null) {
            fail(listener, activity.getString(R.string.request_failed));
            return;
        }
        result.getUser().getIdToken(false).addOnSuccessListener(tokenResult -> activity.runOnUiThread(() -> {
            setAuthBusy(false);
            String email = result.getUser().getEmail();
            listener.onSuccess(tokenResult.getToken(), email == null ? "" : email);
        })).addOnFailureListener(error -> {
            try {
                CrashReportStore.record("AuthOAuth.Firebase", error);
            } catch (Exception ignored) {}
            fail(listener, activity.getString(R.string.auth_error_title), AuthClient.formatError(error),
                    error != null ? error.getClass().getSimpleName() + ": " + error.getMessage() : null);
        });
    }

    private void fail(Listener listener, String message) {
        fail(listener, activity.getString(R.string.auth_error_title), message, null);
    }

    private void fail(Listener listener, String title, String message, String details) {
        activity.runOnUiThread(() -> {
            setAuthBusy(false);
            if (listener != null) {
                listener.onFailure(
                        title == null ? activity.getString(R.string.auth_error_title) : title,
                        message == null ? activity.getString(R.string.request_failed) : message,
                        details);
            }
        });
    }

    private void setAuthBusy(boolean busy) {
        ViewHelper.setAuthBusy(activity, busy);
    }

    /** Keeps OAuth UI state changes out of MainActivity. */
    static final class ViewHelper {
        private ViewHelper() {}
        static void setAuthBusy(AppCompatActivity activity, boolean busy) {
            android.view.View primary = activity.findViewById(R.id.auth_primary);
            if (primary != null) primary.setEnabled(!busy);
            android.view.View google = activity.findViewById(R.id.google_auth);
            if (google != null) google.setEnabled(!busy);
            android.view.View github = activity.findViewById(R.id.github_auth);
            if (github != null) github.setEnabled(!busy);
        }
    }
}
