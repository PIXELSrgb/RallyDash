package com.rallydash.wireless;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public class MainActivity extends Activity {
    private static final int DISCOVERY_PORT = 20891;
    private static final int WEB_PORT = 8765;
    private volatile boolean discovering = true;
    private WebView webView;
    private LinearLayout connectView;
    private TextView statusText;
    private EditText ipInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        immersive();
        showConnectScreen();
        startDiscovery();
    }

    private void immersive() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    private TextView label(String text, int size, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        return v;
    }

    private void showConnectScreen() {
        int pad = dp(22);
        connectView = new LinearLayout(this);
        connectView.setOrientation(LinearLayout.VERTICAL);
        connectView.setGravity(Gravity.CENTER);
        connectView.setPadding(pad, pad, pad, pad);
        connectView.setBackgroundColor(Color.rgb(5, 6, 9));

        TextView brand = label("RALLY//DASH", 31, Color.WHITE);
        brand.setGravity(Gravity.CENTER);
        brand.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        connectView.addView(brand, new LinearLayout.LayoutParams(-1, -2));

        statusText = label("Searching for your PC over Wi-Fi…", 14, Color.rgb(139, 148, 163));
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.setMargins(0, dp(12), 0, dp(22));
        connectView.addView(statusText, statusParams);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);

        ipInput = new EditText(this);
        ipInput.setSingleLine(true);
        ipInput.setHint("PC IP e.g. 192.168.0.25");
        ipInput.setTextColor(Color.WHITE);
        ipInput.setHintTextColor(Color.rgb(92, 101, 114));
        ipInput.setBackgroundColor(Color.rgb(18, 22, 28));
        ipInput.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams ipParams = new LinearLayout.LayoutParams(dp(310), dp(52));
        row.addView(ipInput, ipParams);

        Button connect = new Button(this);
        connect.setText("CONNECT");
        connect.setTextColor(Color.WHITE);
        connect.setBackgroundColor(Color.rgb(255, 48, 72));
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(dp(125), dp(52));
        buttonParams.setMargins(dp(10), 0, 0, 0);
        row.addView(connect, buttonParams);
        connectView.addView(row);

        TextView help = label("Same Wi-Fi as the PC • No Bluetooth needed", 12, Color.rgb(85, 94, 107));
        help.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(-1, -2);
        helpParams.setMargins(0, dp(18), 0, 0);
        connectView.addView(help, helpParams);

        connect.setOnClickListener(v -> {
            String ip = ipInput.getText().toString().trim();
            if (ip.isEmpty()) {
                Toast.makeText(this, "Enter the PC IP shown by RallyDashBridge", Toast.LENGTH_SHORT).show();
                return;
            }
            if (ip.startsWith("http://") || ip.startsWith("https://")) loadDashboard(ip);
            else loadDashboard("http://" + ip + ":" + WEB_PORT);
        });

        setContentView(connectView);
    }

    private void startDiscovery() {
        new Thread(() -> {
            while (discovering && !isFinishing()) {
                try (DatagramSocket socket = new DatagramSocket()) {
                    socket.setBroadcast(true);
                    socket.setSoTimeout(1200);
                    byte[] msg = "RALLYDASH_DISCOVER".getBytes(StandardCharsets.UTF_8);

                    socket.send(new DatagramPacket(msg, msg.length, InetAddress.getByName("255.255.255.255"), DISCOVERY_PORT));

                    for (NetworkInterface nif : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                        if (!nif.isUp() || nif.isLoopback()) continue;
                        for (InterfaceAddress ia : nif.getInterfaceAddresses()) {
                            InetAddress b = ia.getBroadcast();
                            if (b instanceof Inet4Address) {
                                try { socket.send(new DatagramPacket(msg, msg.length, b, DISCOVERY_PORT)); }
                                catch (Exception ignored) {}
                            }
                        }
                    }

                    byte[] buf = new byte[512];
                    DatagramPacket reply = new DatagramPacket(buf, buf.length);
                    socket.receive(reply);
                    String text = new String(reply.getData(), 0, reply.getLength(), StandardCharsets.UTF_8).trim();
                    if (text.startsWith("RALLYDASH|")) {
                        String url = text.substring("RALLYDASH|".length());
                        discovering = false;
                        runOnUiThread(() -> loadDashboard(url));
                        return;
                    }
                } catch (Exception ignored) {
                    runOnUiThread(() -> {
                        if (statusText != null) statusText.setText("Still searching… or enter the PC IP below");
                    });
                }
                try { Thread.sleep(500); } catch (InterruptedException ignored) { return; }
            }
        }, "RallyDashDiscovery").start();
    }

    private void loadDashboard(String baseUrl) {
        discovering = false;
        String url = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(5, 6, 9));
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setMediaPlaybackRequiresUserGesture(false);
        if (Build.VERSION.SDK_INT >= 21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req != null && req.isForMainFrame()) {
                    Toast.makeText(MainActivity.this, "PC dashboard not reachable", Toast.LENGTH_SHORT).show();
                }
            }
        });
        setContentView(webView);
        webView.loadUrl(url);
        immersive();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else if (webView != null) {
            webView.destroy();
            webView = null;
            discovering = true;
            showConnectScreen();
            startDiscovery();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) immersive();
    }

    @Override
    protected void onDestroy() {
        discovering = false;
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
