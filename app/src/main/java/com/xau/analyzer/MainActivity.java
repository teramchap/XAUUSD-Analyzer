package com.xau.analyzer;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.widget.*;
import java.text.*;
import java.util.*;
import okhttp3.*;
import org.json.*;

public class MainActivity extends Activity {

    TextView price, status, details, updated;
    Button refresh;

    final OkHttpClient client = new OkHttpClient.Builder().build();
    final Handler handler = new Handler(Looper.getMainLooper());
    Runnable timer;

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        price = findViewById(R.id.price);
        status = findViewById(R.id.status);
        details = findViewById(R.id.analysis);
        updated = findViewById(R.id.updated);
        refresh = findViewById(R.id.refresh);

        refresh.setOnClickListener(v -> load());

        findViewById(R.id.settings).setOnClickListener(
            v -> showInfo()
        );

        load();

        timer = () -> {
            load();
            handler.postDelayed(timer, 10000);
        };

        handler.postDelayed(timer, 10000);
    }

    void load() {

        setStatus(
            "🟡 CONNECTING",
            Color.rgb(170, 120, 0),
            "در حال اتصال به فید عمومی XAUUSD..."
        );

        Request req = new Request.Builder()
            .url("https://biquote.io/api/XAUUSD?allowStale=false")
            .header("Accept", "application/json")
            .get()
            .build();

        client.newCall(req).enqueue(new Callback() {

            @Override
            public void onFailure(
                Call call,
                java.io.IOException e
            ) {

                runOnUiThread(() ->
                    setStatus(
                        "🔴 FEED ERROR",
                        Color.rgb(190, 30, 30),
                        "اتصال به فید برقرار نشد.\n\n" +
                        e.getClass().getSimpleName() +
                        ": " +
                        e.getMessage()
                    )
                );
            }

            @Override
            public void onResponse(
                Call call,
                Response response
            ) throws java.io.IOException {

                String body =
                    response.body() != null
                    ? response.body().string()
                    : "";

                if (!response.isSuccessful()) {

                    runOnUiThread(() ->
                        setStatus(
                            "🔴 HTTP " + response.code(),
                            Color.rgb(190, 30, 30),
                            "پاسخ فید موفق نبود.\n\n" + body
                        )
                    );

                    return;
                }

                try {

                    JSONObject j = new JSONObject(body);

                    String symbol =
                        j.optString("symbol", "XAUUSD");

                    double bid =
                        j.optDouble("bid", Double.NaN);

                    double ask =
                        j.optDouble("ask", Double.NaN);

                    double mid =
                        j.optDouble("mid", Double.NaN);

                    String market =
                        j.optString("marketState", "?");

                    boolean stale =
                        j.optBoolean("stale", false);

                    long age =
                        j.optLong("quoteAgeSeconds", -1);

                    String stamp =
                        j.optString("timestamp", "");

                    if (Double.isNaN(mid)) {
                        throw new Exception(
                            "mid در پاسخ فید وجود ندارد"
                        );
                    }

                    runOnUiThread(() ->
                        render(
                            symbol,
                            bid,
                            ask,
                            mid,
                            market,
                            stale,
                            age,
                            stamp
                        )
                    );

                } catch (Exception e) {

                    runOnUiThread(() ->
                        setStatus(
                            "🔴 JSON ERROR",
                            Color.rgb(190, 30, 30),
                            "پاسخ دریافت شد ولی قابل پردازش نیست.\n\n" +
                            e.getMessage()
                        )
                    );
                }
            }
        });
    }

    void render(
        String symbol,
        double bid,
        double ask,
        double mid,
        String market,
        boolean stale,
        long age,
        String stamp
    ) {

        price.setText(
            String.format(
                Locale.US,
                "XAUUSD  %.2f",
                mid
            )
        );

        updated.setText(
            "آخرین دریافت: " +
            new SimpleDateFormat(
                "HH:mm:ss",
                Locale.US
            ).format(new Date())
        );

        String freshness =
            age >= 0
            ? "سن قیمت: " + age + " ثانیه"
            : "سن قیمت: نامشخص";

        int c =
            (!stale && "open".equalsIgnoreCase(market))
            ? Color.rgb(0, 125, 70)
            : Color.rgb(175, 120, 0);

        status.setText(
            !stale
            ? "🟢 CONNECTED"
            : "🟡 STALE"
        );

        status.setTextColor(c);

        details.setText(
            String.format(
                Locale.US,

                "Symbol: %s\n" +
                "Market: %s\n\n" +
                "Bid: %.2f\n" +
                "Ask: %.2f\n" +
                "Mid: %.2f\n" +
                "Spread: %.2f\n\n" +
                "%s\n" +
                "Timestamp: %s\n\n" +

                "فید XAUUSD با موفقیت دریافت شد.\n\n" +

                "مرحله بعد:\n" +
                "اتصال کندل‌های M5 / M15 / H1\n" +
                "و سپس فعال‌کردن موتور تحلیل.",

                symbol,
                market,
                bid,
                ask,
                mid,
                ask - bid,
                freshness,
                stamp
            )
        );
    }

    void setStatus(
        String text,
        int color,
        String message
    ) {

        status.setText(text);
        status.setTextColor(color);
        details.setText(message);
    }

    void showInfo() {

        new AlertDialog.Builder(this)

            .setTitle("XAUUSD Analyzer v6")

            .setMessage(
                "این نسخه فقط اتصال فید را تست می‌کند.\n\n" +
                "قیمت XAUUSD از فید عمومی دریافت می‌شود.\n\n" +
                "API Key و ثبت‌نام لازم نیست.\n\n" +
                "تا زمانی که اتصال CONNECTED تأیید نشود، " +
                "موتور تحلیل فعال نمی‌شود."
            )

            .setPositiveButton("باشه", null)

            .show();
    }
}
