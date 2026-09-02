package com.xau.analyzer;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends Activity {

    // =========================================================
    // CONSTANTS
    // =========================================================

    private static final String PRICE_URL =
            "https://biquote.io/api/XAUUSD?allowStale=false";

    private static final String M5_URL =
            "https://biquote.io/api/XAUUSD/ohlc?interval=5m&limit=200";

    private static final String M15_URL =
            "https://biquote.io/api/XAUUSD/ohlc?interval=15m&limit=200";

    private static final String H1_URL =
            "https://biquote.io/api/XAUUSD/ohlc?interval=1h&limit=200";

    private static final int DIR_NONE = 0;
    private static final int DIR_BUY = 1;
    private static final int DIR_SELL = -1;

    // =========================================================
    // UI
    // =========================================================

    private TextView price;
    private TextView updated;
    private TextView status;
    private TextView statusDot;

    private TextView buyQuality;
    private TextView sellQuality;

    private TextView h1Trend;
    private TextView m15Trend;
    private TextView m5Trend;
    private TextView m15Structure;
    private TextView atrValues;

    private TextView m15Break;
    private TextView pullback;

    private TextView setupState;
    private TextView setupReason;
    private TextView setupWaiting;

    private TextView bullSweep;
    private TextView bearSweep;
    private TextView bullEngulf;
    private TextView bearEngulf;
    private TextView bullBreak;
    private TextView bearBreak;

    private TextView analysis;
    private Button refresh;

    // =========================================================
    // NETWORK
    // =========================================================

    private final OkHttpClient client = new OkHttpClient();

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable autoRefresh = new Runnable() {
        @Override
        public void run() {
            loadAll();
            handler.postDelayed(this, 15000);
        }
    };

    // =========================================================
    // DATA
    // =========================================================

    private final ArrayList<Candle> m5 = new ArrayList<>();
    private final ArrayList<Candle> m15 = new ArrayList<>();
    private final ArrayList<Candle> h1 = new ArrayList<>();

    private double livePrice = 0;

    // =========================================================
    // BREAKOUT STATE
    // =========================================================

    private int activeBreakDirection = DIR_NONE;
    private double activeBreakLevel = 0;
    private long activeBreakTime = 0;

    // =========================================================
    // CANDLE
    // =========================================================

    private static class Candle {

        long openTime;
        double open;
        double high;
        double low;
        double close;

        Candle(long openTime,
               double open,
               double high,
               double low,
               double close) {

            this.openTime = openTime;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
        }
    }

    // =========================================================
    // ON CREATE
    // =========================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        price = findViewById(R.id.price);
        updated = findViewById(R.id.updated);
        status = findViewById(R.id.status);
        statusDot = findViewById(R.id.statusDot);

        buyQuality = findViewById(R.id.buyQuality);
        sellQuality = findViewById(R.id.sellQuality);

        h1Trend = findViewById(R.id.h1Trend);
        m15Trend = findViewById(R.id.m15Trend);
        m5Trend = findViewById(R.id.m5Trend);
        m15Structure = findViewById(R.id.m15Structure);
        atrValues = findViewById(R.id.atrValues);

        m15Break = findViewById(R.id.m15Break);
        pullback = findViewById(R.id.pullback);

        setupState = findViewById(R.id.setupState);
        setupReason = findViewById(R.id.setupReason);
        setupWaiting = findViewById(R.id.setupWaiting);

        bullSweep = findViewById(R.id.bullSweep);
        bearSweep = findViewById(R.id.bearSweep);

        bullEngulf = findViewById(R.id.bullEngulf);
        bearEngulf = findViewById(R.id.bearEngulf);

        bullBreak = findViewById(R.id.bullBreak);
        bearBreak = findViewById(R.id.bearBreak);

        analysis = findViewById(R.id.analysis);
        refresh = findViewById(R.id.refresh);

        refresh.setOnClickListener(v -> loadAll());

        loadAll();

        handler.postDelayed(autoRefresh, 15000);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(autoRefresh);
        super.onDestroy();
    }

    // =========================================================
    // LOAD ALL
    // =========================================================

    private void loadAll() {

        setStatusLoading();

        new Thread(() -> {

            try {

                String priceJson = httpGet(PRICE_URL);
                double p = parsePrice(priceJson);

                String m5Json = httpGet(M5_URL);
                String m15Json = httpGet(M15_URL);
                String h1Json = httpGet(H1_URL);

                ArrayList<Candle> newM5 = parseCandles(m5Json);
                ArrayList<Candle> newM15 = parseCandles(m15Json);
                ArrayList<Candle> newH1 = parseCandles(h1Json);

                if (newM5.size() < 30 ||
                        newM15.size() < 30 ||
                        newH1.size() < 30) {

                    throw new Exception("داده کافی دریافت نشد.");
                }

                runOnUiThread(() -> {

                    livePrice = p;

                    m5.clear();
                    m5.addAll(newM5);

                    m15.clear();
                    m15.addAll(newM15);

                    h1.clear();
                    h1.addAll(newH1);

                    updatePrice();

                    calculateAnalysis();
                });

            } catch (Exception e) {

                runOnUiThread(() -> {

                    status.setText("⚠️ DATA ERROR");
                    status.setTextColor(Color.rgb(180, 70, 70));

                    if (statusDot != null) {
                        statusDot.setText("!");
                        statusDot.setTextColor(Color.WHITE);
                        statusDot.setBackgroundColor(
                                Color.rgb(190, 70, 70)
                        );
                    }

                    setupState.setText(
                            "خطا در دریافت اطلاعات بازار"
                    );

                    setupReason.setText(
                            e.getMessage() == null
                                    ? "دریافت داده ناموفق بود."
                                    : e.getMessage()
                    );

                    setupWaiting.setText(
                            "اتصال اینترنت و منبع قیمت را بررسی کنید."
                    );
                });
            }

        }).start();
    }

    // =========================================================
    // HTTP
    // =========================================================

    private String httpGet(String url) throws IOException {

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = client.newCall(request).execute()) {

            if (!response.isSuccessful()) {
                throw new IOException(
                        "HTTP " + response.code()
                );
            }

            if (response.body() == null) {
                throw new IOException("پاسخ خالی است.");
            }

            return response.body().string();
        }
    }

    // =========================================================
    // PRICE
    // =========================================================

    private double parsePrice(String json) throws Exception {

        JSONObject obj = new JSONObject(json);

        String[] keys = {
                "mid",
                "price",
                "last",
                "bid"
        };

        for (String key : keys) {

            if (obj.has(key) && !obj.isNull(key)) {

                Object value = obj.get(key);

                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }

                return Double.parseDouble(
                        String.valueOf(value)
                );
            }
        }

        if (obj.has("data") && obj.get("data") instanceof JSONObject) {

            JSONObject data = obj.getJSONObject("data");

            for (String key : keys) {

                if (data.has(key)) {

                    Object value = data.get(key);

                    if (value instanceof Number) {
                        return ((Number) value).doubleValue();
                    }

                    return Double.parseDouble(
                            String.valueOf(value)
                    );
                }
            }
        }

        throw new Exception("قیمت XAUUSD در پاسخ پیدا نشد.");
    }

    // =========================================================
    // PARSE CANDLES
    // =========================================================

    private ArrayList<Candle> parseCandles(String json)
            throws Exception {

        ArrayList<Candle> result = new ArrayList<>();

        JSONArray array = null;

        String trimmed = json.trim();

        if (trimmed.startsWith("[")) {

            array = new JSONArray(trimmed);

        } else {

            JSONObject root = new JSONObject(trimmed);

            String[] possibleKeys = {
                    "data",
                    "candles",
                    "bars",
                    "ohlc",
                    "result"
            };

            for (String key : possibleKeys) {

                if (root.has(key)
                        && root.get(key) instanceof JSONArray) {

                    array = root.getJSONArray(key);
                    break;
                }
            }
        }

        if (array == null) {
            throw new Exception("ساختار OHLC قابل شناسایی نیست.");
        }

        for (int i = 0; i < array.length(); i++) {

            JSONObject o = array.getJSONObject(i);

            boolean isOpen = false;

            if (o.has("isOpen")) {
                isOpen = o.optBoolean("isOpen", false);
            }

            // فقط کندل بسته شده
            if (isOpen) {
                continue;
            }

            long time = getLong(o,
                    "openTime",
                    "timestamp",
                    "time",
                    "t");

            double open = getDouble(o,
                    "open",
                    "o");

            double high = getDouble(o,
                    "high",
                    "h");

            double low = getDouble(o,
                    "low",
                    "l");

            double close = getDouble(o,
                    "close",
                    "c");

            if (high <= 0 ||
                    low <= 0 ||
                    open <= 0 ||
                    close <= 0) {
                continue;
            }

            result.add(
                    new Candle(
                            time,
                            open,
                            high,
                            low,
                            close
                    )
            );
        }

        Collections.sort(
                result,
                (a, b) ->
                        Long.compare(
                                a.openTime,
                                b.openTime
                        )
        );

        return result;
    }

    private double getDouble(JSONObject o, String... keys)
            throws Exception {

        for (String key : keys) {

            if (o.has(key) && !o.isNull(key)) {

                Object value = o.get(key);

                if (value instanceof Number) {
                    return ((Number) value).doubleValue();
                }

                return Double.parseDouble(
                        String.valueOf(value)
                );
            }
        }

        throw new Exception(
                "فیلد OHLC پیدا نشد."
        );
    }

    private long getLong(JSONObject o, String... keys)
            throws Exception {

        for (String key : keys) {

            if (o.has(key) && !o.isNull(key)) {

                Object value = o.get(key);

                if (value instanceof Number) {

                    long v = ((Number) value).longValue();

                    // اگر timestamp ثانیه‌ای باشد
                    if (v < 100000000000L) {
                        v *= 1000L;
                    }

                    return v;
                }

                long v = Long.parseLong(
                        String.valueOf(value)
                );

                if (v < 100000000000L) {
                    v *= 1000L;
                }

                return v;
            }
        }

        return 0;
    }

    // =========================================================
    // MAIN ANALYSIS
    // =========================================================

    private void calculateAnalysis() {

        if (m5.size() < 30 ||
                m15.size() < 30 ||
                h1.size() < 30) {
            return;
        }

        // ---------------------------------------------
        // TREND
        // ---------------------------------------------

        boolean h1Bull = trendBull(h1);
        boolean h1Bear = trendBear(h1);

        boolean m15Bull = trendBull(m15);
        boolean m15Bear = trendBear(m15);

        boolean m5Bull = trendBull(m5);
        boolean m5Bear = trendBear(m5);

        // ---------------------------------------------
        // STRUCTURE
        // ---------------------------------------------

        int structure = swingStructure(m15);

        boolean structureBull = structure == DIR_BUY;
        boolean structureBear = structure == DIR_SELL;

        // ---------------------------------------------
        // ATR
        // ---------------------------------------------

        double atrM5 = atr(m5, 14);
        double atrM15 = atr(m15, 14);

        // ---------------------------------------------
        // M5 TRIGGERS
        // ---------------------------------------------

        boolean bullSweep = bullishSweep(m5);
        boolean bearSweep = bearishSweep(m5);

        boolean bullEngulfing = bullishEngulfing(m5);
        boolean bearEngulfing = bearishEngulfing(m5);

        boolean bullBreakM5 = bullishBreakoutM5(m5);
        boolean bearBreakM5 = bearishBreakoutM5(m5);

        boolean bullConfirmation =
                m5Bull &&
                        (bullSweep ||
                                bullEngulfing ||
                                bullBreakM5);

        boolean bearConfirmation =
                m5Bear &&
                        (bearSweep ||
                                bearEngulfing ||
                                bearBreakM5);

        // ---------------------------------------------
        // M15 STRUCTURE BREAK
        // ---------------------------------------------

        BreakInfo breakInfo =
                detectLatestM15Break(m15);

        boolean bullStructureBreak =
                breakInfo.direction == DIR_BUY;

        boolean bearStructureBreak =
                breakInfo.direction == DIR_SELL;

        // ثبت Break فعال
        if (breakInfo.direction != DIR_NONE) {

            activeBreakDirection =
                    breakInfo.direction;

            activeBreakLevel =
                    breakInfo.level;

            activeBreakTime =
                    breakInfo.time;
        }

        // ---------------------------------------------
        // PULLBACK
        // ---------------------------------------------

        boolean buyPullback =
                activeBreakDirection == DIR_BUY
                        && activeBreakTime > 0
                        && validBullPullback(
                        m5,
                        activeBreakLevel,
                        activeBreakTime,
                        atrM5
                );

        boolean sellPullback =
                activeBreakDirection == DIR_SELL
                        && activeBreakTime > 0
                        && validBearPullback(
                        m5,
                        activeBreakLevel,
                        activeBreakTime,
                        atrM5
                );

        // ---------------------------------------------
        // QUALITY
        // ---------------------------------------------

        int buyScore = 0;
        int sellScore = 0;

        // BUY
        if (m15Bull) buyScore += 20;
        if (structureBull) buyScore += 20;
        if (m5Bull) buyScore += 15;
        if (bullStructureBreak) buyScore += 20;
        if (bullConfirmation) buyScore += 15;
        if (buyPullback) buyScore += 10;

        // SELL
        if (m15Bear) sellScore += 20;
        if (structureBear) sellScore += 20;
        if (m5Bear) sellScore += 15;
        if (bearStructureBreak) sellScore += 20;
        if (bearConfirmation) sellScore += 15;
        if (sellPullback) sellScore += 10;

        // ---------------------------------------------
        // READY
        // ---------------------------------------------

        boolean buyReady =
                m15Bull
                        && bullStructureBreak
                        && bullConfirmation
                        && buyPullback
                        && buyScore >= 75;

        boolean sellReady =
                m15Bear
                        && bearStructureBreak
                        && bearConfirmation
                        && sellPullback
                        && sellScore >= 75;

        // ---------------------------------------------
        // FORMING
        // ---------------------------------------------

        boolean buyForming =
                m15Bull &&
                        (
                                m5Bull ||
                                        structureBull ||
                                        bullStructureBreak ||
                                        bullConfirmation ||
                                        buyPullback
                        );

        boolean sellForming =
                m15Bear &&
                        (
                                m5Bear ||
                                        structureBear ||
                                        bearStructureBreak ||
                                        bearConfirmation ||
                                        sellPullback
                        );

        // ---------------------------------------------
        // UI
        // ---------------------------------------------

        updateContextUI(
                h1Bull,
                h1Bear,
                m15Bull,
                m15Bear,
                m5Bull,
                m5Bear,
                structure,
                atrM5,
                atrM15
        );

        updateBreakUI(
                bullStructureBreak,
                bearStructureBreak
        );

        updatePullbackUI(
                buyPullback,
                sellPullback
        );

        updateTriggerUI(
                bullSweep,
                bearSweep,
                bullEngulfing,
                bearEngulfing,
                bullBreakM5,
                bearBreakM5
        );

        buyQuality.setText(
                "BUY QUALITY: " +
                        buyScore +
                        " / 100"
        );

        sellQuality.setText(
                "SELL QUALITY: " +
                        sellScore +
                        " / 100"
        );

        // ---------------------------------------------
        // FINAL STATE
        // ---------------------------------------------

        if (buyReady) {

            showBuyReady(
                    buyScore,
                    atrM5
            );

        } else if (sellReady) {

            showSellReady(
                    sellScore,
                    atrM5
            );

        } else if (buyForming &&
                !sellForming) {

            showBuyForming(
                    buyScore,
                    h1Bear,
                    structureBull,
                    bullConfirmation,
                    buyPullback
            );

        } else if (sellForming &&
                !buyForming) {

            showSellForming(
                    sellScore,
                    h1Bull,
                    structureBear,
                    bearConfirmation,
                    sellPullback
            );

        } else if (buyForming &&
                sellForming) {

            showMixedMarket(
                    buyScore,
                    sellScore
            );

        } else {

            showNoSetup();
        }

        analysis.setText("");
    }

    // =========================================================
    // BUY FORMING
    // =========================================================

    private void showBuyForming(
            int score,
            boolean h1Bear,
            boolean structureBull,
            boolean confirmation,
            boolean pullbackReady) {

        status.setText(
                "BUY SETUP FORMING"
        );

        status.setTextColor(
                Color.rgb(178, 122, 0)
        );

        if (statusDot != null) {

            statusDot.setText("●");
            statusDot.setTextColor(
                    Color.rgb(255, 244, 194)
            );

            statusDot.setBackgroundColor(
                    Color.rgb(255, 208, 74)
            );
        }

        setupState.setText(
                "🟡 BUY در حال شکل‌گیری است."
        );

        StringBuilder reason =
                new StringBuilder();

        if (h1Bear) {

            reason.append(
                    "⚠️ H1 هنوز Bearish است.\n\n"
            );

            reason.append(
                    "اما این موضوع به‌تنهایی BUY را باطل نمی‌کند."
            );

        } else {

            reason.append(
                    "شرایط Context برای BUY در حال تقویت است."
            );
        }

        setupReason.setText(
                reason.toString()
        );

        StringBuilder wait =
                new StringBuilder();

        wait.append("برای BUY منتظر:\n");

        if (!structureBull) {
            wait.append(
                    "• شکست معتبر ساختار M15\n"
            );
        }

        if (!confirmation) {
            wait.append(
                    "• تأیید M5\n"
            );
        }

        if (!pullbackReady) {
            wait.append(
                    "• Pullback به ناحیه شکست\n"
            );
        }

        wait.append(
                "• تأیید ورود بعد از Pullback"
        );

        setupWaiting.setText(
                wait.toString()
        );
    }

    // =========================================================
    // SELL FORMING
    // =========================================================

    private void showSellForming(
            int score,
            boolean h1Bull,
            boolean structureBear,
            boolean confirmation,
            boolean pullbackReady) {

        status.setText(
                "SELL SETUP FORMING"
        );

        status.setTextColor(
                Color.rgb(180, 65, 65)
        );

        if (statusDot != null) {

            statusDot.setText("●");
            statusDot.setTextColor(
                    Color.rgb(255, 220, 220)
            );

            statusDot.setBackgroundColor(
                    Color.rgb(220, 80, 80)
            );
        }

        setupState.setText(
                "🟡 SELL در حال شکل‌گیری است."
        );

        StringBuilder reason =
                new StringBuilder();

        if (h1Bull) {

            reason.append(
                    "⚠️ H1 هنوز Bullish است.\n\n"
            );

            reason.append(
                    "اما این موضوع به‌تنهایی SELL را باطل نمی‌کند."
            );

        } else {

            reason.append(
                    "شرایط Context برای SELL در حال تقویت است."
            );
        }

        setupReason.setText(
                reason.toString()
        );

        StringBuilder wait =
                new StringBuilder();

        wait.append("برای SELL منتظر:\n");

        if (!structureBear) {
            wait.append(
                    "• شکست معتبر ساختار M15\n"
            );
        }

        if (!confirmation) {
            wait.append(
                    "• تأیید M5\n"
            );
        }

        if (!pullbackReady) {
            wait.append(
                    "• Pullback به ناحیه شکست\n"
            );
        }

        wait.append(
                "• تأیید ورود بعد از Pullback"
        );

        setupWaiting.setText(
                wait.toString()
        );
    }

    // =========================================================
    // BUY READY
    // =========================================================

    private void showBuyReady(
            int score,
            double atrM5) {

        status.setText(
                "BUY READY"
        );

        status.setTextColor(
                Color.rgb(20, 145, 80)
        );

        if (statusDot != null) {

            statusDot.setText("●");
            statusDot.setTextColor(
                    Color.rgb(220, 255, 230)
            );

            statusDot.setBackgroundColor(
                    Color.rgb(40, 190, 105)
            );
        }

        setupState.setText(
                "🟢 BUY READY — شرایط ورود تأیید شده است."
        );

        setupReason.setText(
                "شکست ساختار M15 ✓\n" +
                        "تأیید M5 ✓\n" +
                        "Pullback ✓\n" +
                        "تأیید ورود ✓"
        );

        setupWaiting.setText(
                "Entry فقط بعد از تکمیل زنجیره " +
                        "Breakout → Confirmation → Pullback صادر شده است."
        );
    }

    // =========================================================
    // SELL READY
    // =========================================================

    private void showSellReady(
            int score,
            double atrM5) {

        status.setText(
                "SELL READY"
        );

        status.setTextColor(
                Color.rgb(190, 55, 55)
        );

        if (statusDot != null) {

            statusDot.setText("●");
            statusDot.setTextColor(
                    Color.rgb(255, 225, 225)
            );

            statusDot.setBackgroundColor(
                    Color.rgb(215, 65, 65)
            );
        }

        setupState.setText(
                "🔴 SELL READY — شرایط ورود تأیید شده است."
        );

        setupReason.setText(
                "شکست ساختار M15 ✓\n" +
                        "تأیید M5 ✓\n" +
                        "Pullback ✓\n" +
                        "تأیید ورود ✓"
        );

        setupWaiting.setText(
                "Entry فقط بعد از تکمیل زنجیره " +
                        "Breakout → Confirmation → Pullback صادر شده است."
        );
    }

    // =========================================================
    // MIXED
    // =========================================================

    private void showMixedMarket(
            int buyScore,
            int sellScore) {

        status.setText(
                "WAIT — MIXED SETUP"
        );

        status.setTextColor(
                Color.rgb(160, 120, 40)
        );

        if (statusDot != null) {

            statusDot.setText("●");
            statusDot.setTextColor(
                    Color.rgb(255, 244, 194)
            );

            statusDot.setBackgroundColor(
                    Color.rgb(240, 190, 55)
            );
        }

        setupState.setText(
                "🟡 بازار دوطرفه است؛ هنوز Setup برتر مشخص نیست."
        );

        setupReason.setText(
                "BUY QUALITY: " +
                        buyScore +
                        "\nSELL QUALITY: " +
                        sellScore
        );

        setupWaiting.setText(
                "تا زمانی که یک سمت شکست معتبر، " +
                        "تأیید و Pullback مناسب نداشته باشد، Entry صادر نمی‌شود."
        );
    }

    // =========================================================
    // NO SETUP
    // =========================================================

    private void showNoSetup() {

        status.setText(
                "NO SETUP"
        );

        status.setTextColor(
                Color.rgb(100, 100, 100)
        );

        if (statusDot != null) {

            statusDot.setText("●");
            statusDot.setTextColor(
                    Color.WHITE
            );

            statusDot.setBackgroundColor(
                    Color.rgb(150, 150, 150)
            );
        }

        setupState.setText(
                "⚪ فعلاً Setup معتبری در حال شکل‌گیری نیست."
        );

        setupReason.setText(
                "شرایط فعلی برای تشکیل یک Setup مشخص کافی نیست."
        );

        setupWaiting.setText(
                "منتظر شکل‌گیری Context و ساختار معتبر بازار باشید."
        );
    }

    // =========================================================
    // CONTEXT UI
    // =========================================================

    private void updateContextUI(
            boolean h1Bull,
            boolean h1Bear,
            boolean m15Bull,
            boolean m15Bear,
            boolean m5Bull,
            boolean m5Bear,
            int structure,
            double atrM5,
            double atrM15) {

        h1Trend.setText(
                "H1: " +
                        trendText(h1Bull, h1Bear)
        );

        m15Trend.setText(
                "M15: " +
                        trendText(m15Bull, m15Bear)
        );

        m5Trend.setText(
                "M5: " +
                        trendText(m5Bull, m5Bear)
        );

        m15Structure.setText(
                structureText(structure)
        );

        atrValues.setText(
                "ATR M5: " +
                        fmt(atrM5) +
                        "\nATR M15: " +
                        fmt(atrM15)
        );
    }

    private String trendText(
            boolean bull,
            boolean bear) {

        if (bull) {
            return "BULLISH 🟢";
        }

        if (bear) {
            return "BEARISH 🔴";
        }

        return "NEUTRAL 🟡";
    }

    private String structureText(
            int structure) {

        if (structure == DIR_BUY) {
            return "🟢 ساختار M15: BULLISH";
        }

        if (structure == DIR_SELL) {
            return "🔴 ساختار M15: BEARISH";
        }

        return "🟡 ساختار M15: NEUTRAL";
    }

    // =========================================================
    // BREAK UI
    // =========================================================

    private void updateBreakUI(
            boolean bull,
            boolean bear) {

        m15Break.setText(
                "Bullish Structure Break: " +
                        (bull ? "YES ✓" : "NO") +
                        "\n" +
                        "Bearish Structure Break: " +
                        (bear ? "YES ✓" : "NO")
        );
    }

    // =========================================================
    // PULLBACK UI
    // =========================================================

    private void updatePullbackUI(
            boolean buy,
            boolean sell) {

        pullback.setText(
                "BUY Pullback: " +
                        (buy ? "YES ✓" : "NO") +
                        "\n" +
                        "SELL Pullback: " +
                        (sell ? "YES ✓" : "NO")
        );
    }

    // =========================================================
    // TRIGGER UI
    // =========================================================

    private void updateTriggerUI(
            boolean bullSweepValue,
            boolean bearSweepValue,
            boolean bullEngulfValue,
            boolean bearEngulfValue,
            boolean bullBreakValue,
            boolean bearBreakValue) {

        bullSweep.setText(
                "Liquidity Sweep صعودی  " +
                        (bullSweepValue ? "YES ✓" : "NO")
        );

        bearSweep.setText(
                "Liquidity Sweep نزولی  " +
                        (bearSweepValue ? "YES ✓" : "NO")
        );

        bullEngulf.setText(
                "Bullish Engulfing  " +
                        (bullEngulfValue ? "YES ✓" : "NO")
        );

        bearEngulf.setText(
                "Bearish Engulfing  " +
                        (bearEngulfValue ? "YES ✓" : "NO")
        );

        bullBreak.setText(
                "Breakout صعودی M5  " +
                        (bullBreakValue ? "YES ✓" : "NO")
        );

        bearBreak.setText(
                "Breakout نزولی M5  " +
                        (bearBreakValue ? "YES ✓" : "NO")
        );
    }

    // =========================================================
    // LOADING
    // =========================================================

    private void setStatusLoading() {

        status.setText(
                "⏳ ANALYZING..."
        );

        status.setTextColor(
                Color.rgb(120, 120, 120)
        );

        setupState.setText(
                "در حال دریافت اطلاعات بازار..."
        );

        setupReason.setText(
                "OHLC تایم‌فریم‌های M5 / M15 / H1 در حال دریافت است."
        );

        setupWaiting.setText(
                "لطفاً چند لحظه صبر کنید."
        );
    }

    // =========================================================
    // PRICE UI
    // =========================================================

    private void updatePrice() {

        price.setText(
                String.format(
                        Locale.US,
                        "%.2f",
                        livePrice
                )
        );

        SimpleDateFormat sdf =
                new SimpleDateFormat(
                        "HH:mm:ss",
                        Locale.US
                );

        sdf.setTimeZone(
                TimeZone.getDefault()
        );

        updated.setText(
                sdf.format(
                        new Date()
                )
        );
    }

    // =========================================================
    // TREND
    // =========================================================

    private boolean trendBull(
            ArrayList<Candle> candles) {

        double ema20 =
                ema(candles, 20);

        double ema50 =
                ema(candles, 50);

        return ema20 > ema50;
    }

    private boolean trendBear(
            ArrayList<Candle> candles) {

        double ema20 =
                ema(candles, 20);

        double ema50 =
                ema(candles, 50);

        return ema20 < ema50;
    }

    // =========================================================
    // EMA
    // =========================================================

    private double ema(
            ArrayList<Candle> candles,
            int period) {

        if (candles.size() < period) {
            return 0;
        }

        double multiplier =
                2.0 / (period + 1);

        double ema =
                candles
                        .get(candles.size() - period)
                        .close;

        for (int i =
             candles.size() - period + 1;
             i < candles.size();
             i++) {

            ema =
                    (candles.get(i).close - ema)
                            * multiplier
                            + ema;
        }

        return ema;
    }

    // =========================================================
    // ATR
    // =========================================================

    private double atr(
            ArrayList<Candle> candles,
            int period) {

        if (candles.size() < period + 1) {
            return 0;
        }

        double sum = 0;

        int start =
                candles.size() - period;

        for (int i = start; i < candles.size(); i++) {

            Candle current =
                    candles.get(i);

            Candle previous =
                    candles.get(i - 1);

            double tr =
                    Math.max(
                            current.high - current.low,
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
    // SWING STRUCTURE
    // =========================================================

    private int swingStructure(
            ArrayList<Candle> candles) {

        if (candles.size() < 15) {
            return DIR_NONE;
        }

        List<Double> highs =
                new ArrayList<>();

        List<Double> lows =
                new ArrayList<>();

        int start =
                Math.max(
                        2,
                        candles.size() - 50
                );

        int end =
                candles.size() - 2;

        for (int i = start; i <= end; i++) {

            Candle c =
                    candles.get(i);

            if (c.high >
                    candles.get(i - 1).high &&
                    c.high >
                    candles.get(i - 2).high &&
                    c.high >
                    candles.get(i + 1).high &&
                    c.high >
                    candles.get(i + 2).high) {

                highs.add(c.high);
            }

            if (c.low <
                    candles.get(i - 1).low &&
                    c.low <
                    candles.get(i - 2).low &&
                    c.low <
                    candles.get(i + 1).low &&
                    c.low <
                    candles.get(i + 2).low) {

                lows.add(c.low);
            }
        }

        if (highs.size() < 2 ||
                lows.size() < 2) {

            return DIR_NONE;
        }

        double lastHigh =
                highs.get(highs.size() - 1);

        double previousHigh =
                highs.get(highs.size() - 2);

        double lastLow =
                lows.get(lows.size() - 1);

        double previousLow =
                lows.get(lows.size() - 2);

        boolean bullish =
                lastHigh > previousHigh &&
                        lastLow > previousLow;

        boolean bearish =
                lastHigh < previousHigh &&
                        lastLow < previousLow;

        if (bullish) {
            return DIR_BUY;
        }

        if (bearish) {
            return DIR_SELL;
        }

        return DIR_NONE;
    }

    // =========================================================
    // M5 SWEEP
    // =========================================================

    private boolean bullishSweep(
            ArrayList<Candle> candles) {

        if (candles.size() < 6) {
            return false;
        }

        Candle last =
                candles.get(candles.size() - 1);

        double previousLow =
                recentLow(
                        candles,
                        5,
                        1
                );

        return last.low < previousLow &&
                last.close > previousLow;
    }

    private boolean bearishSweep(
            ArrayList<Candle> candles) {

        if (candles.size() < 6) {
            return false;
        }

        Candle last =
                candles.get(candles.size() - 1);

        double previousHigh =
                recentHigh(
                        candles,
                        5,
                        1
                );

        return last.high > previousHigh &&
                last.close < previousHigh;
    }

    // =========================================================
    // ENGULFING
    // =========================================================

    private boolean bullishEngulfing(
            ArrayList<Candle> candles) {

        if (candles.size() < 3) {
            return false;
        }

        Candle prev =
                candles.get(
                        candles.size() - 2
                );

        Candle last =
                candles.get(
                        candles.size() - 1
                );

        boolean prevBear =
                prev.close < prev.open;

        boolean lastBull =
                last.close > last.open;

        return prevBear &&
                lastBull &&
                last.open <= prev.close &&
                last.close >= prev.open;
    }

    private boolean bearishEngulfing(
            ArrayList<Candle> candles) {

        if (candles.size() < 3) {
            return false;
        }

        Candle prev =
                candles.get(
                        candles.size() - 2
                );

        Candle last =
                candles.get(
                        candles.size() - 1
                );

        boolean prevBull =
                prev.close > prev.open;

        boolean lastBear =
                last.close < last.open;

        return prevBull &&
                lastBear &&
                last.open >= prev.close &&
                last.close <= prev.open;
    }

    // =========================================================
    // M5 BREAKOUT
    // =========================================================

    private boolean bullishBreakoutM5(
            ArrayList<Candle> candles) {

        if (candles.size() < 8) {
            return false;
        }

        Candle last =
                candles.get(
                        candles.size() - 1
                );

        double level =
                previousHigh(
                        candles,
                        6,
                        1
                );

        return last.close > level;
    }

    private boolean bearishBreakoutM5(
            ArrayList<Candle> candles) {

        if (candles.size() < 8) {
            return false;
        }

        Candle last =
                candles.get(
                        candles.size() - 1
                );

        double level =
                previousLow(
                        candles,
                        6,
                        1
                );

        return last.close < level;
    }

    // =========================================================
    // M15 BREAK INFO
    // =========================================================

    private static class BreakInfo {

        int direction;
        double level;
        long time;

        BreakInfo(
                int direction,
                double level,
                long time) {

            this.direction = direction;
            this.level = level;
            this.time = time;
        }
    }

    // =========================================================
    // M15 BREAKOUT DETECTION
    // =========================================================

    private BreakInfo detectLatestM15Break(
            ArrayList<Candle> candles) {

        if (candles.size() < 10) {

            return new BreakInfo(
                    DIR_NONE,
                    0,
                    0
            );
        }

        int last =
                candles.size() - 1;

        Candle c =
                candles.get(last);

        double highLevel =
                previousHigh(
                        candles,
                        6,
                        1
                );

        double lowLevel =
                previousLow(
                        candles,
                        6,
                        1
                );

        if (c.close > highLevel) {

            return new BreakInfo(
                    DIR_BUY,
                    highLevel,
                    c.openTime
            );
        }

        if (c.close < lowLevel) {

            return new BreakInfo(
                    DIR_SELL,
                    lowLevel,
                    c.openTime
            );
        }

        return new BreakInfo(
                DIR_NONE,
                0,
                0
        );
    }

    // =========================================================
    // PREVIOUS HIGH
    // =========================================================

    private double previousHigh(
            ArrayList<Candle> candles,
            int count,
            int offset) {

        int end =
                candles.size() - 1 - offset;

        int start =
                Math.max(
                        0,
                        end - count + 1
                );

        double highest =
                Double.NEGATIVE_INFINITY;

        for (int i = start; i <= end; i++) {

            highest =
                    Math.max(
                            highest,
                            candles.get(i).high
                    );
        }

        return highest;
    }

    // =========================================================
    // PREVIOUS LOW
    // =========================================================

    private double previousLow(
            ArrayList<Candle> candles,
            int count,
            int offset) {

        int end =
                candles.size() - 1 - offset;

        int start =
                Math.max(
                        0,
                        end - count + 1
                );

        double lowest =
                Double.POSITIVE_INFINITY;

        for (int i = start; i <= end; i++) {

            lowest =
                    Math.min(
                            lowest,
                            candles.get(i).low
                    );
        }

        return lowest;
    }

    // =========================================================
    // PULLBACK BUY
    // =========================================================

    private boolean validBullPullback(
            ArrayList<Candle> candles,
            double breakLevel,
            long breakTime,
            double atrM5) {

        if (candles.size() < 3) {
            return false;
        }

        double tolerance =
                Math.max(
                        atrM5 * 0.25,
                        0.30
                );

        for (int i =
             candles.size() - 1;
             i >= 0;
             i--) {

            Candle c =
                    candles.get(i);

            // Pullback باید بعد از Break رخ دهد
            if (c.openTime <= breakTime) {
                continue;
            }

            boolean touched =
                    c.low <=
                            breakLevel + tolerance;

            boolean reclaimed =
                    c.close > breakLevel;

            if (touched && reclaimed) {
                return true;
            }
        }

        return false;
    }

    // =========================================================
    // PULLBACK SELL
    // =========================================================

    private boolean validBearPullback(
            ArrayList<Candle> candles,
            double breakLevel,
            long breakTime,
            double atrM5) {

        if (candles.size() < 3) {
            return false;
        }

        double tolerance =
                Math.max(
                        atrM5 * 0.25,
                        0.30
                );

        for (int i =
             candles.size() - 1;
             i >= 0;
             i--) {

            Candle c =
                    candles.get(i);

            if (c.openTime <= breakTime) {
                continue;
            }

            boolean touched =
                    c.high >=
                            breakLevel - tolerance;

            boolean rejected =
                    c.close < breakLevel;

            if (touched && rejected) {
                return true;
            }
        }

        return false;
    }

    // =========================================================
    // RECENT HIGH
    // =========================================================

    private double recentHigh(
            ArrayList<Candle> candles,
            int count,
            int offset) {

        return previousHigh(
                candles,
                count,
                offset
        );
    }

    // =========================================================
    // RECENT LOW
    // =========================================================

    private double recentLow(
            ArrayList<Candle> candles,
            int count,
            int offset) {

        return previousLow(
                candles,
                count,
                offset
        );
    }

    // =========================================================
    // FORMAT
    // =========================================================

    private String fmt(double value) {

        return String.format(
                Locale.US,
                "%.2f",
                value
        );
    }
}
