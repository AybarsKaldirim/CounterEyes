package com.countereyes.app;

import android.content.SharedPreferences;

import org.json.JSONObject;

/** Kasa bağlantısı ayarları. Sırlar (token/başlık değeri) Keystore ile şifreli saklanır. */
final class PosConfig {
    static final String SIMPRA = "simpra";
    static final String HTTP = "http";
    static final String DEMO = "demo";

    private final SharedPreferences prefs;
    private final TokenStore secrets;

    PosConfig(SharedPreferences prefs) {
        this.prefs = prefs;
        this.secrets = new TokenStore(prefs);
    }

    boolean configured() {
        return prefs.getBoolean("configured", false);
    }

    String provider() {
        String value = prefs.getString("provider", SIMPRA);
        return value == null ? SIMPRA : value;
    }

    String url() {
        return prefs.getString("url", "");
    }

    String reviewUrl() {
        return prefs.getString("reviewUrl", "");
    }

    String headerName() {
        return prefs.getString("headerName", "");
    }

    String itemsPath() {
        return prefs.getString("itemsPath", "");
    }

    String idField() {
        return prefs.getString("idField", "id");
    }

    String closedAtField() {
        return prefs.getString("closedAtField", "closed_at");
    }

    String amountField() {
        return prefs.getString("amountField", "");
    }

    boolean includeZero() {
        return prefs.getBoolean("includeZero", true);
    }

    int pollMs() {
        return Math.max(2000, Math.min(60000, prefs.getInt("pollMs", 5000)));
    }

    /** Arayüze gidecek ayarlar — sır içermez. */
    JSONObject publicJson() {
        JSONObject out = new JSONObject();
        try {
            out.put("provider", provider());
            out.put("url", url());
            out.put("headerName", headerName());
            out.put("itemsPath", itemsPath());
            out.put("idField", idField());
            out.put("closedAtField", closedAtField());
            out.put("amountField", amountField());
            out.put("reviewUrl", reviewUrl());
            out.put("includeZero", includeZero());
            out.put("pollMs", pollMs());
            out.put("configured", configured());
            out.put("tokenSaved", !secret("token").isEmpty());
            out.put("headerSaved", !secret("headerValue").isEmpty());
        } catch (Exception ignored) {
            // JSONObject.put yalnızca anahtar hatasında atar.
        }
        return out;
    }

    /** Kaydedilmiş sır (Keystore). Yoksa boş metin. */
    String secret(String key) {
        try {
            JSONObject saved = new JSONObject(secrets.load().isEmpty() ? "{}" : secrets.load());
            return saved.optString(key, "");
        } catch (Exception error) {
            return "";
        }
    }

    void save(JSONObject in) throws Exception {
        String provider = in.optString("provider", SIMPRA);
        if (!SIMPRA.equals(provider) && !HTTP.equals(provider) && !DEMO.equals(provider)) {
            throw new IllegalArgumentException("Bilinmeyen kasa türü");
        }
        String url = in.optString("url", "").trim();
        String reviewUrl = in.optString("reviewUrl", "").trim();
        if (!DEMO.equals(provider) && url.isEmpty()) throw new IllegalArgumentException("Kasa adresi gerekli");
        if (!url.isEmpty() && !(url.startsWith("http://") || url.startsWith("https://"))) {
            throw new IllegalArgumentException("Adres http:// veya https:// ile başlamalı");
        }
        if (!reviewUrl.isEmpty() && !(reviewUrl.startsWith("http://") || reviewUrl.startsWith("https://"))) {
            throw new IllegalArgumentException("Yorum bağlantısı http(s):// olmalı");
        }
        if (url.length() > 2048 || reviewUrl.length() > 2048) throw new IllegalArgumentException("Adres çok uzun");

        JSONObject blob = new JSONObject();
        String token = in.optString("token", "").trim();
        String headerValue = in.optString("headerValue", "").trim();
        if (token.isEmpty()) token = secret("token");
        if (headerValue.isEmpty()) headerValue = secret("headerValue");
        if (token.length() > 4096 || headerValue.length() > 4096) throw new IllegalArgumentException("Sır çok uzun");
        blob.put("token", token);
        blob.put("headerValue", headerValue);
        secrets.save(blob.toString());

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("provider", provider);
        editor.putString("url", url);
        editor.putString("headerName", in.optString("headerName", "").trim());
        editor.putString("itemsPath", in.optString("itemsPath", "").trim());
        editor.putString("idField", in.optString("idField", "id").trim());
        editor.putString("closedAtField", in.optString("closedAtField", "closed_at").trim());
        editor.putString("amountField", in.optString("amountField", "").trim());
        editor.putString("reviewUrl", reviewUrl);
        editor.putBoolean("includeZero", in.optBoolean("includeZero", true));
        editor.putInt("pollMs", Math.max(2000, Math.min(60000, in.optInt("pollMs", 5000))));
        editor.putBoolean("configured", true);
        if (!editor.commit()) throw new IllegalStateException("Ayarlar kaydedilemedi");
    }

    boolean ready() {
        if (DEMO.equals(provider())) return true;
        if (!configured()) return false;
        if (url().isEmpty()) return false;
        if (SIMPRA.equals(provider())) return !secret("token").isEmpty();
        return true;
    }

    /** Ayar değişti mi? (yoklamayı yeniden başlatmak için) */
    String signature() {
        return provider() + "|" + url() + "|" + headerName() + "|" + itemsPath() + "|" +
            idField() + "|" + closedAtField() + "|" + amountField() + "|" + includeZero() + "|" +
            pollMs() + "|" + secret("token").length() + "|" + secret("headerValue").length();
    }

    PosSource source() {
        return build(provider(), url(), headerName(), secret("headerValue"), itemsPath(),
            idField(), closedAtField(), amountField(), includeZero(), secret("token"));
    }

    /** Ayar ekranındaki "Bağlantıyı dene" için: kaydetmeden dener. */
    static PosSource preview(JSONObject in, String storedToken, String storedHeader) {
        String token = in.optString("token", "").trim();
        String header = in.optString("headerValue", "").trim();
        return build(in.optString("provider", SIMPRA), in.optString("url", "").trim(),
            in.optString("headerName", "").trim(), header.isEmpty() ? storedHeader : header,
            in.optString("itemsPath", "").trim(), in.optString("idField", "id").trim(),
            in.optString("closedAtField", "closed_at").trim(), in.optString("amountField", "").trim(),
            in.optBoolean("includeZero", true), token.isEmpty() ? storedToken : token);
    }

    private static PosSource build(String provider, String url, String headerName, String headerValue,
                                   String itemsPath, String idField, String closedAtField,
                                   String amountField, boolean includeZero, String token) {
        if (DEMO.equals(provider)) return null;
        if (SIMPRA.equals(provider)) return new SimpraSource(url, token, includeZero);
        return new HttpJsonSource(url, headerName, headerValue, itemsPath, idField, closedAtField,
            amountField, includeZero);
    }
}
