# XAUUSD Analyzer v6

نسخه ۶ فقط اتصال فید را تست می‌کند تا مشکل نسخه‌های قبلی جدا شود.

- GET `https://biquote.io/api/XAUUSD?allowStale=false`
- نمایش Bid / Ask / Mid / Spread
- نمایش marketState و quoteAgeSeconds
- بروزرسانی هر 10 ثانیه
- نمایش خطای HTTP/JSON/Network به صورت واضح
- بدون تحلیل و بدون معامله خودکار

طبق مستندات Biquote، endpoint عمومی XAUUSD بدون API Key و ثبت‌نام قابل استفاده است و برای FX/CFD باید `mid` به عنوان قیمت واحد استفاده شود. OHLC نیز در `/api/{symbol}/ohlc` با intervalهای M5/M15/H1 ارائه می‌شود.
