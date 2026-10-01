package studio.ocean.app.providers.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Represents an active, user-configured account, official CLI session, or API key connection.
 * Supports multiple accounts per provider.
 */
public final class ProviderConnection {
    public final String id;
    public final String providerId;
    public final String displayAccount;
    public final AuthStrategy strategy;
    public final ConnectionStatus status;
    public final String baseUrl;
    public final String selectedModel;
    public final String credentialRef; // Key in CredentialVault (null for provider-owned CLI session)
    public final String cliSessionRef;
    public final List<String> scopes;
    public final Long expiresAtEpochMs;
    public final QuotaSnapshot quota;
    public final List<ModelDescriptor> models;
    public final long lastValidatedAtEpochMs;

    public ProviderConnection(String id, String providerId, String displayAccount,
                              AuthStrategy strategy, ConnectionStatus status,
                              String baseUrl, String selectedModel,
                              String credentialRef, String cliSessionRef,
                              List<String> scopes, Long expiresAtEpochMs,
                              QuotaSnapshot quota, List<ModelDescriptor> models,
                              long lastValidatedAtEpochMs) {
        this.id = id != null ? id : UUID.randomUUID().toString();
        this.providerId = providerId;
        this.displayAccount = displayAccount != null ? displayAccount : "";
        this.strategy = strategy;
        this.status = status != null ? status : ConnectionStatus.CONNECTED;
        this.baseUrl = baseUrl != null ? baseUrl : "";
        this.selectedModel = selectedModel != null ? selectedModel : "";
        this.credentialRef = credentialRef;
        this.cliSessionRef = cliSessionRef;
        this.scopes = scopes != null ? Collections.unmodifiableList(scopes) : Collections.emptyList();
        this.expiresAtEpochMs = expiresAtEpochMs;
        this.quota = quota != null ? quota : QuotaSnapshot.unknown("Standard", "initial");
        this.models = models != null ? Collections.unmodifiableList(models) : Collections.emptyList();
        this.lastValidatedAtEpochMs = lastValidatedAtEpochMs;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("providerId", providerId);
        o.put("displayAccount", displayAccount);
        o.put("strategy", strategy.name());
        o.put("status", status.name());
        o.put("baseUrl", baseUrl);
        o.put("selectedModel", selectedModel);
        if (credentialRef != null) o.put("credentialRef", credentialRef);
        if (cliSessionRef != null) o.put("cliSessionRef", cliSessionRef);
        JSONArray scArr = new JSONArray();
        for (String s : scopes) scArr.put(s);
        o.put("scopes", scArr);
        if (expiresAtEpochMs != null) o.put("expiresAt", expiresAtEpochMs);
        if (quota != null) o.put("quota", quota.toJson());
        JSONArray mArr = new JSONArray();
        for (ModelDescriptor m : models) mArr.put(m.toJson());
        o.put("models", mArr);
        o.put("lastValidatedAt", lastValidatedAtEpochMs);
        return o;
    }

    public static ProviderConnection fromJson(JSONObject o) {
        if (o == null) return null;
        String id = o.optString("id", UUID.randomUUID().toString());
        String providerId = o.optString("providerId");
        String displayAccount = o.optString("displayAccount", "");
        AuthStrategy strategy = AuthStrategy.valueOf(o.optString("strategy", AuthStrategy.API_KEY.name()));
        ConnectionStatus status = ConnectionStatus.valueOf(o.optString("status", ConnectionStatus.CONNECTED.name()));
        String baseUrl = o.optString("baseUrl", "");
        String selectedModel = o.optString("selectedModel", "");
        String credentialRef = o.has("credentialRef") && !o.isNull("credentialRef") ? o.optString("credentialRef") : null;
        String cliSessionRef = o.has("cliSessionRef") && !o.isNull("cliSessionRef") ? o.optString("cliSessionRef") : null;
        List<String> scopes = new ArrayList<>();
        JSONArray scArr = o.optJSONArray("scopes");
        if (scArr != null) {
            for (int i = 0; i < scArr.length(); i++) scopes.add(scArr.optString(i));
        }
        Long expiresAt = o.has("expiresAt") && !o.isNull("expiresAt") ? o.optLong("expiresAt") : null;
        QuotaSnapshot quota = o.has("quota") ? QuotaSnapshot.fromJson(o.optJSONObject("quota")) : null;
        List<ModelDescriptor> models = new ArrayList<>();
        JSONArray mArr = o.optJSONArray("models");
        if (mArr != null) {
            for (int i = 0; i < mArr.length(); i++) {
                ModelDescriptor md = ModelDescriptor.fromJson(mArr.optJSONObject(i));
                if (md != null) models.add(md);
            }
        }
        long lastValidatedAt = o.optLong("lastValidatedAt", System.currentTimeMillis());
        return new ProviderConnection(id, providerId, displayAccount, strategy, status,
                baseUrl, selectedModel, credentialRef, cliSessionRef, scopes, expiresAt, quota, models, lastValidatedAt);
    }

    public ProviderConnection withQuota(QuotaSnapshot newQuota) {
        return new ProviderConnection(id, providerId, displayAccount, strategy, status,
                baseUrl, selectedModel, credentialRef, cliSessionRef, scopes, expiresAtEpochMs,
                newQuota, models, System.currentTimeMillis());
    }
}
