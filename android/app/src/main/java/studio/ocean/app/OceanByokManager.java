package studio.ocean.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Persists BYOK configuration without storing provider secrets as plaintext. */
public final class OceanByokManager {
    public static final String PREFS_NAME="ocean_byok_prefs";
    public static final String PROVIDER_GOOGLE="google", PROVIDER_ANTHROPIC="anthropic",
            PROVIDER_OPENAI="openai", PROVIDER_CUSTOM="custom";
    private static final String PROVIDER="provider", MODEL="model", API_KEY="api_key_encrypted",
            BASE_URL="base_url", VERIFIED="verified_configuration", KEY_ALIAS="ocean_byok_aes_v1";
    private final SharedPreferences prefs;

    public OceanByokManager(Context context) { prefs=context.getSharedPreferences(PREFS_NAME,Context.MODE_PRIVATE); }
    public String getProvider(){return prefs.getString(PROVIDER,PROVIDER_GOOGLE);}
    public String getModel(){return prefs.getString(MODEL,"");}
    public String getBaseUrl(){
        String saved=prefs.getString(BASE_URL,""); if(!saved.isEmpty())return saved;
        if(PROVIDER_GOOGLE.equals(getProvider()))return "https://generativelanguage.googleapis.com";
        if(PROVIDER_ANTHROPIC.equals(getProvider()))return "https://api.anthropic.com/v1";
        if(PROVIDER_OPENAI.equals(getProvider()))return "https://api.openai.com/v1";
        return "";
    }
    public String getApiKey(){
        String value=prefs.getString(API_KEY,""); if(value.isEmpty())return "";
        try{return decrypt(value);}catch(Exception error){return "";}
    }
    public void saveConfig(String provider,String model,String apiKey,String baseUrl) throws Exception {
        if(provider==null||model==null||model.trim().isEmpty()||apiKey==null||apiKey.trim().isEmpty())
            throw new IllegalArgumentException("Provider, model, and API key are required");
        if(baseUrl==null||!baseUrl.startsWith("https://"))throw new IllegalArgumentException("An HTTPS endpoint is required");
        prefs.edit().putString(PROVIDER,provider).putString(MODEL,model.trim())
                .putString(API_KEY,encrypt(apiKey.trim())).putString(BASE_URL,baseUrl.replaceAll("/+$",""))
                .remove(VERIFIED).commit();
    }
    public boolean hasApiKey(){return !getApiKey().isEmpty();}
    public void markVerified(){prefs.edit().putString(VERIFIED,configurationDigest()).apply();}
    public boolean isVerified(){String current=prefs.getString(VERIFIED,"");return !current.isEmpty()&&current.equals(configurationDigest());}

    private String configurationDigest(){
        try{byte[] digest=MessageDigest.getInstance("SHA-256").digest((getProvider()+"\n"+getModel()+"\n"+getBaseUrl()+"\n"+getApiKey()).getBytes(StandardCharsets.UTF_8));return Base64.encodeToString(digest,Base64.NO_WRAP);}
        catch(Exception error){return "";}
    }
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(KEY_ALIAS))return ((KeyStore.SecretKeyEntry)store.getEntry(KEY_ALIAS,null)).getSecretKey();
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    private String encrypt(String plaintext)throws Exception{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());byte[] encrypted=cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));byte[] out=new byte[cipher.getIV().length+encrypted.length];System.arraycopy(cipher.getIV(),0,out,0,cipher.getIV().length);System.arraycopy(encrypted,0,out,cipher.getIV().length,encrypted.length);return Base64.encodeToString(out,Base64.NO_WRAP);}
    private String decrypt(String encoded)throws Exception{byte[] all=Base64.decode(encoded,Base64.NO_WRAP);if(all.length<13)throw new IllegalArgumentException("Invalid encrypted key");byte[] iv=new byte[12],data=new byte[all.length-12];System.arraycopy(all,0,iv,0,12);System.arraycopy(all,12,data,0,data.length);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));return new String(cipher.doFinal(data),StandardCharsets.UTF_8);}
}
