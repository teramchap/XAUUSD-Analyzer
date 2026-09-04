# XAUUSD Analyzer — v14

## هدف
تبدیل تحلیل ساده به موتور تصمیم‌گیری:
`Setup → M15 Breakout → M5 Confirmation → Pullback → Entry Confirmation`

## خروجی
- NO ACTIONABLE TRADE
- BUY/SELL SETUP FORMING
- BUY READY
- SELL READY
- Entry
- Stop Loss
- TP1 / TP2 / TP3
- Risk و R:R

## منطق v14
1. فقط کندل‌های بسته تحلیل می‌شوند.
2. H1 فقط Context است.
3. M15 Trend + Structure + Breakout اصلی را تعیین می‌کند.
4. M5 باید بعد از Breakout تأیید بدهد.
5. Pullback باید بعد از Confirmation رخ دهد.
6. Entry فقط با بسته‌شدن کندل M5 در جهت شکست Pullback تأیید می‌شود.
7. Breakout بعد از حدود 2 ساعت منقضی می‌شود.
8. شکست نامعتبر M15 قبل از تکمیل زنجیره، Setup را باطل می‌کند.
9. Score فقط کیفیت Setup است، نه احتمال برد.
10. وقتی READY نیست، Entry/SL/TP ساختگی نمایش داده نمی‌شود.

## Entry / SL / TP
BUY:
- Entry = Close آخرین کندل بسته M5
- SL = کمینه Pullback منهای buffer یا فاصله 1.5×ATR، هرکدام محافظه‌کارانه‌تر باشد
- TP1/2/3 = 1R / 2R / 3R

SELL برعکس.

## Feed
Biquote:
- Price: https://biquote.io/api/XAUUSD?allowStale=false
- M5: https://biquote.io/api/XAUUSD/ohlc?interval=5m&limit=200
- M15: https://biquote.io/api/XAUUSD/ohlc?interval=15m&limit=200
- H1: https://biquote.io/api/XAUUSD/ohlc?interval=1h&limit=200

## تغییر UI
ظاهر اصلی حفظ شده و فقط یک کارت مستقل «Trade Plan» اضافه شده تا وقتی READY شد، Entry/SL/TP واضح و جداگانه دیده شوند.
