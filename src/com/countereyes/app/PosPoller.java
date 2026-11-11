package com.countereyes.app;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Kasa kaynağını yoklar; taze kapanış varsa ekrana QR isteği gönderir. */
final class PosPoller {
    static final long FRESH_SECONDS = 35;
    private static final long ERROR_MS = 15000;

    interface Listener {
        void status(String text);
        void closed();
    }

    private final SharedPreferences prefs;
    private final PosConfig config;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Runnable tick = this::poll;
    private final Map<String, Long> seen;
    private int generation;
    private String signature = "";
    private boolean running;
    private long activeSince;

    PosPoller(SharedPreferences prefs, PosConfig config, Listener listener) {
        this.prefs = prefs;
        this.config = config;
        this.listener = listener;
        this.seen = Closure.deserialize(prefs.getString("seen", "{}"));
    }

    void start() {
        String current = config.signature();
        if (running && current.equals(signature)) return;
        stop();
        signature = current;
        running = true;
        String provider = config.provider();
        if (PosConfig.DEMO.equals(provider)) {
            listener.status("Demo modu: kapanışı sağ üstteki ⚙ menüsünden tetikle.");
            return;
        }
        if (!config.ready()) {
            listener.status("Kasa bağlantısı ayarlanmadı. ⚙ menüsünden kur.");
            return;
        }
        activeSince = System.currentTimeMillis() / 1000 - 2;
        listener.status("Kasa bağlantısı denetleniyor…");
        main.post(tick);
    }

    void stop() {
        generation++;
        running = false;
        main.removeCallbacks(tick);
    }

    void refresh() {
        signature = "";
        start();
    }

    void close() {
        stop();
        worker.shutdownNow();
    }

    private void poll() {
        if (!running) return;
        final int current = generation;
        final long now = System.currentTimeMillis() / 1000;
        final long since = Math.max(activeSince, now - FRESH_SECONDS);
        final PosSource source;
        try {
            source = config.source();
        } catch (Exception error) {
            fail(current, "Ayarlar okunamadı: " + message(error));
            return;
        }
        if (source == null) return;
        final String label = source.label();
        worker.execute(() -> {
            try {
                List<Closure> events = source.fetch(since, now + 5);
                main.post(() -> receive(current, label, events));
            } catch (Exception error) {
                main.post(() -> fail(current, label + " hatası: " + message(error)));
            }
        });
    }

    private void receive(int current, String label, List<Closure> events) {
        if (current != generation) return;
        long now = System.currentTimeMillis() / 1000;
        Closure.prune(seen, now);
        boolean show = Closure.recordNew(events, seen, now, FRESH_SECONDS);
        if (show) prefs.edit().putString("seen", Closure.serialize(seen)).apply();
        listener.status(label + " bağlı");
        if (show) listener.closed();
        main.postDelayed(tick, config.pollMs());
    }

    private void fail(int current, String text) {
        if (current != generation) return;
        listener.status(text + " · yeniden denenecek");
        main.postDelayed(tick, ERROR_MS);
    }

    private static String message(Exception error) {
        String text = error.getMessage();
        return text == null || text.isEmpty() ? error.getClass().getSimpleName() : text;
    }
}
