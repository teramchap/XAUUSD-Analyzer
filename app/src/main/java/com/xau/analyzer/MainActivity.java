package com.xau.analyzer;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.widget.TextView;
import android.widget.Button;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {

    TextView price;
    TextView status;
    TextView analysis;
    TextView updated;
    Button refresh;

    final OkHttpClient client =
            new OkHttpClient.Builder().build();

    final Handler handler =
            new Handler(Looper.getMainLooper());

    Runnable timer;

    ArrayList<Double> m5Closes = new ArrayList<>();
    ArrayList<Double> m15Closes = new ArrayList<>();
    ArrayList<Double> h1Closes = new ArrayList<>();

    double livePrice = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        price = findViewById(R.id.price);
        status = findViewById(R.id.status);
        analysis = findViewById(R.id.analysis);
        updated = findViewById(R.id.updated);
        refresh = findViewById(R.id.refresh);

        refresh.setOnClickListener(v -> loadAll());

        loadAll();

        timer = () -> {
            loadAll();
            handler.postDelayed(timer, 15000);
        };

        handler.postDelayed(timer, 15000);
    }

    // =========================================================
    // دریافت قیمت لحظه‌ای
    // =========================================================

    void loadAll() {

        setStatus(
                "🟡 ANALYZING",
                Color.rgb(170, 120, 0),
                "در حال دریافت قیمت و کندل‌های M5 / M15 / H1..."
        );

        Request request = new Request.Builder()
                .url("https://biquote.io/api/XAUUSD?allowStale=false")
                .header("Accept", "application/json")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {

            @Override
            public void onFailure(
                    Call call,
                    java.io.IOException e) {

                runOnUiThread(() ->
                        setStatus(
                                "🔴 FEED ERROR",
                                Color.rgb(190, 30, 30),
                                "دریافت قیمت ناموفق بود.\n\n" +
                                e.getMessage()
                        )
                );
            }

            @Override
            public void onResponse(
                    Call call,
                    Response response)
                    throws java.io.IOException {

                String body =
                        response.body() != null
                                ? response.body().string()
                                : "";

                if (!response.isSuccessful()) {

                    runOnUiThread(() ->
                            setStatus(
                                    "🔴 HTTP " + response.code(),
                                    Color.rgb(190, 30, 30),
                                    body
                            )
                    );

                    return;
                }

                try {

                    JSONObject json =
                            new JSONObject(body);

                    livePrice =
                            json.optDouble(
                                    "mid",
                                    0
                            );

                    runOnUiThread(() ->
                            loadCandles()
                    );

                } catch (Exception e) {

                    runOnUiThread(() ->
                            setStatus(
                                    "🔴 JSON ERROR",
                                    Color.rgb(190, 30, 30),
                                    e.getMessage()
                            )
                    );
                }
            }
        });
    }

    // =========================================================
    // دریافت کندل‌های سه تایم‌فریم
    // =========================================================

    void loadCandles() {

        loadTimeframe(
                "5m",
                closes -> {
                    m5Closes = closes;

                    loadTimeframe(
                            "15m",
                            closes15 -> {
                                m15Closes = closes15;

                                loadTimeframe(
                                        "1h",
                                        closesH1 -> {
                                            h1Closes = closesH1;

                                            runOnUiThread(
                                                    () -> calculateAnalysis()
                                            );
                                        }
                                );
                            }
                    );
                }
        );
    }

    interface CandleCallback {
        void onResult(ArrayList<Double> closes);
    }

    void loadTimeframe(
            String interval,
            CandleCallback callback) {

        String url =
                "https://biquote.io/api/XAUUSD/ohlc" +
                "?interval=" + interval +
                "&limit=100";

        Request request =
                new Request.Builder()
                        .url(url)
                        .header("Accept", "application/json")
                        .get()
                        .build();

        client.newCall(request).enqueue(
                new Callback() {

                    @Override
                    public void onFailure(
                            Call call,
                            java.io.IOException e) {

                        runOnUiThread(() ->
                                setStatus(
                                        "🔴 CANDLE ERROR",
                                        Color.rgb(190, 30, 30),
                                        "خطا در دریافت " +
                                        interval +
                                        "\n\n" +
                                        e.getMessage()
                                )
                        );
                    }

                    @Override
                    public void onResponse(
                            Call call,
                            Response response)
                            throws java.io.IOException {

                        String body =
                                response.body() != null
                                        ? response.body().string()
                                        : "";

                        if (!response.isSuccessful()) {

                            runOnUiThread(() ->
                                    setStatus(
                                            "🔴 HTTP " +
                                            response.code(),
                                            Color.rgb(190, 30, 30),
                                            "خطا در " +
                                            interval +
                                            "\n\n" +
                                            body
                                    )
                            );

                            return;
                        }

                        try {

                            JSONObject root =
                                    new JSONObject(body);

                            JSONArray bars =
                                    root.getJSONArray("bars");

                            ArrayList<Double> closes =
                                    new ArrayList<>();

                            for (int i = 0;
                                 i < bars.length();
                                 i++) {

                                JSONObject bar =
                                        bars.getJSONObject(i);

                                if (bar.has("close")) {

                                    closes.add(
                                            bar.getDouble(
                                                    "close"
                                            )
                                    );
                                }
                            }

                            if (closes.size() < 50) {

                                throw new Exception(
                                        interval +
                                        ": کندل کافی دریافت نشد"
                                );
                            }

                            callback.onResult(closes);

                        } catch (Exception e) {

                            runOnUiThread(() ->
                                    setStatus(
                                            "🔴 OHLC ERROR",
                                            Color.rgb(190, 30, 30),
                                            "خطا در پردازش کندل " +
                                            interval +
                                            "\n\n" +
                                            e.getMessage()
                                    )
                            );
                        }
                    }
                }
        );
    }

    // =========================================================
    // موتور تحلیل
    // =========================================================

    void calculateAnalysis() {

        if (m5Closes.size() < 50 ||
                m15Closes.size() < 50 ||
                h1Closes.size() < 50) {

            setStatus(
                    "🟡 WAIT",
                    Color.rgb(170, 120, 0),
                    "داده کافی برای تحلیل دریافت نشده است."
            );

            return;
        }

        double ema20M5 =
                ema(m5Closes, 20);

        double ema50M5 =
                ema(m5Closes, 50);

        double ema20M15 =
                ema(m15Closes, 20);

        double ema50M15 =
                ema(m15Closes, 50);

        double ema20H1 =
                ema(h1Closes, 20);

        double ema50H1 =
                ema(h1Closes, 50);

        int bullish = 0;
        int bearish = 0;

        // M5
        if (ema20M5 > ema50M5)
            bullish++;
        else
            bearish++;

        // M15
        if (ema20M15 > ema50M15)
            bullish++;
        else
            bearish++;

        // H1
        if (ema20H1 > ema50H1)
            bullish++;
        else
            bearish++;

        String signal;

        if (bullish >= 3) {
            signal = "BUY";
        }
        else if (bearish >= 3) {
            signal = "SELL";
        }
        else {
            signal = "WAIT";
        }

        int score;

        if (bullish == 3 || bearish == 3)
            score = 75;
        else
            score = 50;

        price.setText(
                String.format(
                        Locale.US,
                        "XAUUSD  %.2f",
                        livePrice
                )
        );

        updated.setText(
                "آخرین تحلیل: " +
                new java.text.SimpleDateFormat(
                        "HH:mm:ss",
                        Locale.US
                ).format(
                        new java.util.Date()
                )
        );

        if (signal.equals("BUY")) {

            status.setText("🟢 BUY");
            status.setTextColor(
                    Color.rgb(0, 125, 70)
            );

        }
        else if (signal.equals("SELL")) {

            status.setText("🔴 SELL");
            status.setTextColor(
                    Color.rgb(190, 30, 30)
            );

        }
        else {

            status.setText("🟡 WAIT");
            status.setTextColor(
                    Color.rgb(170, 120, 0)
            );
        }

        analysis.setText(
                String.format(
                        Locale.US,

                        "امتیاز سیگنال: %d / 100\n\n" +

                        "روند M5: %s\n" +
                        "EMA20: %.2f\n" +
                        "EMA50: %.2f\n\n" +

                        "روند M15: %s\n" +
                        "EMA20: %.2f\n" +
                        "EMA50: %.2f\n\n" +

                        "روند H1: %s\n" +
                        "EMA20: %.2f\n" +
                        "EMA50: %.2f\n\n" +

                        "تایم‌فریم‌های صعودی: %d\n" +
                        "تایم‌فریم‌های نزولی: %d\n\n" +

                        "سیگنال فعلی: %s",

                        score,

                        trendText(
                                ema20M5,
                                ema50M5
                        ),

                        ema20M5,
                        ema50M5,

                        trendText(
                                ema20M15,
                                ema50M15
                        ),

                        ema20M15,
                        ema50M15,

                        trendText(
                                ema20H1,
                                ema50H1
                        ),

                        ema20H1,
                        ema50H1,

                        bullish,
                        bearish,

                        signal
                )
        );
    }

    // =========================================================
    // EMA
    // =========================================================

    double ema(
            ArrayList<Double> values,
            int period) {

        if (values.size() < period)
            return 0;

        double multiplier =
                2.0 / (period + 1);

        double ema =
                values.get(
                        values.size() - period
                );

        for (int i =
             values.size() - period + 1;
             i < values.size();
             i++) {

            ema =
                    ((values.get(i) - ema)
                            * multiplier)
                            + ema;
        }

        return ema;
    }

    String trendText(
            double ema20,
            double ema50) {

        if (ema20 > ema50)
            return "BULLISH";

        if (ema20 < ema50)
            return "BEARISH";

        return "NEUTRAL";
    }

    void setStatus(
            String text,
            int color,
            String message) {

        status.setText(text);
        status.setTextColor(color);
        analysis.setText(message);
    }
}
