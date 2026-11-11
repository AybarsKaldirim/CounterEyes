package com.countereyes.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Simpra `GET /checks` kaynağı: yalnız okuma, sayfalı. */
final class SimpraSource implements PosSource {
    static final String DEFAULT_BASE = "https://api.simpra.app/checks";
    private static final int MAX_PAGES = 25;
    private final String base;
    private final String token;
    private final boolean includeZero;

    SimpraSource(String base, String token, boolean includeZero) {
        this.base = base == null || base.trim().isEmpty() ? DEFAULT_BASE : base.trim();
        this.token = token == null ? "" : token.trim();
        this.includeZero = includeZero;
    }

    @Override public String label() {
        return "Simpra";
    }

    @Override public List<Closure> fetch(long since, long until) throws Exception {
        List<Closure> out = new ArrayList<>();
        for (int page = 1; page <= MAX_PAGES; page++) {
            JSONObject response = page(page, since, until);
            JSONArray checks = response.optJSONArray("checks");
            if (checks != null) {
                for (int i = 0; i < checks.length(); i++) {
                    JSONObject check = checks.optJSONObject(i);
                    if (check == null) continue;
                    String id = check.optString("id", "");
                    long closed = Json.epochSeconds(Json.at(check, "closed_at"));
                    if (id.isEmpty() || closed <= 0) continue;
                    JSONObject summary = check.optJSONObject("summary");
                    double total = summary == null ? 0 : summary.optDouble("total_amount", 0);
                    double paid = summary == null ? 0 : summary.optDouble("paid_amount", 0);
                    if (!includeZero && total <= 0 && paid <= 0) continue;
                    out.add(new Closure(id, closed, total));
                }
            }
            JSONObject meta = response.optJSONObject("meta");
            if (meta == null || !meta.optBoolean("has_next_page")) return out;
        }
        throw new IOException("Simpra sayfa sınırı aşıldı");
    }

    private JSONObject page(int page, long since, long until) throws Exception {
        String query = base + (base.contains("?") ? "&" : "?") +
            "status=closed&sort_by=desc&per_page=100&page=" + page +
            "&closed_at_since=" + since + "&closed_at_until=" + until;
        HttpURLConnection connection = (HttpURLConnection) new URL(query).openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setUseCaches(false);
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(9000);
            connection.setRequestProperty("Simpra-Access-Token", token);
            connection.setRequestProperty("Accept", "application/json");
            int code = connection.getResponseCode();
            if (code != 200) throw new IOException("Simpra HTTP " + code);
            try (InputStream input = connection.getInputStream()) {
                return new JSONObject(new String(read(input, 8 * 1024 * 1024), StandardCharsets.UTF_8));
            }
        } finally {
            connection.disconnect();
        }
    }

    static byte[] read(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (result.size() + count > limit) throw new IOException("Veri sınırı");
            result.write(buffer, 0, count);
        }
        return result.toByteArray();
    }
}
