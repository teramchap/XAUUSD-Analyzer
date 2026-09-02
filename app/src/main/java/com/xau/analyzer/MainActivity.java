package com.xau.analyzer;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.widget.TextView;
import android.widget.Button;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
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

    final OkHttpClient client =
            new OkHttpClient.Builder().build();

    final Handler handler =
            new Handler(Looper.getMainLooper());

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
        boolean isOpen;

        Candle(
                double open,
                double high,
                double low,
                double close,
                boolean isOpen) {

            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.isOpen = isOpen;
        }
    }

    // =========================================================
    // دریافت قیمت زنده
    // =========================================================

    void loadAll() {

        setStatus(
                "🟡 ANALYZING",
                Color.rgb(170, 120, 0),
                "در حال دریافت قیمت و کندل‌های بسته‌شده..."
        );

        Request request =
                new Request.Builder()
                        .url(
                                "https://biquote.io/api/XAUUSD?allowStale=false"
                        )
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
                                            "🔴 HTTP " +
                                            response.code(),
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

                            if (livePrice <= 0) {
                                throw new Exception(
                                        "قیمت معتبر دریافت نشد."
                                );
                            }

                            runOnUiThread(
                                    MainActivity.this::loadCandles
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
                }
        );
    }

    // =========================================================
    // دریافت تایم‌فریم‌ها
    // =========================================================

    void loadCandles() {

        loadTimeframe(
                "5m",
                candles5 -> {

                    m5 = candles5;

                    loadTimeframe(
                            "15m",
                            candles15 -> {

                                m15 = candles15;

                                loadTimeframe(
                                        "1h",
                                        candlesH1 -> {

                                            h1 = candlesH1;

                                            runOnUiThread(
                                                    this::calculateAnalysis
                                            );
                                        }
                                );
                            }
                    );
                }
        );
    }

    interface CandleCallback {
        void onResult(
                ArrayList<Candle> candles
        );
    }

    void loadTimeframe(
            String interval,
            CandleCallback callback) {

        String url =
                "https://biquote.io/api/XAUUSD/ohlc" +
                "?interval=" +
                interval +
                "&limit=200";

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

                            ArrayList<Candle> candles =
                                    new ArrayList<>();

                            for (
                                    int i = 0;
                                    i < bars.length();
                                    i++
                            ) {

                                JSONObject b =
                                        bars.getJSONObject(i);

                                boolean open =
                                        b.optBoolean(
                                                "isOpen",
                                                false
                                        );

                                /*
                                 * کندل در حال تشکیل را
                                 * وارد تحلیل نمی‌کنیم.
                                 */
                                if (open) {
                                    continue;
                                }

                                candles.add(
                                        new Candle(
                                                b.getDouble("open"),
                                                b.getDouble("high"),
                                                b.getDouble("low"),
                                                b.getDouble("close"),
                                                false
                                        )
                                );
                            }

                            if (candles.size() < 70) {

                                throw new Exception(
                                        interval +
                                        ": کندل بسته‌شده کافی نیست"
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
    // موتور تحلیل نسخه 10
    // =========================================================

    void calculateAnalysis() {

        if (
                m5.size() < 70 ||
                m15.size() < 70 ||
                h1.size() < 70
        ) {

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

        double h1Ema20 = ema(h1, 20);
        double h1Ema50 = ema(h1, 50);

        double m15Ema20 = ema(m15, 20);
        double m15Ema50 = ema(m15, 50);

        double m5Ema20 = ema(m5, 20);
        double m5Ema50 = ema(m5, 50);

        // -----------------------------------------------------
        // ATR
        // -----------------------------------------------------

        double atrM5 = atr(m5, 14);
        double atrM15 = atr(m15, 14);

        // -----------------------------------------------------
        // Trend
        // -----------------------------------------------------

        boolean h1Bull =
                h1Ema20 > h1Ema50;

        boolean h1Bear =
                h1Ema20 < h1Ema50;

        boolean m15Bull =
                m15Ema20 > m15Ema50;

        boolean m15Bear =
                m15Ema20 < m15Ema50;

        boolean m5Bull =
                m5Ema20 > m5Ema50;

        boolean m5Bear =
                m5Ema20 < m5Ema50;

        // -----------------------------------------------------
        // ساختار واقعی M15
        // -----------------------------------------------------

        int structure =
                swingStructure(m15);

        boolean structureBull =
                structure > 0;

        boolean structureBear =
                structure < 0;

        // -----------------------------------------------------
        // تریگرهای M5
        // -----------------------------------------------------

        boolean bullSweep =
                bullishSweep(m5);

        boolean bearSweep =
                bearishSweep(m5);

        boolean bullEngulf =
                bullishEngulfing(m5);

        boolean bearEngulf =
                bearishEngulfing(m5);

        boolean bullBreak =
                bullishBreakout(m5);

        boolean bearBreak =
                bearishBreakout(m5);

        // -----------------------------------------------------
        // امتیاز
        // -----------------------------------------------------

        int buyScore = 0;
        int sellScore = 0;

        ArrayList<String> buyReasons =
                new ArrayList<>();

        ArrayList<String> sellReasons =
                new ArrayList<>();

        // =====================================================
        // BUY SCORE
        // =====================================================

        if (h1Bull) {
            buyScore += 25;
            buyReasons.add("H1 صعودی");
        }

        if (m15Bull) {
            buyScore += 20;
            buyReasons.add("M15 صعودی");
        }

        if (structureBull) {
            buyScore += 15;
            buyReasons.add("ساختار M15 صعودی");
        }

        if (m5Bull) {
            buyScore += 10;
            buyReasons.add("M5 صعودی");
        }

        if (bullSweep) {
            buyScore += 10;
            buyReasons.add("Liquidity Sweep صعودی");
        }

        if (bullEngulf) {
            buyScore += 10;
            buyReasons.add("Bullish Engulfing");
        }

        if (bullBreak) {
            buyScore += 10;
            buyReasons.add("Breakout صعودی");
        }

        // =====================================================
        // SELL SCORE
        // =====================================================

        if (h1Bear) {
            sellScore += 25;
            sellReasons.add("H1 نزولی");
        }

        if (m15Bear) {
            sellScore += 20;
            sellReasons.add("M15 نزولی");
        }

        if (structureBear) {
            sellScore += 15;
            sellReasons.add("ساختار M15 نزولی");
        }

        if (m5Bear) {
            sellScore += 10;
            sellReasons.add("M5 نزولی");
        }

        if (bearSweep) {
            sellScore += 10;
            sellReasons.add("Liquidity Sweep نزولی");
        }

        if (bearEngulf) {
            sellScore += 10;
            sellReasons.add("Bearish Engulfing");
        }

        if (bearBreak) {
            sellScore += 10;
            sellReasons.add("Breakout نزولی");
        }

        // =====================================================
        // فیلتر ورود
        // =====================================================

        String signal = "WAIT";

        String waitReason = "";

        /*
         * BUY:
         *
         * H1 + M15 صعودی
         * M5 صعودی
         * حداقل یک تریگر واقعی
         */

        boolean buyTrigger =
                bullSweep ||
                bullEngulf ||
                bullBreak;

        boolean sellTrigger =
                bearSweep ||
                bearEngulf ||
                bearBreak;

        if (h1Bull && m15Bull) {

            if (!m5Bull) {

                waitReason =
                        "M5 هنوز صعودی نشده است.";

            } else if (!buyTrigger) {

                waitReason =
                        "تریگر ورود BUY در M5 تأیید نشده.";

            } else if (buyScore >= 70) {

                signal = "BUY";

            } else {

                waitReason =
                        "قدرت BUY هنوز به حد تأیید نرسیده.";
            }

        } else if (h1Bear && m15Bear) {

            if (!m5Bear) {

                waitReason =
                        "M5 هنوز نزولی نشده است.";

            } else if (!sellTrigger) {

                waitReason =
                        "تریگر ورود SELL در M5 تأیید نشده.";

            } else if (sellScore >= 70) {

                signal = "SELL";

            } else {

                waitReason =
                        "قدرت SELL هنوز به حد تأیید نرسیده.";
            }

        } else {

            waitReason =
                    "H1 و M15 هم‌جهت نیستند.";
        }

        // =====================================================
        // Entry / SL / TP
        // =====================================================

        double entry = livePrice;

        double sl = 0;
        double tp1 = 0;
        double tp2 = 0;
        double tp3 = 0;

        double risk = 0;

        if (signal.equals("BUY")) {

            sl =
                    entry -
                    (atrM15 * 1.5);

            risk =
                    entry - sl;

            tp1 =
                    entry + risk;

            tp2 =
                    entry + (risk * 2);

            tp3 =
                    entry + (risk * 3);

        } else if (signal.equals("SELL")) {

            sl =
                    entry +
                    (atrM15 * 1.5);

            risk =
                    sl - entry;

            tp1 =
                    entry - risk;

            tp2 =
                    entry - (risk * 2);

            tp3 =
                    entry - (risk * 3);
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
                new SimpleDateFormat(
                        "HH:mm:ss",
                        Locale.US
                ).format(
                        new Date()
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

        // =====================================================
        // متن خروجی
        // =====================================================

        StringBuilder text =
                new StringBuilder();

        int score =
                Math.max(
                        buyScore,
                        sellScore
                );

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
                "📊 جهت بازار\n\n"
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
                "ساختار M15: " +
                structureText(structure) +
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

        if (signal.equals("BUY") ||
                signal.equals("SELL")) {

            text.append(
                    String.format(
                            Locale.US,

                            "Entry: %.2f\n" +
                            "SL: %.2f\n\n" +

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

        } else {

            text.append(
                    "⛔ فعلاً نقطه ورود تأیید نشده.\n\n"
            );

            text.append(
                    "علت:\n"
            );

            text.append(
                    "• " +
                    waitReason +
                    "\n\n"
            );
        }

        text.append(
                "━━━━━━━━━━━━━━━━\n"
        );

        text.append(
                "🔎 تریگرهای M5\n\n"
        );

        text.append(
                "Liquidity Sweep صعودی: " +
                yesNo(bullSweep) +
                "\n"
        );

        text.append(
                "Liquidity Sweep نزولی: " +
                yesNo(bearSweep) +
                "\n"
        );

        text.append(
                "Bullish Engulfing: " +
                yesNo(bullEngulf) +
                "\n"
        );

        text.append(
                "Bearish Engulfing: " +
                yesNo(bearEngulf) +
                "\n"
        );

        text.append(
                "Breakout صعودی: " +
                yesNo(bullBreak) +
                "\n"
        );

        text.append(
                "Breakout نزولی: " +
                yesNo(bearBreak) +
                "\n\n"
        );

        text.append(
                "━━━━━━━━━━━━━━━━\n"
        );

        text.append(
                "🧠 دلایل BUY:\n"
        );

        if (buyReasons.isEmpty()) {

            text.append("• ندارد\n");

        } else {

            for (String reason :
                    buyReasons) {

                text.append(
                        "• " +
                        reason +
                        "\n"
                );
            }
        }

        text.append(
                "\n🧠 دلایل SELL:\n"
        );

        if (sellReasons.isEmpty()) {

            text.append("• ندارد\n");

        } else {

            for (String reason :
                    sellReasons) {

                text.append(
                        "• " +
                        reason +
                        "\n"
                );
            }
        }

        text.append(
                "\n━━━━━━━━━━━━━━━━\n"
        );

        text.append(
                "✓ فقط کندل‌های بسته‌شده " +
                "در تحلیل استفاده شده‌اند.\n"
        );

        text.append(
                "✓ کندل در حال تشکیل از محاسبات حذف شده.\n\n"
        );

        text.append(
                "⚠️ این برنامه ابزار تحلیل است؛ " +
                "سیگنال تضمینی معامله نیست."
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
                2.0 / (period + 1);

        double value =
                candles.get(
                        candles.size() - period
                ).close;

        for (
                int i =
                        candles.size() - period + 1;
                i < candles.size();
                i++
        ) {

            value =
                    (
                            (
                                    candles.get(i).close -
                                    value
                            ) * multiplier
                    ) + value;
        }

        return value;
    }

    // =========================================================
    // ATR
    // =========================================================

    double atr(
            ArrayList<Candle> candles,
            int period) {

        if (
                candles.size()
                < period + 1
        )
            return 0;

        double sum = 0;

        int start =
                candles.size() - period;

        for (
                int i = start;
                i < candles.size();
                i++
        ) {

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
    // Swing Structure
    // =========================================================

    int swingStructure(
            ArrayList<Candle> candles) {

        ArrayList<Double> highs =
                new ArrayList<>();

        ArrayList<Double> lows =
                new ArrayList<>();

        int n =
                candles.size();

        /*
         * Swing با دو کندل سمت چپ
         * و دو کندل سمت راست.
         */
        for (
                int i = 2;
                i < n - 2;
                i++
        ) {

            Candle c =
                    candles.get(i);

            boolean swingHigh =
                    c.high >
                    candles.get(i - 1).high &&
                    c.high >
                    candles.get(i - 2).high &&
                    c.high >
                    candles.get(i + 1).high &&
                    c.high >
                    candles.get(i + 2).high;

            boolean swingLow =
                    c.low <
                    candles.get(i - 1).low &&
                    c.low <
                    candles.get(i - 2).low &&
                    c.low <
                    candles.get(i + 1).low &&
                    c.low <
                    candles.get(i + 2).low;

            if (swingHigh)
                highs.add(c.high);

            if (swingLow)
                lows.add(c.low);
        }

        if (
                highs.size() < 2 ||
                lows.size() < 2
        )
            return 0;

        double lastHigh =
                highs.get(
                        highs.size() - 1
                );

        double previousHigh =
                highs.get(
                        highs.size() - 2
                );

        double lastLow =
                lows.get(
                        lows.size() - 1
                );

        double previousLow =
                lows.get(
                        lows.size() - 2
                );

        boolean bullish =
                lastHigh > previousHigh &&
                lastLow > previousLow;

        boolean bearish =
                lastHigh < previousHigh &&
                lastLow < previousLow;

        if (bullish)
            return 1;

        if (bearish)
            return -1;

        return 0;
    }

    // =========================================================
    // Liquidity Sweep
    // =========================================================

    boolean bullishSweep(
            ArrayList<Candle> candles) {

        int n =
                candles.size();

        if (n < 8)
            return false;

        Candle last =
                candles.get(n - 1);

        double previousLow =
                Double.MAX_VALUE;

        for (
                int i = n - 7;
                i < n - 1;
                i++
        ) {

            previousLow =
                    Math.min(
                            previousLow,
                            candles.get(i).low
                    );
        }

        return
                last.low < previousLow &&
                last.close > previousLow;
    }

    boolean bearishSweep(
            ArrayList<Candle> candles) {

        int n =
                candles.size();

        if (n < 8)
            return false;

        Candle last =
                candles.get(n - 1);

        double previousHigh =
                Double.MIN_VALUE;

        for (
                int i = n - 7;
                i < n - 1;
                i++
        ) {

            previousHigh =
                    Math.max(
                            previousHigh,
                            candles.get(i).high
                    );
        }

        return
                last.high > previousHigh &&
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

        Candle previous =
                candles.get(n - 2);

        Candle last =
                candles.get(n - 1);

        return
                previous.close < previous.open &&
                last.close > last.open &&
                last.open <= previous.close &&
                last.close >= previous.open;
    }

    boolean bearishEngulfing(
            ArrayList<Candle> candles) {

        int n =
                candles.size();

        if (n < 2)
            return false;

        Candle previous =
                candles.get(n - 2);

        Candle last =
                candles.get(n - 1);

        return
                previous.close > previous.open &&
                last.close < last.open &&
                last.open >= previous.close &&
                last.close <= previous.open;
    }

    // =========================================================
    // Breakout
    // =========================================================

    boolean bullishBreakout(
            ArrayList<Candle> candles) {

        int n =
                candles.size();

        if (n < 8)
            return false;

        Candle last =
                candles.get(n - 1);

        double highest =
                Double.MIN_VALUE;

        for (
                int i = n - 7;
                i < n - 1;
                i++
        ) {

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

        if (n < 8)
            return false;

        Candle last =
                candles.get(n - 1);

        double lowest =
                Double.MAX_VALUE;

        for (
                int i = n - 7;
                i < n - 1;
                i++
        ) {

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
    // Structure Text
    // =========================================================

    String structureText(
            int structure) {

        if (structure > 0)
            return "BULLISH 🟢";

        if (structure < 0)
            return "BEARISH 🔴";

        return "NEUTRAL 🟡";
    }

    // =========================================================
    // Yes / No
    // =========================================================

    String yesNo(boolean value) {

        return value
                ? "YES ✓"
                : "NO";
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
