package com.xau.analyzer;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;

public class XauChartView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ArrayList<MainActivity.CandleProxy> candles = new ArrayList<>();
    private int direction = 0;
    private double price, breakLevel, zoneLow, zoneHigh, entry, sl, tp1, tp2, tp3;
    private boolean ready;
    private String stage = "در انتظار تحلیل...";

    public XauChartView(Context c) { super(c); p.setTypeface(Typeface.create("sans", Typeface.NORMAL)); }

    public void setData(List<MainActivity.CandleProxy> data, double price, int direction,
                        double breakLevel, double zoneLow, double zoneHigh,
                        boolean ready, double entry, double sl, double tp1, double tp2, double tp3,
                        String stage) {
        candles.clear(); candles.addAll(data); this.price=price; this.direction=direction;
        this.breakLevel=breakLevel; this.zoneLow=zoneLow; this.zoneHigh=zoneHigh; this.ready=ready;
        this.entry=entry; this.sl=sl; this.tp1=tp1; this.tp2=tp2; this.tp3=tp3; this.stage=stage;
        invalidate();
    }
    private float y(double v, double min, double max, float top, float bottom) {
        if(max-min < 1e-9) return (top+bottom)/2f;
        return bottom-(float)((v-min)/(max-min))*(bottom-top);
    }
    private void text(Canvas c,String s,float x,float yy,float size,int color,Paint.Align align){
        p.setStyle(Paint.Style.FILL); p.setColor(color); p.setTextSize(size); p.setTextAlign(align); c.drawText(s,x,yy,p);
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c); c.drawColor(Color.rgb(16,18,20));
        if(candles.isEmpty()) { text(c,"در انتظار دریافت نمودار M5...",getWidth()/2f,getHeight()/2f,18,Color.LTGRAY,Paint.Align.CENTER); return; }
        int left=18,right=92,top=62,bottom=getHeight()-28;
        int n=Math.min(70,candles.size());
        double min=Double.MAX_VALUE,max=-Double.MAX_VALUE;
        for(int i=candles.size()-n;i<candles.size();i++){ MainActivity.CandleProxy z=candles.get(i); min=Math.min(min,z.low); max=Math.max(max,z.high); }
        double pad=Math.max((max-min)*0.10,0.5); min-=pad; max+=pad;
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1); p.setColor(Color.rgb(48,52,56));
        for(int i=1;i<6;i++){ float yy=top+(bottom-top)*i/6f; c.drawLine(left,yy,getWidth()-right,yy,p); }
        text(c,"XAUUSD · M5",left,30,18,Color.WHITE,Paint.Align.LEFT);
        text(c,stage,getWidth()-18,30,15,Color.LTGRAY,Paint.Align.RIGHT);
        float plotW=getWidth()-left-right, step=plotW/n, body=Math.max(3,step*0.55f);
        for(int k=0;k<n;k++){
            MainActivity.CandleProxy z=candles.get(candles.size()-n+k); float x=left+step*k+step/2f;
            float yh=y(z.high,min,max,top,bottom), yl=y(z.low,min,max,top,bottom), yo=y(z.open,min,max,top,bottom), yc=y(z.close,min,max,top,bottom);
            p.setStrokeWidth(1.5f); p.setColor(z.close>=z.open?Color.rgb(50,210,125):Color.rgb(235,75,75)); c.drawLine(x,yh,x,yl,p);
            p.setStyle(Paint.Style.FILL); float btop=Math.min(yo,yc), bh=Math.max(2,Math.abs(yo-yc)); c.drawRect(x-body/2,btop,x+body/2,btop+bh,p);
        }
        // Pullback zone
        if(direction!=0 && breakLevel>0){
            float zy1=y(zoneHigh,min,max,top,bottom), zy2=y(zoneLow,min,max,top,bottom);
            p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(45,255,193,7)); c.drawRect(left,Math.min(zy1,zy2),getWidth()-right,Math.max(zy1,zy2),p);
            line(c,y(breakLevel,min,max,top,bottom),Color.rgb(255,193,7),2,"BREAK "+fmt(breakLevel));
            line(c,zy1,Color.rgb(255,193,7),1,"ZONE"); line(c,zy2,Color.rgb(255,193,7),1,null);
        }
        if(ready){
            line(c,y(entry,min,max,top,bottom),Color.rgb(70,170,255),2,"ENTRY "+fmt(entry));
            line(c,y(sl,min,max,top,bottom),Color.rgb(245,75,75),2,"SL "+fmt(sl));
            line(c,y(tp1,min,max,top,bottom),Color.rgb(70,210,140),1,"TP1 "+fmt(tp1));
            line(c,y(tp2,min,max,top,bottom),Color.rgb(70,210,140),1,"TP2 "+fmt(tp2));
            line(c,y(tp3,min,max,top,bottom),Color.rgb(70,210,140),1,"TP3 "+fmt(tp3));
        }
        line(c,y(price,min,max,top,bottom),Color.WHITE,1.5,"PRICE "+fmt(price));
    }
    private void line(Canvas c,float yy,int color,float width,String label){
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(width); p.setColor(color); c.drawLine(18,yy,getWidth()-92,yy,p);
        if(label!=null) text(c,label,getWidth()-8,yy-4,11,color,Paint.Align.RIGHT);
    }
    private String fmt(double v){ return String.format(Locale.US,"%.2f",v); }
}
