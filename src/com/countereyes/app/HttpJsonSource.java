package com.countereyes.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Genel amaçlı kasa kaynağı: GET ile JSON alır, kullanıcının verdiği alan yollarından
 * kapanışları çıkarır. Böylece herhangi bir POS/otomasyon adresi kullanılabilir.
 */
final class HttpJsonSource implements PosSource {
    private final String url;
    private final String headerName;
    private final String headerValue;
    private final String itemsPath;
    private final String idField;
    private final String closedAtField;
    private final String amountField;
    private final boolean includeZero;

    HttpJsonSource(String url, String headerName, String headerValue, String itemsPath,
                   String idField, String closedAtField, String amountField, boolean includeZero) {
        this.url = url == null ? "" : url.trim();
        this.headerName = headerName == null ? "" : headerName.trim();
        this.headerValue = headerValue == null ? "" : headerValue.trim();
        this.itemsPath = itemsPath == null ? "" : itemsPath.trim();
        this.idField = emptyTo(idField, "id");
        this.closedAtField = emptyTo(closedAtField, "closed_at");
        this.amountField = amountField == null ? "" : amountField.trim();
        this.includeZero = includeZero;
    }

    @Override public String label() {
        return "Kasa bağlantısı";
    }

    @Override public List<Closure> fetch(long since, long until) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(9000);
            connection.setRequestProperty("Accept", "application/json");
            if (!headerName.isEmpty() && !headerValue.isEmpty()) {
                connection.setRequestProperty(headerName, headerValue);
            }
            int code = connection.getResponseCode();
            if (code != 200) throw new Exception("Kasa yanıtı: HTTP " + code);
            String body;
            try (InputStream input = connection.getInputStream()) {
                body = new String(SimpraSource.read(input, 4 * 1024 * 1024), StandardCharsets.UTF_8);
            }
            Object root = Json.isArrayBody(body) ? new JSONArray(body) : new JSONObject(body);
            JSONArray items = Json.arrayAt(root, itemsPath);
            if (items == null) throw new Exception("JSON içinde dizi bulunamadı: " + (itemsPath.isEmpty() ? "(kök)" : itemsPath));
            List<Closure> parsed = Closure.parse(items, idField, closedAtField, amountField);
            if (!includeZero) {
                parsed = filterZero(parsed);
            }
            return parsed;
        } finally {
            connection.disconnect();
        }
    }

    private static List<Closure> filterZero(List<Closure> items) {
        java.util.List<Closure> kept = new java.util.ArrayList<>();
        for (Closure item : items) {
            if (item.amount > 0) kept.add(item);
        }
        return kept.isEmpty() ? Collections.<Closure>emptyList() : kept;
    }

    private static String emptyTo(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
