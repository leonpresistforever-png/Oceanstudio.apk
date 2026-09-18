package studio.ocean.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.Locale;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Private, non-backed-up signing identity used only by the user-facing Ocean Forge signer. */
public final class OceanForgeSigningStore {
    private static final String PREFS="ocean_forge_signing";
    private static final String AES_ALIAS="ocean_forge_signing_aes_v1";
    private static final String KEY_ALIAS="key_alias", STORE_PASS="store_pass", KEY_PASS="key_pass", FILE_SHA="file_sha256";
    private static final long MAX_KEYSTORE_BYTES=16L*1024L*1024L;

    private final Context context;
    private final SharedPreferences prefs;

    public OceanForgeSigningStore(Context context){
        this.context=context.getApplicationContext();
        this.prefs=this.context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }

    public boolean isConfigured(){
        File file=keystoreFile();
        return file.isFile()&&file.length()>0&&!alias().isEmpty()&&!storePassword().isEmpty()&&!keyPassword().isEmpty();
    }

    public File keystoreFile(){
        return new File(new File(context.getNoBackupFilesDir(),"ocean-forge"),"signing.keystore");
    }

    public String alias(){return prefs.getString(KEY_ALIAS,"");}
    public String sha256(){return prefs.getString(FILE_SHA,"");}

    public String storePassword(){
        try{return decrypt(prefs.getString(STORE_PASS,""));}catch(Exception error){return "";}
    }
    public String keyPassword(){
        try{return decrypt(prefs.getString(KEY_PASS,""));}catch(Exception error){return "";}
    }

    public synchronized void save(File source,String alias,String storePassword,String keyPassword)throws Exception{
        if(source==null||!source.isFile()||!source.canRead())throw new IOException("Signing keystore is not readable");
        if(source.length()<=0||source.length()>MAX_KEYSTORE_BYTES)throw new IOException("Signing keystore size is invalid");
        if(alias==null||alias.trim().isEmpty()||alias.length()>128)throw new IllegalArgumentException("Signing alias is invalid");
        if(storePassword==null||storePassword.isEmpty()||keyPassword==null||keyPassword.isEmpty())throw new IllegalArgumentException("Signing passwords are required");

        File target=keystoreFile(), dir=target.getParentFile();
        if(dir==null||(!dir.isDirectory()&&!dir.mkdirs()))throw new IOException("Could not create private Forge signing directory");
        File temp=new File(dir,"signing.keystore.tmp");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(source);FileOutputStream out=new FileOutputStream(temp,false)){
            byte[] buffer=new byte[32768];int n;long total=0;
            while((n=in.read(buffer))!=-1){
                total+=n;if(total>MAX_KEYSTORE_BYTES)throw new IOException("Signing keystore is too large");
                digest.update(buffer,0,n);out.write(buffer,0,n);
            }
            out.flush();out.getFD().sync();
        }
        if(target.exists()&&!target.delete())throw new IOException("Could not replace private Forge signing keystore");
        if(!temp.renameTo(target))throw new IOException("Could not activate private Forge signing keystore");
        target.setReadable(false,false);target.setWritable(false,false);
        target.setReadable(true,true);target.setWritable(true,true);

        prefs.edit()
                .putString(KEY_ALIAS,alias.trim())
                .putString(STORE_PASS,encrypt(storePassword))
                .putString(KEY_PASS,encrypt(keyPassword))
                .putString(FILE_SHA,hex(digest.digest()))
                .commit();
    }

    public synchronized void clear(){
        File file=keystoreFile();if(file.exists())file.delete();
        prefs.edit().clear().commit();
        try{
            KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
            if(store.containsAlias(AES_ALIAS))store.deleteEntry(AES_ALIAS);
        }catch(Exception ignored){}
    }

    private SecretKey key()throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(AES_ALIAS))return ((KeyStore.SecretKeyEntry)store.getEntry(AES_ALIAS,null)).getSecretKey();
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(AES_ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }

    private String encrypt(String value)throws Exception{
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        byte[] data=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] out=new byte[cipher.getIV().length+data.length];
        System.arraycopy(cipher.getIV(),0,out,0,cipher.getIV().length);
        System.arraycopy(data,0,out,cipher.getIV().length,data.length);
        return Base64.encodeToString(out,Base64.NO_WRAP);
    }

    private String decrypt(String encoded)throws Exception{
        if(encoded==null||encoded.isEmpty())return "";
        byte[] all=Base64.decode(encoded,Base64.NO_WRAP);
        if(all.length<13)throw new IllegalArgumentException("Invalid encrypted signing secret");
        byte[] iv=new byte[12],data=new byte[all.length-12];
        System.arraycopy(all,0,iv,0,12);System.arraycopy(all,12,data,0,data.length);
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,iv));
        return new String(cipher.doFinal(data),StandardCharsets.UTF_8);
    }

    private static String hex(byte[] bytes){
        StringBuilder out=new StringBuilder();
        for(byte b:bytes)out.append(String.format(Locale.ROOT,"%02x",b&255));
        return out.toString();
    }
}
