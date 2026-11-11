package com.countereyes.app;

import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Sırları (kasa anahtarı, başlık değeri) Android Keystore ile şifreleyerek saklar. */
final class TokenStore {
    private static final String ALIAS = "counter-eyes-secret";
    private static final String FIELD = "secret_blob";
    private final SharedPreferences prefs;

    TokenStore(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    void save(String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key(true));
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        String stored = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + "." +
            Base64.encodeToString(encrypted, Base64.NO_WRAP);
        if (!prefs.edit().putString(FIELD, stored).commit()) {
            throw new IllegalStateException("Sır kaydedilemedi");
        }
    }

    String load() throws Exception {
        String stored = prefs.getString(FIELD, "");
        if (stored == null || stored.isEmpty()) return "";
        String[] parts = stored.split("\\.", 2);
        if (parts.length != 2) throw new IllegalStateException("Sır okunamadı");
        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(128, iv));
        byte[] decoded = cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP));
        try {
            return new String(decoded, StandardCharsets.UTF_8);
        } finally {
            Arrays.fill(decoded, (byte) 0);
        }
    }

    private SecretKey key(boolean create) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        SecretKey existing = (SecretKey) store.getKey(ALIAS, null);
        if (existing != null) return existing;
        if (!create) throw new IllegalStateException("Anahtar bulunamadı");
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
            KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .build());
        return generator.generateKey();
    }
}
