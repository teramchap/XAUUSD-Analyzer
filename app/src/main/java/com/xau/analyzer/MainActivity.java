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

    private static final String APP_VERSION = "V18";

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

    // Trade plan: only actionable when Entry is confirmed.
    private TextView tradePlan;
    private TextView tradePlanNote;

    private TextView bullSweep;
    private TextView bearSweep;
    private TextView bullEngulf;
    private TextView bearEngulf;
    private TextView bullBreak;
    private TextView bearBreak;

    private TextView analysis;
    private Button refresh;

    private View analysisPage, chartPage, tabAnalysis, tabChart;
    private XauChartView xauChart;
    private double chartEntry=0, chartSl=0, chartTp1=0, chartTp2=0, chartTp3=0;
    private boolean chartReady=false;
    private String chartStage="در انتظار تحلیل...";

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

    public static class CandleProxy {
        public long openTime; public double open, high, low, close;
        public CandleProxy(long t,double o,double h,double l,double c){openTime=t;open=o;high=h;low=l;close=c;}
    }

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

        tradePlan = findViewById(R.id.tradePlan);
        tradePlanNote = findViewById(R.id.tradePlanNote);

        bullSweep = findViewById(R.id.bullSweep);
        bearSweep = findViewById(R.id.bearSweep);

        bullEngulf = findViewById(R.id.bullEngulf);
        bearEngulf = findViewById(R.id.bearEngulf);

        bullBreak = findViewById(R.id.bullBreak);
        bearBreak = findViewById(R.id.bearBreak);

        analysis = findViewById(R.id.analysis);
        refresh = findViewById(R.id.refresh);
        analysisPage = findViewById(R.id.analysisPage);
        chartPage = findViewById(R.id.chartPage);
        tabAnalysis = findViewById(R.id.tabAnalysis);
        tabChart = findViewById(R.id.tabChart);
        xauChart = findViewById(R.id.xauChart);

        tabAnalysis.setOnClickListener(v -> showTab(true));
        tabChart.setOnClickListener(v -> showTab(false));
        showTab(true);

        refresh.setOnClickListener(v -> loadAll());

        loadAll();

        handler.postDelayed(autoRefresh, 15000);
    }

    private void showTab(boolean analysisSelected) {
        analysisPage.setVisibility(analysisSelected ? View.VISIBLE : View.GONE);
        chartPage.setVisibility(analysisSelected ? View.GONE : View.VISIBLE);
        tabAnalysis.setBackgroundColor(analysisSelected ? Color.rgb(80,80,80) : Color.rgb(238,238,238));
        tabAnalysis.setTextColor(analysisSelected ? Color.WHITE : Color.rgb(85,85,85));
        tabChart.setBackgroundColor(analysisSelected ? Color.rgb(238,238,238) : Color.rgb(80,80,80));
        tabChart.setTextColor(analysisSelected ? Color.rgb(85,85,85) : Color.WHITE);
        if (!analysisSelected && xauChart != null) xauChart.invalidate();
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

                    try {
                        calculateAnalysis();
                    } catch (Exception analysisError) {
                        showAnalysisError(analysisError);
                    }
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
    // ANALYSIS ERROR
    // =========================================================

    private void showAnalysisError(Exception e) {

        status.setText("⚠️ ANALYSIS ERROR");
        status.setTextColor(Color.rgb(180, 70, 70));

        if (statusDot != null) {
            statusDot.setText("!");
            statusDot.setTextColor(Color.WHITE);
            statusDot.setBackgroundColor(Color.rgb(190, 70, 70));
        }

        setupState.setText("خطا در تحلیل داده‌های بازار");

        String message = e.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = e.getClass().getSimpleName();
        }
        setupReason.setText(message);
        setupWaiting.setText(
                "برنامه بسته نمی‌شود؛ داده بعدی در به‌روزرسانی بعدی دوباره تحلیل خواهد شد."
        );
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

                String textValue = String.valueOf(value).trim();

                // پشتیبانی از timestamp عددی
                try {
                    long v = Long.parseLong(textValue);

                    // اگر timestamp ثانیه‌ای باشد
                    if (v < 100000000000L) {
                        v *= 1000L;
                    }

                    return v;

                } catch (NumberFormatException ignored) {
                    // اگر عدد نبود، ISO-8601 را امتحان می‌کنیم
                }

                // نمونه: 2026-09-02T13:00:00Z
                try {
                    SimpleDateFormat iso =
                            new SimpleDateFormat(
                                    "yyyy-MM-dd'T'HH:mm:ssX",
                                    Locale.US
                            );

                    iso.setTimeZone(TimeZone.getTimeZone("UTC"));

                    Date date = iso.parse(textValue);

                    if (date != null) {
                        return date.getTime();
                    }

                } catch (Exception ignored) {
                    // فرمت زمانی ناشناخته است
                }

                throw new Exception(
                        "فرمت timestamp قابل شناسایی نیست: " + textValue
                );
            }
        }

        return 0;
    }

    // =========================================================
    // MAIN ANALYSIS
    // =========================================================

    private void calculateAnalysis() {

        if (m5.size() < 30 || m15.size() < 30 || h1.size() < 30) {
            return;
        }

        // =====================================================
        // 1) MARKET CONTEXT
        // =====================================================
        boolean h1Bull = trendBull(h1);
        boolean h1Bear = trendBear(h1);

        boolean m15Bull = trendBull(m15);
        boolean m15Bear = trendBear(m15);

        boolean m5Bull = trendBull(m5);
        boolean m5Bear = trendBear(m5);

        int structure = swingStructure(m15);
        boolean structureBull = structure == DIR_BUY;
        boolean structureBear = structure == DIR_SELL;

        double atrM5 = atr(m5, 14);
        double atrM15 = atr(m15, 14);

        // =====================================================
        // 2) DETECT NEW M15 BREAK
        // =====================================================
        BreakInfo breakInfo = detectLatestM15Break(m15);

        if (breakInfo.direction != DIR_NONE) {
            boolean isNewBreak =
                    breakInfo.direction != activeBreakDirection ||
                    breakInfo.time != activeBreakTime ||
                    Math.abs(breakInfo.level - activeBreakLevel) > Math.max(atrM15 * 0.05, 0.10);

            if (isNewBreak) {
                activeBreakDirection = breakInfo.direction;
                activeBreakLevel = breakInfo.level;
                activeBreakTime = breakInfo.time;
            }
        }

        // =====================================================
        // 3) BREAKOUT LIFECYCLE
        // =====================================================
        boolean breakActive = isActiveBreakValid(m15, atrM15);

        if (!breakActive) {
            activeBreakDirection = DIR_NONE;
            activeBreakLevel = 0;
            activeBreakTime = 0;
        }

        // =====================================================
        // 4) M5 CONFIRMATION MUST HAPPEN AFTER ACTIVE BREAK
        // =====================================================
        long confirmationTime = 0;
        boolean bullConfirmation = false;
        boolean bearConfirmation = false;

        if (breakActive && activeBreakDirection == DIR_BUY) {
            confirmationTime = latestBullConfirmationTime(m5, activeBreakTime);
            bullConfirmation = confirmationTime > 0;
        }

        if (breakActive && activeBreakDirection == DIR_SELL) {
            confirmationTime = latestBearConfirmationTime(m5, activeBreakTime);
            bearConfirmation = confirmationTime > 0;
        }

        // =====================================================
        // 5) PULLBACK AFTER CONFIRMATION
        // =====================================================
        long pullbackTime = 0;
        boolean buyPullback = false;
        boolean sellPullback = false;

        if (breakActive &&
                activeBreakDirection == DIR_BUY &&
                bullConfirmation) {

            pullbackTime = latestBullPullbackTime(
                    m5,
                    activeBreakLevel,
                    confirmationTime,
                    atrM5
            );

            buyPullback = pullbackTime > 0;
        }

        if (breakActive &&
                activeBreakDirection == DIR_SELL &&
                bearConfirmation) {

            pullbackTime = latestBearPullbackTime(
                    m5,
                    activeBreakLevel,
                    confirmationTime,
                    atrM5
            );

            sellPullback = pullbackTime > 0;
        }

        // =====================================================
        // 6) ENTRY CONFIRMATION AFTER PULLBACK
        // =====================================================
        boolean buyEntryConfirmed =
                buyPullback &&
                entryConfirmedAfterBullPullback(
                        m5,
                        pullbackTime
                );

        boolean sellEntryConfirmed =
                sellPullback &&
                entryConfirmedAfterBearPullback(
                        m5,
                        pullbackTime
                );

        // =====================================================
        // 7) QUALITY — SETUP QUALITY, NOT WIN PROBABILITY
        // =====================================================
        int buyScore = 0;
        int sellScore = 0;

        if (m15Bull) buyScore += 20;
        if (structureBull) buyScore += 20;
        if (m5Bull) buyScore += 15;
        if (activeBreakDirection == DIR_BUY) buyScore += 20;
        if (bullConfirmation) buyScore += 10;
        if (buyPullback) buyScore += 10;
        if (buyEntryConfirmed) buyScore += 5;

        if (m15Bear) sellScore += 20;
        if (structureBear) sellScore += 20;
        if (m5Bear) sellScore += 15;
        if (activeBreakDirection == DIR_SELL) sellScore += 20;
        if (bearConfirmation) sellScore += 10;
        if (sellPullback) sellScore += 10;
        if (sellEntryConfirmed) sellScore += 5;

        // =====================================================
        // 8) READY = FULL CHAIN
        // Setup -> M15 Break -> M5 Confirmation -> Pullback -> Entry
        // =====================================================
        boolean buyReady =
                breakActive &&
                activeBreakDirection == DIR_BUY &&
                m15Bull &&
                bullConfirmation &&
                buyPullback &&
                buyEntryConfirmed &&
                buyScore >= 75;

        boolean sellReady =
                breakActive &&
                activeBreakDirection == DIR_SELL &&
                m15Bear &&
                bearConfirmation &&
                sellPullback &&
                sellEntryConfirmed &&
                sellScore >= 75;

        // =====================================================
        // 9) FORMING
        // =====================================================
        boolean buyForming =
                (m15Bull || structureBull || activeBreakDirection == DIR_BUY) &&
                !buyReady;

        boolean sellForming =
                (m15Bear || structureBear || activeBreakDirection == DIR_SELL) &&
                !sellReady;

        // =====================================================
        // 10) UI
        // =====================================================
        updateContextUI(
                h1Bull, h1Bear,
                m15Bull, m15Bear,
                m5Bull, m5Bear,
                structure,
                atrM5,
                atrM15
        );

        updateBreakUI(
                activeBreakDirection == DIR_BUY,
                activeBreakDirection == DIR_SELL
        );

        updatePullbackUI(
                buyPullback,
                sellPullback
        );

        updateTriggerUI(
                activeBreakDirection == DIR_BUY && latestBullSweepAfter(m5, activeBreakTime),
                activeBreakDirection == DIR_SELL && latestBearSweepAfter(m5, activeBreakTime),
                activeBreakDirection == DIR_BUY && latestBullEngulfAfter(m5, activeBreakTime),
                activeBreakDirection == DIR_SELL && latestBearEngulfAfter(m5, activeBreakTime),
                activeBreakDirection == DIR_BUY && latestBullBreakAfter(m5, activeBreakTime),
                activeBreakDirection == DIR_SELL && latestBearBreakAfter(m5, activeBreakTime)
        );

        buyQuality.setText("BUY QUALITY: " + buyScore + " / 100");
        sellQuality.setText("SELL QUALITY: " + sellScore + " / 100");

        // =====================================================
        // 11) FINAL DECISION
        // =====================================================
        chartReady = false; chartEntry=chartSl=chartTp1=chartTp2=chartTp3=0;
        if (buyReady) {
            showBuyReady(buyScore, atrM5, pullbackTime);
        } else if (sellReady) {
            showSellReady(sellScore, atrM5, pullbackTime);
        } else if (buyForming && !sellForming) {
            showBuyForming(
                    buyScore,
                    h1Bear,
                    structureBull,
                    bullConfirmation,
                    buyPullback
            );
            showWaitingTradePlan(
                    DIR_BUY,
                    activeBreakLevel,
                    confirmationTime,
                    pullbackTime,
                    buyEntryConfirmed
            );
        } else if (sellForming && !buyForming) {
            showSellForming(
                    sellScore,
                    h1Bull,
                    structureBear,
                    bearConfirmation,
                    sellPullback
            );
            showWaitingTradePlan(
                    DIR_SELL,
                    activeBreakLevel,
                    confirmationTime,
                    pullbackTime,
                    sellEntryConfirmed
            );
        } else if (buyForming && sellForming) {
            showMixedMarket(buyScore, sellScore);
            showWaitingTradePlan(
                    DIR_NONE,
                    activeBreakLevel,
                    confirmationTime,
                    pullbackTime,
                    false
            );
        } else {
            showNoSetup();
            showWaitingTradePlan(
                    DIR_NONE,
                    0,
                    0,
                    0,
                    false
            );
        }

        analysis.setText("");

        updateChart(directionForChart(buyReady, sellReady, activeBreakDirection), activeBreakLevel,
                confirmationTime, pullbackTime, buyEntryConfirmed || sellEntryConfirmed, buyReady || sellReady,
                atrM5);
    }

    private int directionForChart(boolean buyReady, boolean sellReady, int active) {
        if (buyReady) return DIR_BUY;
        if (sellReady) return DIR_SELL;
        return active;
    }

    private void updateChart(int dir, double breakLevel, long confirmationTime, long pullbackTime,
                             boolean entryConfirmed, boolean ready, double atrM5) {
        if (xauChart == null || m5.isEmpty()) return;
        double ztol = Math.max(atrM5 * 0.25, 0.30);
        double zl = breakLevel > 0 ? breakLevel - ztol : 0;
        double zh = breakLevel > 0 ? breakLevel + ztol : 0;
        String stage;
        if (dir == DIR_NONE) stage = "NO ACTIONABLE TRADE";
        else if (ready) stage = dir == DIR_BUY ? "BUY READY" : "SELL READY";
        else if (confirmationTime <= 0) stage = dir == DIR_BUY ? "BUY · WAIT CONFIRMATION" : "SELL · WAIT CONFIRMATION";
        else if (pullbackTime <= 0) stage = dir == DIR_BUY ? "BUY · WAIT PULLBACK" : "SELL · WAIT PULLBACK";
        else if (!entryConfirmed) stage = dir == DIR_BUY ? "BUY · WAIT ENTRY" : "SELL · WAIT ENTRY";
        else stage = dir == DIR_BUY ? "BUY · ENTRY CONFIRMED" : "SELL · ENTRY CONFIRMED";
        ArrayList<CandleProxy> list = new ArrayList<>();
        for (Candle q : m5) list.add(new CandleProxy(q.openTime,q.open,q.high,q.low,q.close));
        xauChart.setData(list, livePrice, dir, breakLevel, zl, zh, chartReady, chartEntry, chartSl, chartTp1, chartTp2, chartTp3, stage);
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
            double atrM5,
            long pullbackTime) {

        status.setText("BUY READY");
        status.setTextColor(Color.rgb(20, 145, 80));

        if (statusDot != null) {
            statusDot.setText("●");
            statusDot.setTextColor(Color.rgb(220, 255, 230));
            statusDot.setBackgroundColor(Color.rgb(40, 190, 105));
        }

        setupState.setText(
                "🟢 BUY READY — شرایط ورود تأیید شده است."
        );

        double entry = m5.get(m5.size() - 1).close;
        double pullbackLow = pullbackExtremeLow(m5, pullbackTime);

        if (pullbackLow <= 0) {
            pullbackLow = recentLow(m5, 5, 0);
        }

        double slByPullback =
                pullbackLow - Math.max(atrM5 * 0.20, 0.30);

        double slByAtr =
                entry - atrM5 * 1.50;

        double sl = Math.min(slByPullback, slByAtr);
        double risk = entry - sl;

        if (risk <= 0 || Double.isNaN(risk)) {
            risk = Math.max(atrM5, 0.30);
            sl = entry - risk;
        }

        double tp1 = entry + risk;
        double tp2 = entry + risk * 2.0;
        double tp3 = entry + risk * 3.0;

        chartReady = true; chartEntry=entry; chartSl=sl; chartTp1=tp1; chartTp2=tp2; chartTp3=tp3;

        setupReason.setText(
                "M15 Breakout ✓\n" +
                "M5 Confirmation ✓\n" +
                "Pullback ✓\n" +
                "Entry Confirmation ✓"
        );

        setupWaiting.setText(
                "BUY READY | Quality: " + score + "/100\n" +
                "ورود بر اساس آخرین Close کندل بسته M5 پس از تأیید Pullback."
        );

        showTradePlan(
                DIR_BUY,
                entry,
                sl,
                tp1,
                tp2,
                tp3,
                risk,
                score
        );
    }

    private void showSellReady(
            int score,
            double atrM5,
            long pullbackTime) {

        status.setText("SELL READY");
        status.setTextColor(Color.rgb(190, 55, 55));

        if (statusDot != null) {
            statusDot.setText("●");
            statusDot.setTextColor(Color.rgb(255, 225, 225));
            statusDot.setBackgroundColor(Color.rgb(215, 65, 65));
        }

        setupState.setText(
                "🔴 SELL READY — شرایط ورود تأیید شده است."
        );

        double entry = m5.get(m5.size() - 1).close;
        double pullbackHigh = pullbackExtremeHigh(m5, pullbackTime);

        if (pullbackHigh <= 0) {
            pullbackHigh = recentHigh(m5, 5, 0);
        }

        double slByPullback =
                pullbackHigh + Math.max(atrM5 * 0.20, 0.30);

        double slByAtr =
                entry + atrM5 * 1.50;

        double sl = Math.max(slByPullback, slByAtr);
        double risk = sl - entry;

        if (risk <= 0 || Double.isNaN(risk)) {
            risk = Math.max(atrM5, 0.30);
            sl = entry + risk;
        }

        double tp1 = entry - risk;
        double tp2 = entry - risk * 2.0;
        double tp3 = entry - risk * 3.0;

        chartReady = true; chartEntry=entry; chartSl=sl; chartTp1=tp1; chartTp2=tp2; chartTp3=tp3;

        setupReason.setText(
                "M15 Breakout ✓\n" +
                "M5 Confirmation ✓\n" +
                "Pullback ✓\n" +
                "Entry Confirmation ✓"
        );

        setupWaiting.setText(
                "SELL READY | Quality: " + score + "/100\n" +
                "ورود بر اساس آخرین Close کندل بسته M5 پس از تأیید Pullback."
        );

        showTradePlan(
                DIR_SELL,
                entry,
                sl,
                tp1,
                tp2,
                tp3,
                risk,
                score
        );
    }

    private void showTradePlan(
            int direction,
            double entry,
            double sl,
            double tp1,
            double tp2,
            double tp3,
            double risk,
            int score) {

        if (tradePlan == null || tradePlanNote == null) return;

        if (direction == DIR_BUY) {
            tradePlan.setText(
                    "🟢 BUY TRADE PLAN\n\n" +
                    "ENTRY      " + fmt(entry) + "\n" +
                    "STOP LOSS  " + fmt(sl) + "\n" +
                    "TP1        " + fmt(tp1) + "\n" +
                    "TP2        " + fmt(tp2) + "\n" +
                    "TP3        " + fmt(tp3) + "\n\n" +
                    "RISK       " + fmt(risk) + "\n" +
                    "R:R        1:1  |  1:2  |  1:3"
            );
        } else {
            tradePlan.setText(
                    "🔴 SELL TRADE PLAN\n\n" +
                    "ENTRY      " + fmt(entry) + "\n" +
                    "STOP LOSS  " + fmt(sl) + "\n" +
                    "TP1        " + fmt(tp1) + "\n" +
                    "TP2        " + fmt(tp2) + "\n" +
                    "TP3        " + fmt(tp3) + "\n\n" +
                    "RISK       " + fmt(risk) + "\n" +
                    "R:R        1:1  |  1:2  |  1:3"
            );
        }

        tradePlanNote.setText(
                "✓ زنجیره کامل شد: Breakout → Confirmation → Pullback → Entry\n" +
                "Quality: " + score + "/100 — این عدد احتمال برد نیست."
        );

        tradePlan.setVisibility(View.VISIBLE);
        tradePlanNote.setVisibility(View.VISIBLE);
    }

    private void showWaitingTradePlan(
            int direction,
            double breakLevel,
            long confirmationTime,
            long pullbackTime,
            boolean entryConfirmed) {

        if (tradePlan == null || tradePlanNote == null) return;

        tradePlan.setVisibility(View.VISIBLE);
        tradePlanNote.setVisibility(View.VISIBLE);

        String title;

        if (direction == DIR_BUY) {
            if (confirmationTime <= 0) {
                title = "🟠 BUY — WAIT FOR CONFIRMATION";
            } else if (pullbackTime <= 0) {
                title = "🟠 BUY — WAIT FOR PULLBACK";
            } else if (!entryConfirmed) {
                title = "🟡 BUY — PULLBACK IN ZONE / WAIT FOR ENTRY";
            } else {
                title = "🔵 BUY — ENTRY CONFIRMED";
            }
        } else if (direction == DIR_SELL) {
            if (confirmationTime <= 0) {
                title = "🟠 SELL — WAIT FOR CONFIRMATION";
            } else if (pullbackTime <= 0) {
                title = "🟠 SELL — WAIT FOR PULLBACK";
            } else if (!entryConfirmed) {
                title = "🟡 SELL — PULLBACK IN ZONE / WAIT FOR ENTRY";
            } else {
                title = "🔵 SELL — ENTRY CONFIRMED";
            }
        } else {
            title = "⚪ NO ACTIONABLE TRADE";
        }

        StringBuilder b = new StringBuilder();
        b.append(title).append("\n\n");

        if (direction != DIR_NONE && breakLevel > 0) {
            b.append("BREAK LEVEL      ").append(fmt(breakLevel)).append("\n");

            // The pullback zone is intentionally provisional: it is a watch
            // area around the broken level, not an entry signal.
            double zoneTolerance = Math.max(atrM5 * 0.25, 0.30);
            double zoneLow = breakLevel - zoneTolerance;
            double zoneHigh = breakLevel + zoneTolerance;

            b.append("PULLBACK ZONE    ")
                    .append(fmt(zoneLow))
                    .append(" – ")
                    .append(fmt(zoneHigh))
                    .append("\n");
        }

        if (confirmationTime > 0) {
            b.append("M5 CONFIRMATION   ✓\n");
        } else if (direction != DIR_NONE) {
            b.append("M5 CONFIRMATION   ✗\n");
        }

        if (pullbackTime > 0) {
            b.append("PULLBACK          ✓\n");
        } else if (direction != DIR_NONE) {
            b.append("PULLBACK          ✗\n");
        }

        if (entryConfirmed) {
            b.append("ENTRY CONFIRMATION ✓");
        } else if (direction != DIR_NONE) {
            b.append("ENTRY CONFIRMATION ✗");
        } else {
            b.append("ورود فعلاً مجاز نیست.");
        }

        tradePlan.setText(b.toString());

        if (direction == DIR_NONE) {
            tradePlanNote.setText(
                    "تا زمانی که Setup معتبر + Breakout + Confirmation + Pullback + Entry Confirmation کامل نشود، معامله‌ای صادر نمی‌شود."
            );
        } else if (entryConfirmed) {
            tradePlanNote.setText(
                    "Entry Confirmation تشکیل شده؛ اگر شرایط READY نشد، کیفیت یا Context هنوز حداقل لازم را ندارد."
            );
        } else if (pullbackTime > 0) {
            tradePlanNote.setText(
                    "قیمت به ناحیه Pullback رسیده؛ فعلاً ورود ممنوع است. منتظر تأیید نهایی M5 برای Entry هستیم."
            );
        } else if (confirmationTime > 0) {
            tradePlanNote.setText(
                    "Breakout و Confirmation انجام شده؛ حالا فقط منتظر برگشت قیمت به ناحیه Pullback هستیم. ورود فعلاً ممنوع است."
            );
        } else {
            tradePlanNote.setText(
                    "Breakout فعال است؛ هنوز Confirmation معتبر M5 نداریم. فعلاً هیچ ورودی مجاز نیست."
            );
        }
    }

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
    // SWING STRUCTURE — V13
    // =========================================================

    private static class SwingPoint {
        int index;
        double price;
        boolean high;

        SwingPoint(int index, double price, boolean high) {
            this.index = index;
            this.price = price;
            this.high = high;
        }
    }

    private ArrayList<SwingPoint> confirmedSwings(
            ArrayList<Candle> candles) {

        ArrayList<SwingPoint> swings = new ArrayList<>();

        // 2-left / 2-right confirmation prevents the newest
        // unfinished candle structure from being treated as a swing.
        int start = Math.max(2, candles.size() - 80);
        int end = candles.size() - 3; // last 2 candles must exist after pivot

        for (int i = start; i <= end; i++) {
            Candle c = candles.get(i);

            boolean isHigh =
                    c.high > candles.get(i - 1).high &&
                    c.high >= candles.get(i - 2).high &&
                    c.high > candles.get(i + 1).high &&
                    c.high >= candles.get(i + 2).high;

            boolean isLow =
                    c.low < candles.get(i - 1).low &&
                    c.low <= candles.get(i - 2).low &&
                    c.low < candles.get(i + 1).low &&
                    c.low <= candles.get(i + 2).low;

            if (isHigh) {
                swings.add(new SwingPoint(i, c.high, true));
            }

            if (isLow) {
                swings.add(new SwingPoint(i, c.low, false));
            }
        }

        swings.sort((a, b) -> Integer.compare(a.index, b.index));
        return swings;
    }

    private int swingStructure(
            ArrayList<Candle> candles) {

        ArrayList<SwingPoint> swings = confirmedSwings(candles);

        ArrayList<SwingPoint> highs = new ArrayList<>();
        ArrayList<SwingPoint> lows = new ArrayList<>();

        for (SwingPoint s : swings) {
            if (s.high) highs.add(s);
            else lows.add(s);
        }

        if (highs.size() < 2 || lows.size() < 2) {
            return DIR_NONE;
        }

        SwingPoint lastHigh = highs.get(highs.size() - 1);
        SwingPoint prevHigh = highs.get(highs.size() - 2);
        SwingPoint lastLow = lows.get(lows.size() - 1);
        SwingPoint prevLow = lows.get(lows.size() - 2);

        boolean bullish =
                lastHigh.price > prevHigh.price &&
                lastLow.price > prevLow.price;

        boolean bearish =
                lastHigh.price < prevHigh.price &&
                lastLow.price < prevLow.price;

        if (bullish) return DIR_BUY;
        if (bearish) return DIR_SELL;
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
    // M15 BREAKOUT DETECTION — V13 REAL SWING BREAK
    // =========================================================

    private BreakInfo detectLatestM15Break(
            ArrayList<Candle> candles) {

        if (candles.size() < 20) {
            return new BreakInfo(DIR_NONE, 0, 0);
        }

        ArrayList<SwingPoint> swings = confirmedSwings(candles);
        if (swings.size() < 4) {
            return new BreakInfo(DIR_NONE, 0, 0);
        }

        double atrM15 = atr(candles, 14);
        double buffer = Math.max(atrM15 * 0.10, 0.20);

        int last = candles.size() - 1;
        int firstScan = Math.max(1, last - 12);

        // Find the latest confirmed swing high/low that existed BEFORE
        // each candidate break candle. A break requires a candle CLOSE
        // beyond that actual pivot, not merely a wick through it.
        for (int i = last; i >= firstScan; i--) {
            Candle c = candles.get(i);
            Candle prev = candles.get(i - 1);

            double priorHigh = Double.NaN;
            double priorLow = Double.NaN;

            for (int j = swings.size() - 1; j >= 0; j--) {
                SwingPoint sp = swings.get(j);
                if (sp.index >= i - 2) continue;

                if (Double.isNaN(priorHigh) && sp.high) {
                    priorHigh = sp.price;
                }

                if (Double.isNaN(priorLow) && !sp.high) {
                    priorLow = sp.price;
                }

                if (!Double.isNaN(priorHigh) && !Double.isNaN(priorLow)) {
                    break;
                }
            }

            if (!Double.isNaN(priorHigh) &&
                    prev.close <= priorHigh &&
                    c.close > priorHigh + buffer) {

                return new BreakInfo(
                        DIR_BUY,
                        priorHigh,
                        c.openTime
                );
            }

            if (!Double.isNaN(priorLow) &&
                    prev.close >= priorLow &&
                    c.close < priorLow - buffer) {

                return new BreakInfo(
                        DIR_SELL,
                        priorLow,
                        c.openTime
                );
            }
        }

        return new BreakInfo(DIR_NONE, 0, 0);
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
    // STATE MACHINE HELPERS — V14
    // =========================================================

    private boolean isActiveBreakValid(
            ArrayList<Candle> candles,
            double atrM15) {

        if (activeBreakDirection == DIR_NONE ||
                activeBreakTime <= 0 ||
                activeBreakLevel <= 0) {
            return false;
        }

        int breakIndex = -1;

        for (int i = 0; i < candles.size(); i++) {
            if (candles.get(i).openTime == activeBreakTime) {
                breakIndex = i;
                break;
            }
        }

        if (breakIndex < 0) return false;

        // A break is allowed to live for 8 closed M15 candles (~2 hours).
        if (candles.size() - 1 - breakIndex > 8) {
            return false;
        }

        double invalidationBuffer =
                Math.max(atrM15 * 0.50, 0.50);

        // Invalidate only BEFORE a valid pullback has reclaimed/rejected
        // the level. This prevents a stale breakout from producing a trade.
        for (int i = breakIndex + 1; i < candles.size(); i++) {
            Candle c = candles.get(i);

            if (activeBreakDirection == DIR_BUY &&
                    c.close < activeBreakLevel - invalidationBuffer) {
                return false;
            }

            if (activeBreakDirection == DIR_SELL &&
                    c.close > activeBreakLevel + invalidationBuffer) {
                return false;
            }
        }

        return true;
    }

    private long latestBullConfirmationTime(
            ArrayList<Candle> candles,
            long afterTime) {

        long t = 0;

        for (int i = 1; i < candles.size(); i++) {
            Candle c = candles.get(i);
            if (c.openTime <= afterTime) continue;

            Candle prev = candles.get(i - 1);

            boolean sweep =
                    c.low < prev.low &&
                    c.close > prev.low;

            boolean engulf =
                    prev.close < prev.open &&
                    c.close > c.open &&
                    c.open <= prev.close &&
                    c.close >= prev.open;

            boolean breakout =
                    c.close > previousHigh(candles, 6, candles.size() - i);

            if ((sweep || engulf || breakout) && c.close > c.open) {
                t = c.openTime;
            }
        }

        return t;
    }

    private long latestBearConfirmationTime(
            ArrayList<Candle> candles,
            long afterTime) {

        long t = 0;

        for (int i = 1; i < candles.size(); i++) {
            Candle c = candles.get(i);
            if (c.openTime <= afterTime) continue;

            Candle prev = candles.get(i - 1);

            boolean sweep =
                    c.high > prev.high &&
                    c.close < prev.high;

            boolean engulf =
                    prev.close > prev.open &&
                    c.close < c.open &&
                    c.open >= prev.close &&
                    c.close <= prev.open;

            boolean breakout =
                    c.close < previousLow(candles, 6, candles.size() - i);

            if ((sweep || engulf || breakout) && c.close < c.open) {
                t = c.openTime;
            }
        }

        return t;
    }

    private long latestBullPullbackTime(
            ArrayList<Candle> candles,
            double breakLevel,
            long afterTime,
            double atrM5) {

        double tolerance = Math.max(atrM5 * 0.25, 0.30);
        long result = 0;

        for (Candle c : candles) {
            if (c.openTime <= afterTime) continue;

            if (c.low <= breakLevel + tolerance &&
                    c.close > breakLevel) {
                result = c.openTime;
            }
        }

        return result;
    }

    private long latestBearPullbackTime(
            ArrayList<Candle> candles,
            double breakLevel,
            long afterTime,
            double atrM5) {

        double tolerance = Math.max(atrM5 * 0.25, 0.30);
        long result = 0;

        for (Candle c : candles) {
            if (c.openTime <= afterTime) continue;

            if (c.high >= breakLevel - tolerance &&
                    c.close < breakLevel) {
                result = c.openTime;
            }
        }

        return result;
    }

    private boolean entryConfirmedAfterBullPullback(
            ArrayList<Candle> candles,
            long pullbackTime) {

        int p = indexByTime(candles, pullbackTime);
        if (p < 0 || p >= candles.size() - 1) return false;

        Candle pull = candles.get(p);
        Candle last = candles.get(candles.size() - 1);

        if (last.openTime <= pullbackTime) return false;

        // Final trigger: price must close above the pullback candle high.
        return last.close > pull.high &&
                last.close > last.open;
    }

    private boolean entryConfirmedAfterBearPullback(
            ArrayList<Candle> candles,
            long pullbackTime) {

        int p = indexByTime(candles, pullbackTime);
        if (p < 0 || p >= candles.size() - 1) return false;

        Candle pull = candles.get(p);
        Candle last = candles.get(candles.size() - 1);

        if (last.openTime <= pullbackTime) return false;

        // Final trigger: price must close below the pullback candle low.
        return last.close < pull.low &&
                last.close < last.open;
    }

    private int indexByTime(
            ArrayList<Candle> candles,
            long time) {

        for (int i = 0; i < candles.size(); i++) {
            if (candles.get(i).openTime == time) {
                return i;
            }
        }

        return -1;
    }

    private double pullbackExtremeLow(
            ArrayList<Candle> candles,
            long pullbackTime) {

        int i = indexByTime(candles, pullbackTime);
        if (i < 0) return 0;

        return candles.get(i).low;
    }

    private double pullbackExtremeHigh(
            ArrayList<Candle> candles,
            long pullbackTime) {

        int i = indexByTime(candles, pullbackTime);
        if (i < 0) return 0;

        return candles.get(i).high;
    }

    private boolean latestBullSweepAfter(
            ArrayList<Candle> candles,
            long afterTime) {

        for (int i = candles.size() - 1; i >= 1; i--) {
            Candle c = candles.get(i);
            if (c.openTime <= afterTime) break;

            Candle prev = candles.get(i - 1);

            if (c.low < prev.low &&
                    c.close > prev.low &&
                    c.close > c.open) {
                return true;
            }
        }

        return false;
    }

    private boolean latestBearSweepAfter(
            ArrayList<Candle> candles,
            long afterTime) {

        for (int i = candles.size() - 1; i >= 1; i--) {
            Candle c = candles.get(i);
            if (c.openTime <= afterTime) break;

            Candle prev = candles.get(i - 1);

            if (c.high > prev.high &&
                    c.close < prev.high &&
                    c.close < c.open) {
                return true;
            }
        }

        return false;
    }

    private boolean latestBullEngulfAfter(
            ArrayList<Candle> candles,
            long afterTime) {

        for (int i = candles.size() - 1; i >= 1; i--) {
            Candle c = candles.get(i);
            if (c.openTime <= afterTime) break;

            Candle prev = candles.get(i - 1);

            if (prev.close < prev.open &&
                    c.close > c.open &&
                    c.open <= prev.close &&
                    c.close >= prev.open) {
                return true;
            }
        }

        return false;
    }

    private boolean latestBearEngulfAfter(
            ArrayList<Candle> candles,
            long afterTime) {

        for (int i = candles.size() - 1; i >= 1; i--) {
            Candle c = candles.get(i);
            if (c.openTime <= afterTime) break;

            Candle prev = candles.get(i - 1);

            if (prev.close > prev.open &&
                    c.close < c.open &&
                    c.open >= prev.close &&
                    c.close <= prev.open) {
                return true;
            }
        }

        return false;
    }

    private boolean latestBullBreakAfter(
            ArrayList<Candle> candles,
            long afterTime) {

        for (int i = candles.size() - 1; i >= 1; i--) {
            Candle c = candles.get(i);
            if (c.openTime <= afterTime) break;

            double level = previousHigh(
                    candles,
                    6,
                    candles.size() - 1 - i
            );

            if (c.close > level && c.close > c.open) {
                return true;
            }
        }

        return false;
    }

    private boolean latestBearBreakAfter(
            ArrayList<Candle> candles,
            long afterTime) {

        for (int i = candles.size() - 1; i >= 1; i--) {
            Candle c = candles.get(i);
            if (c.openTime <= afterTime) break;

            double level = previousLow(
                    candles,
                    6,
                    candles.size() - 1 - i
            );

            if (c.close < level && c.close < c.open) {
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
