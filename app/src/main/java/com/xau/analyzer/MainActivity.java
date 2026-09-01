package com.xau.analyzer;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.view.*;
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

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        price=findViewById(R.id.price);
        status=findViewById(R.id.status);
        details=findViewById(R.id.analysis);
        updated=findViewById(R.id.updated);
        refresh=findViewById(R.id.refresh);
        refresh.setOnClickListener(v -> load());
        findViewById(R.id.settings).setOnClickListener(v -> showInfo());
        load();
        timer=()->{ load(); handler.postDelayed(timer,10000); };
        handler.postDelayed(timer,10000);
    }

    void load() {
        setStatus("🟡 CONNECTING", Color.rgb(170,120,0), "در حال اتصال به فید عمومی XAUUSD...");
        Request req=new Request.Builder()
            .url("https://biquote.io/api/XAUUSD?allowStale=false")
            .header("Accept","application/json")
            .get().build();
        client.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, java.io.IOException e) {
                runOnUiThread(()->setStatus("🔴 FEED ERROR", Color.rgb(190,30,30),
                    "اتصال به Biquote برقرار نشد.\n\n"+e.getClass().getSimpleName()+": "+e.getMessage()));
            }
            @Override public void onResponse(Call call, Response response) throws java.io.IOException {
                String body=response.body()!=null?response.body().string():"";
                if(!response.isSuccessful()) {
                    runOnUiThread(()->setStatus("🔴 HTTP "+response.code(), Color.rgb(190,30,30),
                        "پاسخ فید موفق نبود.\n\n"+body));
                    return;
                }
                try {
                    JSONObject j=new JSONObject(body);
                    String symbol=j.optString("symbol","XAUUSD");
                    double bid=j.optDouble("bid",Double.NaN);
                    double ask=j.optDouble("ask",Double.NaN);
                    double mid=j.optDouble("mid",Double.NaN);
                    String market=j.optString("marketState","?");
                    boolean stale=j.optBoolean("stale",false);
                    long age=j.optLong("quoteAgeSeconds",-1);
                    String stamp=j.optString("timestamp","");
                    if(Double.isNaN(mid)) throw new Exception("mid در پاسخ وجود ندارد");
                    runOnUiThread(()->render(symbol,bid,ask,mid,market,stale,age,stamp));
                } catch(Exception e) {
                    runOnUiThread(()->setStatus("🔴 JSON ERROR", Color.rgb(190,30,30),
                        "پاسخ دریافت شد ولی ساختار آن قابل پردازش نیست.\n\n"+e.getMessage()+"\n\n"+body));
                }
            }
        });
    }

    void render(String symbol,double bid,double ask,double mid,String market,boolean stale,long age,String stamp) {
        price.setText(String.format(Locale.US,"XAUUSD  %.2f",mid));
        String freshness=age>=0 ? ("سن قیمت: "+age+" ثانیه") : "سن قیمت: نامشخص";
        updated.setText("آخرین دریافت: "+new SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date()));
        int c=(!stale && "open".equalsIgnoreCase(market))?Color.rgb(0,125,70):Color.rgb(175,120,0);
        status.setText((!stale ? "🟢 CONNECTED" : "🟡 STALE"));
        status.setTextColor(c);
        details.setText(String.format(Locale.US,
            "Symbol: %s\nMarket: %s\nBid: %.2f\nAsk: %.2f\nMid: %.2f\nSpread: %.2f\n%s\nTimestamp: %s\n\nفید زنده با موفقیت دریافت شد.\n\nمرحله بعد: اتصال OHLC واقعی M5 / M15 / H1 و سپس فعال‌کردن موتور تحلیل.",
            symbol,market,bid,ask,mid,ask-bid,freshness,stamp));
    }

    void setStatus(String s,int c,String d){status.setText(s);status.setTextColor(c);details.setText(d);}
    void showInfo(){new AlertDialog.Builder(this)
        .setTitle("XAUUSD Analyzer v6")
        .setMessage("این نسخه عمداً فقط اتصال فید را تست می‌کند. از endpoint رسمی عمومی Biquote برای XAUUSD استفاده می‌شود و API Key/ثبت‌نام لازم نیست. تا زمانی که CONNECTED را نبینیم، موتور تحلیل را فعال نمی‌کنیم.")
        .setPositiveButton("باشه",null).show();}
}