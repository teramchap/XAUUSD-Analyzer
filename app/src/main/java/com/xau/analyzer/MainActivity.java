package com.xau.analyzer;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.widget.TextView;
import android.widget.Button;

import java.util.ArrayList;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {

    TextView price, status, analysis, updated;
    Button refresh;

    final OkHttpClient client = new OkHttpClient.Builder().build();
    final Handler handler = new Handler(Looper.getMainLooper());

    Runnable timer;

    ArrayList<Candle> m5 = new ArrayList<>();
    ArrayList<Candle> m15 = new ArrayList<>();
    ArrayList<Candle> h1 = new ArrayList<>();

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
    // Candle
    // =========================================================

    static class Candle {

        double open;
        double high;
        double low;
        double close;

        Candle(
                double open,
                double high,
                double low,
                double close) {

            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
        }
    }

    // =========================================================
    // دریافت قیمت
    // =========================================================

    void loadAll() {

        setStatus(
                "🟡 ANALYZING",
                Color.rgb(170, 120, 0),
                "در حال دریافت قیمت و داده‌های بازار..."
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
                                "اتصال به فید برقرار نشد.\n\n" +
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
                            json.optDouble("mid", 0);

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
    // دریافت کندل‌ها
    // =========================================================

    void loadCandles() {

        loadTimeframe("5m", candles5 -> {

            m5 = candles5;

            loadTimeframe("15m", candles15 -> {

                m15 = candles15;

                loadTimeframe("1h", candlesH1 -> {

                    h1 = candlesH1;

                    runOnUiThread(
                            this::calculateAnalysis
                    );
                });
            });
        });
    }

    interface CandleCallback {
        void onResult(ArrayList<Candle> candles);
    }

    void loadTimeframe(
            String interval,
            CandleCallback callback) {

        String url =
                "https://biquote.io/api/XAUUSD/ohlc" +
                "?interval=" + interval +
                "&limit=150";

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

                            ArrayList<Candle> candles =
                                    new ArrayList<>();

                            for (int i = 0;
                                 i < bars.length();
                                 i++) {

                                JSONObject b =
                                        bars.getJSONObject(i);

                                double open =
                                        b.getDouble("open");

                                double high =
                                        b.getDouble("high");

                                double low =
                                        b.getDouble("low");

                                double close =
                                        b.getDouble("close");

                                candles.add(
                                        new Candle(
                                                open,
                                                high,
                                                low,
                                                close
                                        )
                                );
                            }

                            if (candles.size() < 60) {

                                throw new Exception(
                                        interval +
                                        ": کندل کافی دریافت نشد"
                                );
                            }

                            callback.onResult(candles);

                        } catch (Exception e) {

                            runOnUiThread(() ->
                                    setStatus(
                                            "🔴 OHLC ERROR",
                                            Color.rgb(190, 30, 30),
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
    // موتور اصلی تحلیل
    // =========================================================

    void calculateAnalysis() {

        if (m5.size() < 60 ||
                m15.size() < 60 ||
                h1.size() < 60) {

            setStatus(
                    "🟡 WAIT",
                    Color.rgb(170, 120, 0),
                    "داده کافی برای تحلیل وجود ندارد."
            );

            return;
        }

        // -----------------------------------------------------
        // EMA
        // -----------------------------------------------------

        double m5Ema20 = ema(m5, 20);
        double m5Ema50 = ema(m5, 50);

        double m15Ema20 = ema(m15, 20);
        double m15Ema50 = ema(m15, 50);

        double h1Ema20 = ema(h1, 20);
        double h1Ema50 = ema(h1, 50);

        // -----------------------------------------------------
        // ATR
        // -----------------------------------------------------

        double atrM5 = atr(m5, 14);
        double atrM15 = atr(m15, 14);

        // -----------------------------------------------------
        // امتیاز
        // -----------------------------------------------------

        int buyScore = 0;
        int sellScore = 0;

        ArrayList<String> reasons =
                new ArrayList<>();

        // =====================================================
        // 1. روند H1
        // =====================================================

        if (h1Ema20 > h1Ema50) {

            buyScore += 20;

            reasons.add(
                    "H1 روند صعودی"
            );

        } else {

            sellScore += 20;

            reasons.add(
                    "H1 روند نزولی"
            );
        }

        // =====================================================
        // 2. روند M15
        // =====================================================

        if (m15Ema20 > m15Ema50) {

            buyScore += 15;

            reasons.add(
                    "M15 صعودی"
            );

        } else {

            sellScore += 15;

            reasons.add(
                    "M15 نزولی"
            );
        }

        // =====================================================
        // 3. روند M5
        // =====================================================

        if (m5Ema20 > m5Ema50) {

            buyScore += 10;

        } else {

            sellScore += 10;
        }

        // =====================================================
        // 4. Market Structure
        // =====================================================

        int structure =
                marketStructure(m15);

        if (structure > 0) {

            buyScore += 15;

            reasons.add(
                    "ساختار بازار صعودی"
            );

        } else if (structure < 0) {

            sellScore += 15;

            reasons.add(
                    "ساختار بازار نزولی"
            );
        }

        // =====================================================
        // 5. Liquidity Sweep
        // =====================================================

        if (bullishSweep(m5)) {

            buyScore += 15;

            reasons.add(
                    "Bullish Liquidity Sweep"
            );
        }

        if (bearishSweep(m5)) {

            sellScore += 15;

            reasons.add(
                    "Bearish Liquidity Sweep"
            );
        }

        // =====================================================
        // 6. Engulfing
        // =====================================================

        if (bullishEngulfing(m5)) {

            buyScore += 10;

            reasons.add(
                    "Bullish Engulfing"
            );
        }

        if (bearishEngulfing(m5)) {

            sellScore += 10;

            reasons.add(
                    "Bearish Engulfing"
            );
        }

        // =====================================================
        // 7. Breakout
        // =====================================================

        if (bullishBreakout(m5)) {

            buyScore += 10;

            reasons.add(
                    "Bullish Breakout"
            );
        }

        if (bearishBreakout(m5)) {

            sellScore += 10;

            reasons.add(
                    "Bearish Breakout"
            );
        }

        // =====================================================
        // انتخاب سیگنال
        // =====================================================

        String signal;

        int score;

        if (buyScore >= 60 &&
                buyScore > sellScore + 10) {

            signal = "BUY";
            score = Math.min(buyScore, 100);

        } else if (sellScore >= 60 &&
                sellScore > buyScore + 10) {

            signal = "SELL";
            score = Math.min(sellScore, 100);

        } else {

            signal = "WAIT";
            score = Math.max(
                    buyScore,
                    sellScore
            );
        }

        // =====================================================
        // Entry / SL / TP
        // =====================================================

        double entry = livePrice;

        double sl;
        double tp1;
        double tp2;
        double tp3;

        if (signal.equals("BUY")) {

            sl = entry - atrM15 * 1.5;

            double risk =
                    entry - sl;

            tp1 =
                    entry + risk * 1.0;

            tp2 =
                    entry + risk * 2.0;

            tp3 =
                    entry + risk * 3.0;

        } else if (signal.equals("SELL")) {

            sl = entry + atrM15 * 1.5;

            double risk =
                    sl - entry;

            tp1 =
                    entry - risk * 1.0;

            tp2 =
                    entry - risk * 2.0;

            tp3 =
                    entry - risk * 3.0;

        } else {

            sl = 0;
            tp1 = 0;
            tp2 = 0;
            tp3 = 0;
        }

        // =====================================================
        // نمایش
        // =====================================================

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

        } else if (signal.equals("SELL")) {

            status.setText("🔴 SELL");
            status.setTextColor(
                    Color.rgb(190, 30, 30)
            );

        } else {

            status.setText("🟡 WAIT");
            status.setTextColor(
                    Color.rgb(170, 120, 0)
            );
        }

        StringBuilder text =
                new StringBuilder();

        text.append(
                String.format(
                        Locale.US,
                        "امتیاز BUY: %d / 100\n" +
                        "امتیاز SELL: %d / 100\n\n",
                        buyScore,
                        sellScore
                )
        );

        text.append(
                "━━━━━━━━━━━━━━━━\n"
        );

        text.append(
                "📊 روند\n\n"
        );

        text.append(
                "H1: " +
                trend(h1Ema20, h1Ema50) +
                "\n"
        );

        text.append(
                "M15: " +
                trend(m15Ema20, m15Ema50) +
                "\n"
        );

        text.append(
                "M5: " +
                trend(m5Ema20, m5Ema50) +
                "\n\n"
        );

        text.append(
                String.format(
                        Locale.US,
                        "ATR M5: %.2f\n" +
                        "ATR M15: %.2f\n\n",
                        atrM5,
                        atrM15
                )
        );

        text.append(
                "━━━━━━━━━━━━━━━━\n"
        );

        text.append(
                "🎯 سیگنال\n\n"
        );

        text.append(
                "Signal: " +
                signal +
                "\n"
        );

        text.append(
                "Score: " +
                score +
                " / 100\n\n"
        );

        if (!signal.equals("WAIT")) {

            text.append(
                    String.format(
                            Locale.US,

                            "Entry: %.2f\n" +
                            "Stop Loss: %.2f\n\n" +

                            "TP1: %.2f\n" +
                            "TP2: %.2f\n" +
                            "TP3: %.2f\n\n",

                            entry,
                            sl,
                            tp1,
                            tp2,
                            tp3
                    )
            );

            text.append(
                    "Risk / Reward:\n" +
                    "TP1 = 1:1\n" +
                    "TP2 = 1:2\n" +
                    "TP3 = 1:3\n\n"
            );
        }

        text.append(
                "━━━━━━━━━━━━━━━━\n"
        );

        text.append(
                "🧠 دلایل:\n\n"
        );

        for (String reason : reasons) {

            text.append(
                    "• " +
                    reason +
                    "\n"
            );
        }

        text.append(
                "\n⚠️ این نسخه ابزار تحلیل است؛ " +
                "سیگنال تضمینی یا توصیه قطعی معامله نیست."
        );

        analysis.setText(
                text.toString()
        );
    }

    // =========================================================
    // EMA
    // =========================================================

    double ema(
            ArrayList<Candle> candles,
            int period) {

        if (candles.size() < period)
            return 0;

        double multiplier =
                2.0 /
                (period + 1);

        double value =
                candles.get(
                        candles.size() - period
                ).close;

        for (int i =
             candles.size() - period + 1;
             i < candles.size();
             i++) {

            value =
                    ((candles.get(i).close - value)
                            * multiplier)
                            + value;
        }

        return value;
    }

    // =========================================================
    // ATR
    // =========================================================

    double atr(
            ArrayList<Candle> candles,
            int period) {

        if (candles.size() < period + 1)
            return 0;

        double sum = 0;

        int start =
                candles.size() - period;

        for (int i = start;
             i < candles.size();
             i++) {

            Candle current =
                    candles.get(i);

            Candle previous =
                    candles.get(i - 1);

            double tr =
                    Math.max(
                            current.high -
                            current.low,

                            Math.max(
                                    Math.abs(
                                            current.high -
                                            previous.close
                                    ),

                                    Math.abs(
                                            current.low -
                                            previous.close
                                    )
                            )
                    );

            sum += tr;
        }

        return sum / period;
    }

    // =========================================================
    // Market Structure
    // =========================================================

    int marketStructure(
            ArrayList<Candle> candles) {

        int n =
                candles.size();

        if (n < 10)
            return 0;

        Candle old =
                candles.get(n - 6);

        Candle recent =
                candles.get(n - 2);

        if (recent.high > old.high &&
                recent.low > old.low) {

            return 1;
        }

        if (recent.high < old.high &&
                recent.low < old.low) {

            return -1;
        }

        return 0;
    }

    // =========================================================
    // Liquidity Sweep
    // =========================================================

    boolean bullishSweep(
            ArrayList<Candle> candles) {

        int n =
                candles.size();

        if (n < 6)
            return false;

        Candle last =
                candles.get(n - 1);

        double previousLow =
                candles.get(n - 2).low;

        for (int i = n - 6;
             i < n - 2;
             i++) {

            previousLow =
                    Math.min(
                            previousLow,
                            candles.get(i).low
                    );
        }

        return last.low < previousLow &&
                last.close > previousLow;
    }

    boolean bearishSweep(
            ArrayList<Candle> candles) {

        int n =
                candles.size();

        if (n < 6)
            return false;

        Candle last =
                candles.get(n - 1);

        double previousHigh =
                candles.get(n - 2).high;

        for (int i = n - 6;
             i < n - 2;
             i++) {

            previousHigh =
                    Math.max(
                            previousHigh,
                            candles.get(i).high
                    );
        }

        return last.high > previousHigh &&
                last.close < previousHigh;
    }

    // =========================================================
    // Engulfing
    // =========================================================

    boolean bullishEngulfing(
            ArrayList<Candle> candles) {

        int n =
                candles.size();

        if (n < 2)
            return false;

        Candle prev =
                candles.get(n - 2);

        Candle last =
                candles.get(n - 1);

        boolean previousBearish =
                prev.close < prev.open;

        boolean currentBullish =
                last.close > last.open;

        return previousBearish &&
                currentBullish &&
                last.open <= prev.close &&
                last.close >= prev.open;
    }

    boolean bearishEngulfing(
            ArrayList<Candle> candles) {

        int n =
                candles.size();

        if (n < 2)
            return false;

        Candle prev =
                candles.get(n - 2);

        Candle last =
                candles.get(n - 1);

        boolean previousBullish =
                prev.close > prev.open;

        boolean currentBearish =
                last.close < last.open;

        return previousBullish &&
                currentBearish &&
                last.open >= prev.close &&
                last.close <= prev.open;
    }

    // =========================================================
    // Breakout
    // =========================================================

    boolean bullishBreakout(
            ArrayList<Candle> candles) {

        int n =
                candles.size();

        if (n < 10)
            return false;

        Candle last =
                candles.get(n - 1);

        double highest =
                Double.MIN_VALUE;

        for (int i = n - 6;
             i < n - 1;
             i++) {

            highest =
                    Math.max(
                            highest,
                            candles.get(i).high
                    );
        }

        return last.close > highest;
    }

    boolean bearishBreakout(
            ArrayList<Candle> candles) {

        int n =
                candles.size();

        if (n < 10)
            return false;

        Candle last =
                candles.get(n - 1);

        double lowest =
                Double.MAX_VALUE;

        for (int i = n - 6;
             i < n - 1;
             i++) {

            lowest =
                    Math.min(
                            lowest,
                            candles.get(i).low
                    );
        }

        return last.close < lowest;
    }

    // =========================================================
    // Trend
    // =========================================================

    String trend(
            double ema20,
            double ema50) {

        if (ema20 > ema50)
            return "BULLISH 🟢";

        if (ema20 < ema50)
            return "BEARISH 🔴";

        return "NEUTRAL 🟡";
    }

    // =========================================================
    // Status
    // =========================================================

    void setStatus(
            String text,
            int color,
            String message) {

        status.setText(text);
        status.setTextColor(color);
        analysis.setText(message);
    }
                                          }
