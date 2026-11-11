package com.countereyes.app;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String TAG = "COUNTER-EYES";
    private static final String HOST = "appassets.androidplatform.net";
    private static final String INDEX = "https://" + HOST + "/index.html";
    private static final String SETUP = "https://" + HOST + "/setup.html";
    private static final int CAMERA_REQUEST = 1;

    private WebView web;
    private PosPoller poller;
    private PosConfig config;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean pageReady;
    private boolean resumed;
    private String status = "Başlatılıyor";

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();
        config = new PosConfig(getSharedPreferences("config", MODE_PRIVATE));
        poller = new PosPoller(getSharedPreferences("checks", MODE_PRIVATE), config, new PosPoller.Listener() {
            @Override public void status(String text) {
                if (!status.equals(text)) Log.i(TAG, "[pos] " + text);
                status = text;
                callPage("status(" + JSONObject.quote(text) + ")");
            }

            @Override public void closed() {
                Log.i(TAG, "[pos] adisyon kapanışı alındı");
                callPage("review(false)");
            }
        });

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.addJavascriptInterface(new Bridge(), "CE");

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isLocal(request.getUrl());
            }

            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (!isLocal(request.getUrl()) || !"GET".equals(request.getMethod())) return empty(403, "Forbidden");
                String name = request.getUrl().getPath();
                if (name == null || name.isEmpty() || "/".equals(name)) name = "/index.html";
                if (!name.matches("/[a-zA-Z0-9._/-]+") || name.contains("..")) return empty(404, "Not Found");
                String asset = "www" + name;
                try {
                    if (name.endsWith(".mp3")) {
                        String range = null;
                        for (Map.Entry<String, String> header : request.getRequestHeaders().entrySet()) {
                            if ("Range".equalsIgnoreCase(header.getKey())) range = header.getValue();
                        }
                        return audio(asset, range);
                    }
                    String mime = mime(name);
                    String charset = mime.startsWith("text/") || "application/json".equals(mime) ? "UTF-8" : null;
                    return new WebResourceResponse(mime, charset, getAssets().open(asset, AssetManager.ACCESS_STREAMING));
                } catch (IOException missing) {
                    return empty(404, "Not Found");
                }
            }

            @Override public void onPageFinished(WebView view, String url) {
                if (url != null && url.contains("setup.html")) {
                    pageReady = false;
                    web.evaluateJavascript(syncScript(), null);
                    return;
                }
                if (!INDEX.equals(url)) return;
                pageReady = true;
                callPage("enableAudio()");
                startPolling();
                web.postDelayed(() -> {
                    if (resumed && pageReady) web.evaluateJavascript(
                        "JSON.stringify(window.counterEyes && window.counterEyes.runtime())",
                        value -> Log.i(TAG, "[runtime] " + value));
                }, 12000);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onConsoleMessage(ConsoleMessage message) {
                Log.i(TAG, message.message());
                return true;
            }

            @Override public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    if (isLocal(request.getOrigin()) &&
                        checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        for (String resource : request.getResources()) {
                            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                                request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                                return;
                            }
                        }
                    }
                    request.deny();
                });
            }
        });

        FrameLayout frame = new FrameLayout(this);
        frame.addView(web, new FrameLayout.LayoutParams(-1, -1));
        Button setup = new Button(this);
        setup.setText("⚙");
        setup.setContentDescription("Kasa bağlantısı ve yorum QR ayarları");
        setup.setTextColor(Color.WHITE);
        setup.setBackgroundColor(0x5523373A);
        FrameLayout.LayoutParams corner = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.RIGHT);
        corner.setMargins(0, dp(8), dp(8), 0);
        frame.addView(setup, corner);
        setup.setOnClickListener(v -> openSetup());
        setContentView(frame);

        open(config.configured() ? INDEX : SETUP);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
        }
    }

    /** Ayarlar sayfasından gelen çağrılar. */
    private final class Bridge {
        @JavascriptInterface public String config() {
            return config.publicJson().toString();
        }

        @JavascriptInterface public String save(String json) {
            try {
                config.save(new JSONObject(json));
                poller.refresh();
                return "ok";
            } catch (Exception error) {
                return error.getMessage() == null ? "Ayarlar kaydedilemedi" : error.getMessage();
            }
        }

        @JavascriptInterface public void test(String json) {
            final JSONObject input;
            try {
                input = new JSONObject(json);
            } catch (Exception error) {
                post("Bu ayarlar okunamadı.");
                return;
            }
            final String token = config.secret("token");
            final String header = config.secret("headerValue");
            worker.execute(() -> {
                try {
                    PosSource source = PosConfig.preview(input, token, header);
                    if (source == null) {
                        post("Demo modunda bağlantı denenmez; kapanışı elle tetikle.");
                        return;
                    }
                    long now = System.currentTimeMillis() / 1000;
                    List<Closure> events = source.fetch(now - 3600, now + 5);
                    if (events.isEmpty()) {
                        post("Bağlantı çalıştı, son bir saatte kapanan adisyon görünmedi.");
                    } else {
                        Closure last = events.get(0);
                        for (Closure item : events) {
                            if (item.closedAt > last.closedAt) last = item;
                        }
                        post("Bağlantı çalıştı. Son kapanış: " + events.size() + " kayıt içinde " +
                            last.id + " (" + last.closedAt + ").");
                    }
                } catch (Exception error) {
                    post("Bağlantı hatası: " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
                }
            });
        }

        @JavascriptInterface public void open() {
            runOnUiThread(() -> MainActivity.this.open(INDEX));
        }

        @JavascriptInterface public void close() {
            runOnUiThread(() -> MainActivity.this.open(INDEX));
        }
    }

    private void post(String message) {
        runOnUiThread(() -> web.evaluateJavascript(
            "window.ceTestResult && window.ceTestResult(" + JSONObject.quote(message) + ")", null));
    }

    private void open(String url) {
        pageReady = false;
        poller.stop();
        web.loadUrl(url);
    }

    private void openSetup() {
        open(SETUP);
    }

    private String syncScript() {
        return "window.ceInit && window.ceInit(" + JSONObject.quote(config.publicJson().toString()) + ")";
    }

    private void startPolling() {
        if (!resumed || !pageReady) return;
        poller.start();
    }

    private void callPage(String method) {
        if (pageReady && resumed) web.evaluateJavascript("window.counterEyes && window.counterEyes." + method, null);
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == CAMERA_REQUEST && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            pageReady = false;
            poller.stop();
            web.reload();
        } else if (request == CAMERA_REQUEST) {
            Toast.makeText(this, "Kamera izni verilmezse gözler yalnız boşta hareket eder.", Toast.LENGTH_LONG).show();
        }
    }

    private WebResourceResponse audio(String asset, String range) throws IOException {
        byte[] bytes;
        try (java.io.InputStream input = getAssets().open(asset)) {
            bytes = SimpraSource.read(input, 2 * 1024 * 1024);
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept-Ranges", "bytes");
        if (range == null) {
            headers.put("Content-Length", Integer.toString(bytes.length));
            return new WebResourceResponse("audio/mpeg", null, 200, "OK", headers, new ByteArrayInputStream(bytes));
        }
        int[] bounds = MediaRange.parse(range, bytes.length);
        if (bounds == null) {
            headers.put("Content-Range", "bytes */" + bytes.length);
            return new WebResourceResponse("audio/mpeg", null, 416, "Range Not Satisfiable", headers,
                new ByteArrayInputStream(new byte[0]));
        }
        int length = bounds[1] - bounds[0] + 1;
        headers.put("Content-Length", Integer.toString(length));
        headers.put("Content-Range", "bytes " + bounds[0] + "-" + bounds[1] + "/" + bytes.length);
        return new WebResourceResponse("audio/mpeg", null, 206, "Partial Content", headers,
            new ByteArrayInputStream(bytes, bounds[0], length));
    }

    private static boolean isLocal(Uri uri) {
        return "https".equals(uri.getScheme()) && HOST.equals(uri.getHost()) &&
            uri.getPort() == -1 && uri.getUserInfo() == null;
    }

    private static WebResourceResponse empty(int code, String reason) {
        return new WebResourceResponse("text/plain", "UTF-8", code, reason, Collections.emptyMap(),
            new ByteArrayInputStream(new byte[0]));
    }

    private static String mime(String name) {
        if (name.endsWith(".html")) return "text/html";
        if (name.endsWith(".js") || name.endsWith(".mjs")) return "text/javascript";
        if (name.endsWith(".wasm")) return "application/wasm";
        if (name.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN |
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) hideSystemUi();
    }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        web.onResume();
        hideSystemUi();
        startPolling();
    }

    @Override protected void onPause() {
        resumed = false;
        poller.stop();
        web.onPause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        poller.close();
        worker.shutdownNow();
        web.destroy();
        super.onDestroy();
    }
}
