package com.countereyes.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Bir kasa kapanışı (adisyon) olayı ve ortak tazelik/tekrar kuralları. */
final class Closure {
    final String id;
    final long closedAt;
    final double amount;

    Closure(String id, long closedAt, double amount) {
        this.id = id;
        this.closedAt = closedAt;
        this.amount = amount;
    }

    /** Ayarlanan alan yollarıyla JSON dizisini olaylara çevirir. */
    static List<Closure> parse(JSONArray items, String idField, String closedAtField, String amountField) {
        List<Closure> out = new ArrayList<>();
        if (items == null) return out;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString(idField, "");
            if (id.isEmpty()) id = "row-" + i;
            Object amount = amountField.isEmpty() ? null : Json.at(item, amountField);
            out.add(new Closure(id, Json.epochSeconds(Json.at(item, closedAtField)),
                amount instanceof Number ? ((Number) amount).doubleValue() : 0));
        }
        return out;
    }

    /** Taze (son N saniye) ve daha önce görülmemiş olay var mı? */
    static boolean recordNew(List<Closure> events, Map<String, Long> seen, long now, long freshnessSeconds) {
        boolean found = false;
        for (Closure event : events) {
            if (event.closedAt <= 0) continue;
            if (event.closedAt < now - freshnessSeconds) continue;
            String key = event.id + ":" + event.closedAt;
            if (seen.containsKey(key)) continue;
            seen.put(key, event.closedAt);
            found = true;
        }
        return found;
    }

    static void prune(Map<String, Long> seen, long now) {
        seen.values().removeIf(time -> time < now - 120 || time > now + 60);
    }

    static String serialize(Map<String, Long> seen) {
        return new JSONObject(seen).toString();
    }

    static Map<String, Long> deserialize(String json) {
        Map<String, Long> seen = new LinkedHashMap<>();
        try {
            JSONObject saved = new JSONObject(json);
            for (java.util.Iterator<String> keys = saved.keys(); keys.hasNext(); ) {
                String key = keys.next();
                seen.put(key, saved.optLong(key, 0));
            }
        } catch (Exception ignored) {
            // Bozuk kayıt çalışmayı engellemesin.
        }
        return seen;
    }
}
