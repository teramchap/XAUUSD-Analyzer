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

    interface CandleCallback {
        void onResult(ArrayList<Candle> candles);
    }

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

            handler.postDelayed(
                    timer,
                    15000
            );
        };

        handler.postDelayed(
                timer,
                15000
        );
    }

    // =====================================================
    // MAIN LOAD
    // =====================================================

    void loadAll() {

        setStatus(
                "🟡 ANALYZING",
                Color.rgb(170,120,0),
                "در حال دریافت اطلاعات بازار..."
        );

        Request request =
                new Request.Builder()
                        .url(
                                "https://biquote.io/api/XAUUSD?allowStale=false"
                        )
                        .header(
                                "Accept",
                                "application/json"
                        )
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
                                        Color.rgb(190,30,30),
                                        "خطا در اتصال به فید:\n\n" +
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
                                            Color.rgb(190,30,30),
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

                            if (livePrice <= 0)
                                throw new Exception(
                                        "قیمت معتبر دریافت نشد."
                                );

                            runOnUiThread(
                                    MainActivity.this::loadCandles
                            );

                        } catch (Exception e) {

                            runOnUiThread(() ->
                                    setStatus(
                                            "🔴 JSON ERROR",
                                            Color.rgb(190,30,30),
                                            e.getMessage()
                                    )
                            );
                        }
                    }
                }
        );
    }

    // =====================================================
    // LOAD CANDLES
    // =====================================================

    void loadCandles() {

        loadTimeframe(
                "5m",
                result5 -> {

                    m5 = result5;

                    loadTimeframe(
                            "15m",
                            result15 -> {

                                m15 = result15;

                                loadTimeframe(
                                        "1h",
                                        resultH1 -> {

                                            h1 = resultH1;

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
                        .header(
                                "Accept",
                                "application/json"
                        )
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
                                        Color.rgb(190,30,30),
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
                                            Color.rgb(190,30,30),
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

                                boolean isOpen =
                                        b.optBoolean(
                                                "isOpen",
                                                false
                                        );

                                // فقط کندل بسته‌شده
                                if (isOpen)
                                    continue;

                                candles.add(
                                        new Candle(
                                                b.getDouble("open"),
                                                b.getDouble("high"),
                                                b.getDouble("low"),
                                                b.getDouble("close")
                                        )
                                );
                            }

                            if (candles.size() < 70)
                                throw new Exception(
                                        "داده کافی برای " +
                                        interval +
                                        " وجود ندارد."
                                );

                            callback.onResult(
                                    candles
                            );

                        } catch (Exception e) {

                            runOnUiThread(() ->
                                    setStatus(
                                            "🔴 OHLC ERROR",
                                            Color.rgb(190,30,30),
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

    // =====================================================
    // ANALYSIS
    // =====================================================

    void calculateAnalysis() {

        double h1Ema20 = ema(h1,20);
        double h1Ema50 = ema(h1,50);

        double m15Ema20 = ema(m15,20);
        double m15Ema50 = ema(m15,50);

        double m5Ema20 = ema(m5,20);
        double m5Ema50 = ema(m5,50);

        double atrM5 = atr(m5,14);
        double atrM15 = atr(m15,14);

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

        int structure =
                swingStructure(m15);

        boolean structureBull =
                structure > 0;

        boolean structureBear =
                structure < 0;

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

        // =================================================
        // SCORES
        // =================================================

        int buyScore = 0;
        int sellScore = 0;

        ArrayList<String> buyReasons =
                new ArrayList<>();

        ArrayList<String> sellReasons =
                new ArrayList<>();

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

        // =================================================
        // TRIGGERS
        // =================================================

        boolean buyTrigger =
                bullSweep ||
                bullEngulf ||
                bullBreak;

        boolean sellTrigger =
                bearSweep ||
                bearEngulf ||
                bearBreak;

        String signal = "WAIT";

        String setupState =
                "NO SETUP";

        String reason = "";

        // =================================================
        // BUY SETUP
        // =================================================

        if (h1Bull && m15Bull) {

            setupState =
                    "BUY SETUP IN PROGRESS";

            if (!structureBull) {

                reason =
                        "ساختار M15 هنوز صعودی تأیید نشده.";

            } else if (!m5Bull) {

                reason =
                        "M5 هنوز صعودی نشده.";

            } else if (!buyTrigger) {

                reason =
                        "تریگر ورود BUY روی M5 وجود ندارد.";

            } else if (buyScore >= 70) {

                signal = "BUY";

                reason =
                        "تمام شروط BUY تأیید شده‌اند.";

            } else {

                reason =
                        "امتیاز BUY هنوز کافی نیست.";
            }
        }

        // =================================================
        // SELL SETUP
        // =================================================

        else if (h1Bear && m15Bear) {

            setupState =
                    "SELL SETUP IN PROGRESS";

            if (!structureBear) {

                reason =
                        "ساختار M15 هنوز نزولی تأیید نشده.";

            } else if (!m5Bear) {

                reason =
                        "M5 هنوز نزولی نشده.";

            } else if (!sellTrigger) {

                reason =
                        "تریگر ورود SELL روی M5 وجود ندارد.";

            } else if (sellScore >= 70) {

                signal = "SELL";

                reason =
                        "تمام شروط SELL تأیید شده‌اند.";

            } else {

                reason =
                        "امتیاز SELL هنوز کافی نیست.";
            }
        }

        else {

            setupState =
                    "NO SETUP";

            reason =
                    "H1 و M15 هم‌جهت نیستند.";
        }

        // =================================================
        // ENTRY / SL / TP
        // =================================================

        double entry = 0;
        double sl = 0;
        double tp1 = 0;
        double tp2 = 0;
        double tp3 = 0;

        if (signal.equals("BUY")) {

            entry =
                    m5.get(m5.size()-1).close;

            double recentLow =
                    recentLow(m5,5);

            double atrStop =
                    entry -
                    atrM5 * 1.5;

            sl =
                    Math.min(
                            recentLow,
                            atrStop
                    );

            double risk =
                    entry - sl;

            tp1 =
                    entry + risk;

            tp2 =
                    entry + risk * 2;

            tp3 =
                    entry + risk * 3;
        }

        if (signal.equals("SELL")) {

            entry =
                    m5.get(m5.size()-1).close;

            double recentHigh =
                    recentHigh(m5,5);

            double atrStop =
                    entry +
                    atrM5 * 1.5;

            sl =
                    Math.max(
                            recentHigh,
                            atrStop
                    );

            double risk =
                    sl - entry;

            tp1 =
                    entry - risk;

            tp2 =
                    entry - risk * 2;

            tp3 =
                    entry - risk * 3;
        }

        // =================================================
        // UI
        // =================================================

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
                ).format(new Date())
        );

        if (signal.equals("BUY")) {

            status.setText("🟢 BUY SETUP");

            status.setTextColor(
                    Color.rgb(0,125,70)
            );

        } else if (signal.equals("SELL")) {

            status.setText("🔴 SELL SETUP");

            status.setTextColor(
                    Color.rgb(190,30,30)
            );

        } else {

            status.setText("🟡 WAIT");

            status.setTextColor(
                    Color.rgb(170,120,0)
            );
        }

        StringBuilder text =
                new StringBuilder();

        text.append(
                "📊 امتیازها\n\n"
        );

        text.append(
                "BUY: " +
                buyScore +
                " / 100\n"
        );

        text.append(
                "SELL: " +
                sellScore +
                " / 100\n\n"
        );

        text.append(
                "━━━━━━━━━━━━━━━━\n\n"
        );

        text.append(
                "📈 جهت بازار\n\n"
        );

        text.append(
                "H1: " +
                trend(h1Ema20,h1Ema50) +
                "\n"
        );

        text.append(
                "M15: " +
                trend(m15Ema20,m15Ema50) +
                "\n"
        );

        text.append(
                "M5: " +
                trend(m5Ema20,m5Ema50) +
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
                "━━━━━━━━━━━━━━━━\n\n"
        );

        text.append(
                "🎯 وضعیت Setup\n\n"
        );

        text.append(
                setupState +
                "\n\n"
        );

        text.append(
                "Signal: " +
                signal +
                "\n\n"
        );

        if (signal.equals("BUY") ||
                signal.equals("SELL")) {

            text.append(
                    "🔥 ورود تأیید شد\n\n"
            );

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
                    "R:R\n" +
                    "TP1 = 1:1\n" +
                    "TP2 = 1:2\n" +
                    "TP3 = 1:3\n\n"
            );

            text.append(
                    "✓ Entry بر اساس آخرین " +
                    "کندل بسته‌شده M5\n"
            );

        } else {

            text.append(
                    "⛔ ورود هنوز تأیید نشده.\n\n"
            );

            text.append(
                    "علت:\n"
            );

            text.append(
                    "• " +
                    reason +
                    "\n\n"
            );

            if (
                    setupState.equals(
                            "BUY SETUP IN PROGRESS"
                    )
            ) {

                text.append(
                        "برای BUY منتظر:\n" +
                        "• تأیید ساختار M15\n" +
                        "• M5 صعودی\n" +
                        "• Sweep / Engulfing / Breakout\n\n"
                );
            }

            if (
                    setupState.equals(
                            "SELL SETUP IN PROGRESS"
                    )
            ) {

                text.append(
                        "برای SELL منتظر:\n" +
                        "• تأیید ساختار M15\n" +
                        "• M5 نزولی\n" +
                        "• Sweep / Engulfing / Breakout\n\n"
                );
            }
        }

        text.append(
                "━━━━━━━━━━━━━━━━\n\n"
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
                "━━━━━━━━━━━━━━━━\n\n"
        );

        text.append(
                "🧠 دلایل BUY\n"
        );

        if (buyReasons.isEmpty()) {

            text.append("• ندارد\n");

        } else {

            for (String s : buyReasons)
                text.append(
                        "• " +
                        s +
                        "\n"
                );
        }

        text.append(
                "\n🧠 دلایل SELL\n"
        );

        if (sellReasons.isEmpty()) {

            text.append("• ندارد\n");

        } else {

            for (String s : sellReasons)
                text.append(
                        "• " +
                        s +
                        "\n"
                );
        }

        text.append(
                "\n━━━━━━━━━━━━━━━━\n\n"
        );

        text.append(
                "✓ فقط کندل‌های بسته‌شده تحلیل شده‌اند.\n"
        );

        text.append(
                "✓ کندل در حال تشکیل حذف شده است.\n"
        );

        text.append(
                "✓ قیمت Entry از کندل بسته‌شده M5 گرفته می‌شود.\n\n"
        );

        text.append(
                "⚠️ ابزار تحلیل است و تضمین معامله نیست."
        );

        analysis.setText(
                text.toString()
        );
    }

    // =====================================================
    // EMA
    // =====================================================

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
                            )
                            *
                            multiplier
                    )
                    +
                    value;
        }

        return value;
    }

    // =====================================================
    // ATR
    // =====================================================

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
                    candles.get(i-1);

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

    // =====================================================
    // SWING STRUCTURE
    // =====================================================

    int swingStructure(
            ArrayList<Candle> candles) {

        ArrayList<Double> highs =
                new ArrayList<>();

        ArrayList<Double> lows =
                new ArrayList<>();

        for (
                int i = 2;
                i < candles.size()-2;
                i++
        ) {

            Candle c =
                    candles.get(i);

            boolean high =
                    c.high >
                    candles.get(i-1).high &&
                    c.high >
                    candles.get(i-2).high &&
                    c.high >
                    candles.get(i+1).high &&
                    c.high >
                    candles.get(i+2).high;

            boolean low =
                    c.low <
                    candles.get(i-1).low &&
                    c.low <
                    candles.get(i-2).low &&
                    c.low <
                    candles.get(i+1).low &&
                    c.low <
                    candles.get(i+2).low;

            if (high)
                highs.add(c.high);

            if (low)
                lows.add(c.low);
        }

        if (
                highs.size() < 2 ||
                lows.size() < 2
        )
            return 0;

        double h1 =
                highs.get(
                        highs.size()-2
                );

        double h2 =
                highs.get(
                        highs.size()-1
                );

        double l1 =
                lows.get(
                        lows.size()-2
                );

        double l2 =
                lows.get(
                        lows.size()-1
                );

        if (
                h2 > h1 &&
                l2 > l1
        )
            return 1;

        if (
                h2 < h1 &&
                l2 < l1
        )
            return -1;

        return 0;
    }

    // =====================================================
    // LIQUIDITY SWEEP
    // =====================================================

    boolean bullishSweep(
            ArrayList<Candle> candles) {

        int n = candles.size();

        if (n < 8)
            return false;

        Candle last =
                candles.get(n-1);

        double low =
                Double.MAX_VALUE;

        for (
                int i=n-7;
                i<n-1;
                i++
        ) {

            low =
                    Math.min(
                            low,
                            candles.get(i).low
                    );
        }

        return
                last.low < low &&
                last.close > low;
    }

    boolean bearishSweep(
            ArrayList<Candle> candles) {

        int n = candles.size();

        if (n < 8)
            return false;

        Candle last =
                candles.get(n-1);

        double high =
                -Double.MAX_VALUE;

        for (
                int i=n-7;
                i<n-1;
                i++
        ) {

            high =
                    Math.max(
                            high,
                            candles.get(i).high
                    );
        }

        return
                last.high > high &&
                last.close < high;
    }

    // =====================================================
    // ENGULFING
    // =====================================================

    boolean bullishEngulfing(
            ArrayList<Candle> candles) {

        int n = candles.size();

        if (n < 2)
            return false;

        Candle previous =
                candles.get(n-2);

        Candle last =
                candles.get(n-1);

        return
                previous.close <
                previous.open &&

                last.close >
                last.open &&

                last.open <=
                previous.close &&

                last.close >=
                previous.open;
    }

    boolean bearishEngulfing(
            ArrayList<Candle> candles) {

        int n = candles.size();

        if (n < 2)
            return false;

        Candle previous =
                candles.get(n-2);

        Candle last =
                candles.get(n-1);

        return
                previous.close >
                previous.open &&

                last.close <
                last.open &&

                last.open >=
                previous.close &&

                last.close <=
                previous.open;
    }

    // =====================================================
    // BREAKOUT
    // =====================================================

    boolean bullishBreakout(
            ArrayList<Candle> candles) {

        int n = candles.size();

        if (n < 8)
            return false;

        Candle last =
                candles.get(n-1);

        double high =
                -Double.MAX_VALUE;

        for (
                int i=n-7;
                i<n-1;
                i++
        ) {

            high =
                    Math.max(
                            high,
                            candles.get(i).high
                    );
        }

        return last.close > high;
    }

    boolean bearishBreakout(
            ArrayList<Candle> candles) {

        int n = candles.size();

        if (n < 8)
            return false;

        Candle last =
                candles.get(n-1);

        double low =
                Double.MAX_VALUE;

        for (
                int i=n-7;
                i<n-1;
                i++
        ) {

            low =
                    Math.min(
                            low,
                            candles.get(i).low
                    );
        }

        return last.close < low;
    }

    // =====================================================
    // RECENT HIGH / LOW
    // =====================================================

    double recentLow(
            ArrayList<Candle> candles,
            int count) {

        int start =
                Math.max(
                        0,
                        candles.size()-count
                );

        double low =
                Double.MAX_VALUE;

        for (
                int i=start;
                i<candles.size();
                i++
        ) {

            low =
                    Math.min(
                            low,
                            candles.get(i).low
                    );
        }

        return low;
    }

    double recentHigh(
            ArrayList<Candle> candles,
            int count) {

        int start =
                Math.max(
                        0,
                        candles.size()-count
                );

        double high =
                -Double.MAX_VALUE;

        for (
                int i=start;
                i<candles.size();
                i++
        ) {

            high =
                    Math.max(
                            high,
                            candles.get(i).high
                    );
        }

        return high;
    }

    // =====================================================
    // TEXT
    // =====================================================

    String trend(
            double ema20,
            double ema50) {

        if (ema20 > ema50)
            return "BULLISH 🟢";

        if (ema20 < ema50)
            return "BEARISH 🔴";

        return "NEUTRAL 🟡";
    }

    String structureText(
            int structure) {

        if (structure > 0)
            return "BULLISH 🟢";

        if (structure < 0)
            return "BEARISH 🔴";

        return "NEUTRAL 🟡";
    }

    String yesNo(boolean value) {

        return value
                ? "YES ✓"
                : "NO";
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
