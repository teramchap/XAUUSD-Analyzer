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

    /*
     * =====================================================
     * V12 STATE
     * =====================================================
     */

    static final int DIR_NONE = 0;
    static final int DIR_BUY = 1;
    static final int DIR_SELL = -1;

    int activeBreakDirection = DIR_NONE;

    double activeBreakLevel = 0;

    boolean breakoutDetected = false;

    /*
     * =====================================================
     * CANDLE
     * =====================================================
     */

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

    /*
     * =====================================================
     * CREATE
     * =====================================================
     */

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

    /*
     * =====================================================
     * MAIN LOAD
     * =====================================================
     */

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

    /*
     * =====================================================
     * LOAD CANDLES
     * =====================================================
     */

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

                                /*
                                 * فقط کندل‌های بسته‌شده
                                 */

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

    /*
     * =====================================================
     * ANALYSIS V12
     * =====================================================
     */

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

        boolean bullBreakM5 =
                bullishBreakout(m5);

        boolean bearBreakM5 =
                bearishBreakout(m5);

        /*
         * =================================================
         * M15 STRUCTURE BREAK
         * =================================================
         */

        double m15BreakHigh =
                previousHigh(m15,6);

        double m15BreakLow =
                previousLow(m15,6);

        Candle lastM15 =
                m15.get(m15.size()-1);

        boolean m15BullBreak =
                lastM15.close > m15BreakHigh;

        boolean m15BearBreak =
                lastM15.close < m15BreakLow;

        /*
         * ثبت آخرین شکست معتبر
         */

        if (m15BullBreak) {

            breakoutDetected = true;
            activeBreakDirection = DIR_BUY;
            activeBreakLevel = m15BreakHigh;

        } else if (m15BearBreak) {

            breakoutDetected = true;
            activeBreakDirection = DIR_SELL;
            activeBreakLevel = m15BreakLow;
        }

        /*
         * =================================================
         * M5 CONFIRMATION
         * =================================================
         */

        boolean buyConfirmation =
                m5Bull &&
                        (
                                bullSweep ||
                                bullEngulf ||
                                bullBreakM5
                        );

        boolean sellConfirmation =
                m5Bear &&
                        (
                                bearSweep ||
                                bearEngulf ||
                                bearBreakM5
                        );

        /*
         * =================================================
         * PULLBACK
         * =================================================
         */

        boolean buyPullback =
                breakoutDetected &&
                        activeBreakDirection == DIR_BUY &&
                        validBullPullback(
                                m5,
                                activeBreakLevel,
                                atrM5
                        );

        boolean sellPullback =
                breakoutDetected &&
                        activeBreakDirection == DIR_SELL &&
                        validBearPullback(
                                m5,
                                activeBreakLevel,
                                atrM5
                        );

        /*
         * =================================================
         * QUALITY SCORE
         *
         * این عدد احتمال نیست.
         * فقط کیفیت Setup را نشان می‌دهد.
         * =================================================
         */

        int buyQuality = 0;
        int sellQuality = 0;

        ArrayList<String> buyReasons =
                new ArrayList<>();

        ArrayList<String> sellReasons =
                new ArrayList<>();

        /*
         * BUY QUALITY
         */

        if (m15Bull) {

            buyQuality += 20;
            buyReasons.add("M15 صعودی ✓");

        } else {

            buyReasons.add("M15 صعودی نیست ✗");
        }

        if (structureBull) {

            buyQuality += 20;
            buyReasons.add("ساختار M15 صعودی ✓");

        } else {

            buyReasons.add("ساختار M15 هنوز صعودی نیست");
        }

        if (m5Bull) {

            buyQuality += 15;
            buyReasons.add("M5 صعودی ✓");

        } else {

            buyReasons.add("M5 صعودی نیست");
        }

        if (m15BullBreak) {

            buyQuality += 20;
            buyReasons.add("شکست ساختار M15 ✓");

        } else if (
                activeBreakDirection == DIR_BUY
        ) {

            buyQuality += 20;
            buyReasons.add("شکست M15 قبلاً ثبت شده ✓");
        }

        if (buyConfirmation) {

            buyQuality += 15;
            buyReasons.add("تأیید M5 ✓");

        } else {

            buyReasons.add("تأیید M5 هنوز کامل نیست");
        }

        if (buyPullback) {

            buyQuality += 10;
            buyReasons.add("Pullback معتبر ✓");

        } else {

            buyReasons.add("Pullback هنوز تأیید نشده");
        }

        /*
         * SELL QUALITY
         */

        if (m15Bear) {

            sellQuality += 20;
            sellReasons.add("M15 نزولی ✓");

        } else {

            sellReasons.add("M15 نزولی نیست ✗");
        }

        if (structureBear) {

            sellQuality += 20;
            sellReasons.add("ساختار M15 نزولی ✓");

        } else {

            sellReasons.add("ساختار M15 هنوز نزولی نیست");
        }

        if (m5Bear) {

            sellQuality += 15;
            sellReasons.add("M5 نزولی ✓");

        } else {

            sellReasons.add("M5 نزولی نیست");
        }

        if (m15BearBreak) {

            sellQuality += 20;
            sellReasons.add("شکست ساختار M15 ✓");

        } else if (
                activeBreakDirection == DIR_SELL
        ) {

            sellQuality += 20;
            sellReasons.add("شکست M15 قبلاً ثبت شده ✓");
        }

        if (sellConfirmation) {

            sellQuality += 15;
            sellReasons.add("تأیید M5 ✓");

        } else {

            sellReasons.add("تأیید M5 هنوز کامل نیست");
        }

        if (sellPullback) {

            sellQuality += 10;
            sellReasons.add("Pullback معتبر ✓");

        } else {

            sellReasons.add("Pullback هنوز تأیید نشده");
        }

        /*
         * =================================================
         * SETUP STATE
         * =================================================
         */

        String setupState =
                "NO SETUP";

        String signal =
                "WAIT";

        String reason =
                "";

        /*
         * BUY FORMING
         *
         * H1 فقط Context است.
         * بنابراین H1 مخالف به‌تنهایی Setup را باطل نمی‌کند.
         */

        boolean buyContext =
                m15Bull &&
                        (
                                m5Bull ||
                                        structureBull ||
                                        m15BullBreak ||
                                        buyConfirmation
                        );

        boolean sellContext =
                m15Bear &&
                        (
                                m5Bear ||
                                        structureBear ||
                                        m15BearBreak ||
                                        sellConfirmation
                        );

        /*
         * BUY READY
         *
         * شکست M15
         * + M5 confirmation
         * + pullback
         */

        boolean buyReady =
                m15BullBreak ||
                        (
                                activeBreakDirection == DIR_BUY &&
                                        breakoutDetected
                        );

        buyReady =
                buyReady &&
                        buyConfirmation &&
                        buyPullback;

        /*
         * SELL READY
         */

        boolean sellReady =
                m15BearBreak ||
                        (
                                activeBreakDirection == DIR_SELL &&
                                        breakoutDetected
                        );

        sellReady =
                sellReady &&
                        sellConfirmation &&
                        sellPullback;

        /*
         * تصمیم نهایی
         */

        if (buyReady &&
                buyQuality >= 75) {

            signal =
                    "BUY";

            setupState =
                    "BUY READY";

            reason =
                    "شکست M15، تأیید M5 و Pullback تکمیل شده‌اند.";

        } else if (sellReady &&
                sellQuality >= 75) {

            signal =
                    "SELL";

            setupState =
                    "SELL READY";

            reason =
                    "شکست M15، تأیید M5 و Pullback تکمیل شده‌اند.";

        } else if (buyContext) {

            setupState =
                    "BUY SETUP FORMING";

            reason =
                    buyFormingReason(
                            h1Bull,
                            h1Bear,
                            structureBull,
                            m5Bull,
                            m15BullBreak,
                            buyConfirmation,
                            buyPullback
                    );

        } else if (sellContext) {

            setupState =
                    "SELL SETUP FORMING";

            reason =
                    sellFormingReason(
                            h1Bull,
                            h1Bear,
                            structureBear,
                            m5Bear,
                            m15BearBreak,
                            sellConfirmation,
                            sellPullback
                    );

        } else {

            setupState =
                    "NO SETUP";

            reason =
                    "فعلاً جهت و ساختار مناسبی برای تشکیل Setup وجود ندارد.";
        }

        /*
         * =================================================
         * ENTRY / SL / TP
         * فقط READY
         * =================================================
         */

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

        /*
         * =================================================
         * UI
         * =================================================
         */

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

        /*
         * STATUS
         */

        if (signal.equals("BUY")) {

            status.setText(
                    "🟢 BUY READY"
            );

            status.setTextColor(
                    Color.rgb(0,125,70)
            );

        } else if (signal.equals("SELL")) {

            status.setText(
                    "🔴 SELL READY"
            );

            status.setTextColor(
                    Color.rgb(190,30,30)
            );

        } else if (
                setupState.equals(
                        "BUY SETUP FORMING"
                )
        ) {

            status.setText(
                    "🟡 BUY SETUP FORMING"
            );

            status.setTextColor(
                    Color.rgb(170,120,0)
            );

        } else if (
                setupState.equals(
                        "SELL SETUP FORMING"
                )
        ) {

            status.setText(
                    "🟡 SELL SETUP FORMING"
            );

            status.setTextColor(
                    Color.rgb(170,120,0)
            );

        } else {

            status.setText(
                    "⚪ NO SETUP"
            );

            status.setTextColor(
                    Color.rgb(100,100,100)
            );
        }

        /*
         * =================================================
         * ANALYSIS TEXT
         * =================================================
         */

        StringBuilder text =
                new StringBuilder();

        text.append(
                "📊 کیفیت Setup\n\n"
        );

        text.append(
                "BUY QUALITY: " +
                        buyQuality +
                        " / 100\n"
        );

        text.append(
                "SELL QUALITY: " +
                        sellQuality +
                        " / 100\n\n"
        );

        text.append(
                "⚠️ این عدد احتمال موفقیت نیست؛ فقط کیفیت شرایط فعلی Setup است.\n\n"
        );

        text.append(
                "━━━━━━━━━━━━━━━━\n\n"
        );

        text.append(
                "📈 Context بازار\n\n"
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

        /*
         * H1 CONTEXT
         */

        if (setupState.equals(
                "BUY SETUP FORMING"
        )) {

            text.append(
                    "🟡 BUY در حال شکل‌گیری است.\n\n"
            );

            if (h1Bear) {

                text.append(
                        "H1 هنوز Bearish است ⚠\n" +
                                "اما این موضوع به‌تنهایی BUY را باطل نمی‌کند.\n\n"
                );
            }

            text.append(
                    "برای BUY منتظر:\n"
            );

            text.append(
                    "• شکست معتبر ساختار M15\n"
            );

            text.append(
                    "• تأیید M5\n"
            );

            text.append(
                    "• Pullback به ناحیه شکست\n"
            );

            text.append(
                    "• تأیید ورود بعد از Pullback\n\n"
            );
        }

        if (setupState.equals(
                "SELL SETUP FORMING"
        )) {

            text.append(
                    "🟡 SELL در حال شکل‌گیری است.\n\n"
            );

            if (h1Bull) {

                text.append(
                        "H1 هنوز Bullish است ⚠\n" +
                                "اما این موضوع به‌تنهایی SELL را باطل نمی‌کند.\n\n"
                );
            }

            text.append(
                    "برای SELL منتظر:\n"
            );

            text.append(
                    "• شکست معتبر ساختار M15\n"
            );

            text.append(
                    "• تأیید M5\n"
            );

            text.append(
                    "• Pullback به ناحیه شکست\n"
            );

            text.append(
                    "• تأیید ورود بعد از Pullback\n\n"
            );
        }

        /*
         * READY
         */

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
                    "✓ Entry بعد از Breakout + Confirmation + Pullback\n"
            );
        }

        /*
         * REASON
         */

        if (!signal.equals("BUY") &&
                !signal.equals("SELL")) {

            text.append(
                    "علت وضعیت فعلی:\n\n"
            );

            text.append(
                    "• " +
                            reason +
                            "\n\n"
            );
        }

        text.append(
                "━━━━━━━━━━━━━━━━\n\n"
        );

        /*
         * TRIGGERS
         */

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
                "Breakout صعودی M5: " +
                        yesNo(bullBreakM5) +
                        "\n"
        );

        text.append(
                "Breakout نزولی M5: " +
                        yesNo(bearBreakM5) +
                        "\n\n"
        );

        text.append(
                "━━━━━━━━━━━━━━━━\n\n"
        );

        /*
         * M15 BREAK
         */

        text.append(
                "🏗 ساختار و شکست M15\n\n"
        );

        text.append(
                "Bullish Structure Break: " +
                        yesNo(m15BullBreak) +
                        "\n"
        );

        text.append(
                "Bearish Structure Break: " +
                        yesNo(m15BearBreak) +
                        "\n"
        );

        if (breakoutDetected) {

            text.append(
                    String.format(
                            Locale.US,
                            "Break Level: %.2f\n",
                            activeBreakLevel
                    )
            );

            text.append(
                    "Break Direction: " +
                            directionText(
                                    activeBreakDirection
                            ) +
                            "\n"
            );
        }

        text.append("\n");

        /*
         * PULLBACK
         */

        text.append(
                "↩️ Pullback\n\n"
        );

        text.append(
                "BUY Pullback: " +
                        yesNo(buyPullback) +
                        "\n"
        );

        text.append(
                "SELL Pullback: " +
                        yesNo(sellPullback) +
                        "\n\n"
        );

        text.append(
                "━━━━━━━━━━━━━━━━\n\n"
        );

        /*
         * BUY REASONS
         */

        text.append(
                "🧠 کیفیت BUY\n"
        );

        for (String s : buyReasons) {

            text.append(
                    "• " +
                            s +
                            "\n"
            );
        }

        /*
         * SELL REASONS
         */

        text.append(
                "\n🧠 کیفیت SELL\n"
        );

        for (String s : sellReasons) {

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
                "✓ Entry فقط بعد از شکست + تأیید + Pullback صادر می‌شود.\n\n"
        );

        text.append(
                "⚠️ ابزار تحلیل است و تضمین معامله نیست."
        );

        analysis.setText(
                text.toString()
        );
    }

    /*
     * =====================================================
     * FORMING REASONS
     * =====================================================
     */

    String buyFormingReason(
            boolean h1Bull,
            boolean h1Bear,
            boolean structureBull,
            boolean m5Bull,
            boolean m15Break,
            boolean confirmation,
            boolean pullback) {

        if (!m15Break)
            return
                    "M15 صعودی است اما هنوز شکست معتبر ساختار ثبت نشده.";

        if (!confirmation)
            return
                    "شکست M15 دیده شده، اما تأیید M5 هنوز کامل نیست.";

        if (!pullback)
            return
                    "شکست و تأیید انجام شده؛ منتظر Pullback معتبر هستیم.";

        if (h1Bear)
            return
                    "Setup BUY شکل گرفته ولی H1 مخالف است؛ کیفیت پایین‌تر است.";

        return
                "شرایط BUY در حال تکمیل شدن است.";
    }

    String sellFormingReason(
            boolean h1Bull,
            boolean h1Bear,
            boolean structureBear,
            boolean m5Bear,
            boolean m15Break,
            boolean confirmation,
            boolean pullback) {

        if (!m15Break)
            return
                    "M15 نزولی است اما هنوز شکست معتبر ساختار ثبت نشده.";

        if (!confirmation)
            return
                    "شکست M15 دیده شده، اما تأیید M5 هنوز کامل نیست.";

        if (!pullback)
            return
                    "شکست و تأیید انجام شده؛ منتظر Pullback معتبر هستیم.";

        if (h1Bull)
            return
                    "Setup SELL شکل گرفته ولی H1 مخالف است؛ کیفیت پایین‌تر است.";

        return
                "شرایط SELL در حال تکمیل شدن است.";
    }

    /*
     * =====================================================
     * EMA
     * =====================================================
     */

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

    /*
     * =====================================================
     * ATR
     * =====================================================
     */

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

    /*
     * =====================================================
     * SWING STRUCTURE
     * =====================================================
     */

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

    /*
     * =====================================================
     * M15 PREVIOUS HIGH / LOW
     * =====================================================
     */

    double previousHigh(
            ArrayList<Candle> candles,
            int count) {

        int n =
                candles.size();

        int end =
                n - 1;

        int start =
                Math.max(
                        0,
                        end - count
                );

        double high =
                -Double.MAX_VALUE;

        for (
                int i = start;
                i < end;
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

    double previousLow(
            ArrayList<Candle> candles,
            int count) {

        int n =
                candles.size();

        int end =
                n - 1;

        int start =
                Math.max(
                        0,
                        end - count
                );

        double low =
                Double.MAX_VALUE;

        for (
                int i = start;
                i < end;
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

    /*
     * =====================================================
     * LIQUIDITY SWEEP
     * =====================================================
     */

    boolean bullishSweep(
            ArrayList<Candle> candles) {

        int n =
                candles.size();

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

        int n =
                candles.size();

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

    /*
     * =====================================================
     * ENGULFING
     * =====================================================
     */

    boolean bullishEngulfing(
            ArrayList<Candle> candles) {

        int n =
                candles.size();

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

        int n =
                candles.size();

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

    /*
     * =====================================================
     * M5 BREAKOUT
     * =====================================================
     */

    boolean bullishBreakout(
            ArrayList<Candle> candles) {

        int n =
                candles.size();

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

        int n =
                candles.size();

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

    /*
     * =====================================================
     * VALID BULL PULLBACK
     * =====================================================
     */

    boolean validBullPullback(
            ArrayList<Candle> candles,
            double breakLevel,
            double atrValue) {

        if (candles.size() < 3)
            return false;

        Candle last =
                candles.get(
                        candles.size()-1
                );

        double tolerance =
                Math.max(
                        atrValue * 0.25,
                        0.30
                );

        /*
         * قیمت باید به ناحیه شکست برگردد
         * ولی بالای آن تثبیت شود.
         */

        boolean touchedZone =
                last.low <=
                        breakLevel + tolerance;

        boolean stayedAbove =
                last.close >
                        breakLevel;

        return
                touchedZone &&
                        stayedAbove;
    }

    /*
     * =====================================================
     * VALID BEAR PULLBACK
     * =====================================================
     */

    boolean validBearPullback(
            ArrayList<Candle> candles,
            double breakLevel,
            double atrValue) {

        if (candles.size() < 3)
            return false;

        Candle last =
                candles.get(
                        candles.size()-1
                );

        double tolerance =
                Math.max(
                        atrValue * 0.25,
                        0.30
                );

        boolean touchedZone =
                last.high >=
                        breakLevel - tolerance;

        boolean stayedBelow =
                last.close <
                        breakLevel;

        return
                touchedZone &&
                        stayedBelow;
    }

    /*
     * =====================================================
     * RECENT LOW
     * =====================================================
     */

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

    /*
     * =====================================================
     * RECENT HIGH
     * =====================================================
     */

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

    /*
     * =====================================================
     * TEXT
     * =====================================================
     */

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

    String directionText(
            int direction) {

        if (direction == DIR_BUY)
            return "BUY 🟢";

        if (direction == DIR_SELL)
            return "SELL 🔴";

        return "NONE";
    }

    String yesNo(
            boolean value) {

        return value
                ? "YES ✓"
                : "NO";
    }

    /*
     * =====================================================
     * STATUS
     * =====================================================
     */

    void setStatus(
            String text,
            int color,
            String message) {

        status.setText(text);
        status.setTextColor(color);
        analysis.setText(message);
    }
}
