package com.countereyes.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Küçük yardımcılar: ayarlanabilir alan yolu ve zaman çözümleme. */
final class Json {
    private Json() {}

    static Object at(Object root, String path) {
        if (path == null) return null;
        String clean = path.trim();
        if (clean.isEmpty()) return root;
        Object current = root;
        for (String part : clean.split("\\.")) {
            if (part.isEmpty()) continue;
            if (current instanceof JSONObject) current = ((JSONObject) current).opt(part);
            else return null;
        }
        return current;
    }

    static JSONArray arrayAt(Object root, String path) {
        Object value = at(root, path);
        return value instanceof JSONArray ? (JSONArray) value : null;
    }

    /** Saniye veya milisaniye sayısı ya da ISO-8601 metni; başarısızsa 0. */
    static long epochSeconds(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) {
            double raw = ((Number) value).doubleValue();
            if (raw <= 0) return 0;
            return raw > 1.0e11 ? Math.round(raw / 1000.0) : Math.round(raw);
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return 0;
        try {
            long raw = Long.parseLong(text);
            return raw > 100000000000L ? raw / 1000 : raw;
        } catch (NumberFormatException ignored) {
            // ISO metni olabilir.
        }
        String[] patterns = {
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                if (pattern.endsWith("'Z'")) format.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = format.parse(text);
                if (date != null) return date.getTime() / 1000L;
            } catch (ParseException ignored) {
                // Sıradaki kalıbı dene.
            }
        }
        return 0;
    }

    static boolean isArrayBody(String body) {
        String trimmed = body.trim();
        return trimmed.startsWith("[");
    }
}
