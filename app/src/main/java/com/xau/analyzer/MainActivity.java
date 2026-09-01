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
 TextView price,updated,status,analysis,chart;
 Handler h=new Handler(); OkHttpClient client=new OkHttpClient(); Runnable loop;
 static class Bar{double o,hi,lo,c;long t;Bar(double o,double hi,double lo,double c,long t){this.o=o;this.hi=hi;this.lo=lo;this.c=c;this.t=t;}}
 @Override public void onCreate(Bundle b){super.onCreate(b);setContentView(R.layout.activity_main);
  price=findViewById(R.id.price);updated=findViewById(R.id.updated);status=findViewById(R.id.status);analysis=findViewById(R.id.analysis);
  findViewById(R.id.refresh).setOnClickListener(v->fetchAll()); findViewById(R.id.settings).setOnClickListener(v->about());
  fetchAll(); loop=()->{fetchAll();h.postDelayed(loop,15000);};h.postDelayed(loop,15000);
 }
 void fetchAll(){fetch("1h",80);fetch("15m",120);fetch("5m",160);}
 void fetch(String tf,int lim){Request q=new Request.Builder().url("https://biquote.io/api/XAUUSD/ohlc?interval="+tf+"&limit="+lim).build();
  client.newCall(q).enqueue(new Callback(){
   public void onFailure(Call c,java.io.IOException e){runOnUiThread(()->err("خطا در فید "+tf));}
   public void onResponse(Call c,Response r)throws java.io.IOException{try{
    JSONObject root=new JSONObject(r.body().string());JSONArray a=root.getJSONArray("bars");ArrayList<Bar>b=new ArrayList<>();
    for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);b.add(new Bar(x.getDouble("open"),x.getDouble("high"),x.getDouble("low"),x.getDouble("close"),x.getLong("openTime")));}
    getPreferences(0).edit().putString(tf,serialize(b)).apply();if(tf.equals("5m"))runOnUiThread(()->render());
   }catch(Exception e){runOnUiThread(()->err("پاسخ فید "+tf+" قابل پردازش نیست"));}}
  });}
 String serialize(ArrayList<Bar>b){JSONArray a=new JSONArray();try{for(Bar x:b){JSONObject j=new JSONObject();j.put("o",x.o);j.put("h",x.hi);j.put("l",x.lo);j.put("c",x.c);j.put("t",x.t);a.put(j);}}catch(Exception ignored){}return a.toString();}
 ArrayList<Bar> read(String tf){ArrayList<Bar>b=new ArrayList<>();try{JSONArray a=new JSONArray(getPreferences(0).getString(tf,"[]"));for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);b.add(new Bar(x.getDouble("o"),x.getDouble("h"),x.getDouble("l"),x.getDouble("c"),x.getLong("t")));}}catch(Exception ignored){}return b;}
 double ema(ArrayList<Bar>b,int n){if(b.size()<n)return Double.NaN;double k=2d/(n+1),e=b.get(b.size()-n).c;for(int i=b.size()-n+1;i<b.size();i++)e=b.get(i).c*k+e*(1-k);return e;}
 double atr(ArrayList<Bar>b,int n){if(b.size()<n+1)return Double.NaN;double s=0;for(int i=b.size()-n;i<b.size();i++){Bar x=b.get(i),p=b.get(i-1);s+=Math.max(x.hi-x.lo,Math.max(Math.abs(x.hi-p.c),Math.abs(x.lo-p.c)));}return s/n;}
 String tr(ArrayList<Bar>b){if(b.size()<50)return "?";double e20=ema(b,20),e50=ema(b,50),c=b.get(b.size()-1).c;return c>e20&&e20>e50?"UP":c<e20&&e20<e50?"DOWN":"RANGE";}
 double[] swings(ArrayList<Bar>b,int n){double hi=-1e99,lo=1e99;for(int i=Math.max(0,b.size()-n-1);i<b.size()-1;i++){hi=Math.max(hi,b.get(i).hi);lo=Math.min(lo,b.get(i).lo);}return new double[]{hi,lo};}
 boolean bullEngulf(ArrayList<Bar>b){if(b.size()<3)return false;Bar a=b.get(b.size()-3),x=b.get(b.size()-2);return x.c>x.o&&a.c<a.o&&x.o<=a.c&&x.c>=a.o;}
 boolean bearEngulf(ArrayList<Bar>b){if(b.size()<3)return false;Bar a=b.get(b.size()-3),x=b.get(b.size()-2);return x.c<x.o&&a.c>a.o&&x.o>=a.c&&x.c<=a.o;}
 void render(){
  ArrayList<Bar> h1=read("1h"),m15=read("15m"),m5=read("5m");if(h1.size()<50||m15.size()<50||m5.size()<30){status.setText("🟡 WAIT");analysis.setText("در حال دریافت کندل‌های H1 / M15 / M5...");return;}
  double p=m5.get(m5.size()-1).c;price.setText(String.format(Locale.US,"XAUUSD  %.2f",p));updated.setText("آخرین تحلیل: "+new SimpleDateFormat("HH:mm:ss",Locale.US).format(new Date()));
  String a=tr(h1),b=tr(m15),c=tr(m5);double atr=atr(m15,14),rr=Math.max(atr*1.15,4);
  double[] sw=swings(m15,20);double rh=sw[0],rl=sw[1];boolean bu=p>rh,bd=p<rl;
  boolean be=bullEngulf(m5),se=bearEngulf(m5);
  int buy=0,sell=0;if(a.equals("UP"))buy+=2;if(a.equals("DOWN"))sell+=2;if(b.equals("UP"))buy+=2;if(b.equals("DOWN"))sell+=2;if(c.equals("UP"))buy++;if(c.equals("DOWN"))sell++;
  if(bu)buy+=2;if(bd)sell+=2;if(be)buy++;if(se)sell++;
  // liquidity sweep: price takes recent extreme but closes back inside
  Bar last=m5.get(m5.size()-2); boolean sweepLow=last.lo<rl&&last.c>rl; boolean sweepHigh=last.hi>rh&&last.c<rh;
  if(sweepLow)buy+=2;if(sweepHigh)sell+=2;
  String d="WAIT";if(buy>=6&&buy>sell)d="BUY";else if(sell>=6&&sell>buy)d="SELL";
  if(d.equals("BUY")){double sl=p-rr,tp1=p+rr*1.5,tp2=p+rr*2.5;status.setText("🟢 BUY SIGNAL");status.setTextColor(Color.rgb(0,125,70));
   analysis.setText(String.format(Locale.US,"H1: %s | M15: %s | M5: %s\\nBuy score: %d | Sell score: %d\\n\\nEntry: %.2f\\nSL: %.2f\\nTP1: %.2f\\nTP2: %.2f\\n\\nساختار/نقدینگی: %s",a,b,c,buy,sell,p,sl,tp1,tp2,(be||sweepLow)?"تأیید صعودی":"هم‌جهتی روند"));}
  else if(d.equals("SELL")){double sl=p+rr,tp1=p-rr*1.5,tp2=p-rr*2.5;status.setText("🔴 SELL SIGNAL");status.setTextColor(Color.rgb(190,30,30));
   analysis.setText(String.format(Locale.US,"H1: %s | M15: %s | M5: %s\\nBuy score: %d | Sell score: %d\\n\\nEntry: %.2f\\nSL: %.2f\\nTP1: %.2f\\nTP2: %.2f\\n\\nساختار/نقدینگی: %s",a,b,c,buy,sell,p,sl,tp1,tp2,(se||sweepHigh)?"تأیید نزولی":"هم‌جهتی روند"));}
  else {status.setText("🟡 WAIT");status.setTextColor(Color.rgb(175,120,0));analysis.setText(String.format(Locale.US,"H1: %s | M15: %s | M5: %s\\nBuy score: %d | Sell score: %d\\n\\nمقاومت اخیر M15: %.2f\\nحمایت اخیر M15: %.2f\\nATR14: %.2f\\n\\nفعلاً ستاپ با کیفیت کافی نداریم.",a,b,c,buy,sell,rh,rl,atr));}
 }
 void err(String s){status.setText("⚠️ FEED ERROR");analysis.setText(s);}
 void about(){new AlertDialog.Builder(this).setTitle("XAUUSD Analyzer v5").setMessage("فید عمومی Biquote + تحلیل چندتایم‌فریمی. شامل EMA20/50، ATR، شکست سقف/کف، Engulfing و تشخیص ساده Liquidity Sweep. این ابزار توصیه قطعی سرمایه‌گذاری نیست و معامله خودکار انجام نمی‌دهد.").setPositiveButton("باشه",null).show();}
}