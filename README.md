# עזרה - העוזר האישי שלך

עוזר קולי אישי בעברית למכשירי Android, עם ממשק גדול, RTL, אנשי קשר, ימי הולדת, תרופות לפי חלקי היום, יומן, תזכורות, חדשות ומזג אוויר.

## התחלה

- מדריך מהיר, כולל כל שאלות ההגדרה והתקנה ללא QR: [docs/QUICKSTART.he.md](docs/QUICKSTART.he.md).
- הדמיות המסכים: [docs/ezra-screens.html](docs/ezra-screens.html) — פתיחה בדפדפן; כפתורי בהיר/כהה והגדלת טקסט בראש הדף.
- התקנה, הרשאות, חתימה ועדכונים: [docs/INSTALL.he.md](docs/INSTALL.he.md).
- תסריטי בדיקה ומגבלות: [docs/TESTING.he.md](docs/TESTING.he.md).
- תבנית נתונים: [docs/contacts-template.csv](docs/contacts-template.csv). להדביק או לשמור כ־`.xlsx`; אין יבוא קובץ CSV דרך בורר Excel.

## בנייה

Android Studio, JDK 17 ומעלה, Android SDK 36. מינימום למכשיר: Android 8.0 (API 26). נדרש אינטרנט להורדת תלויות בבנייה הראשונה.

```powershell
.\scripts\build.ps1
```

קובץ בדיקה: `app/build/outputs/apk/debug/app-debug.apk`.

## מבנה

- Compose למסכים; ViewModel למצב ופעולות; Room לאנשי קשר, מקורות ותזכורות; DataStore להעדפות.
- פענוח פקודות מקומי ודטרמיניסטי. אין שרת, מפתחות AI או חשבון מוצר.
- שירותי Android לזיהוי דיבור ולהקראה. הזיהוי עלול להשתמש בשירות הרשת של המכשיר; לא נשמרים תמלילים בדיסק.
- Calendar Provider לקריאה בלבד; RSS/Atom לחדשות; Open-Meteo למזג אוויר.
- AlarmManager עם תזמון מדויק כאשר אושר, ו־WorkManager לרענון מחזורי.

הדמיות כוללות נתוני דוגמה מסומנים. באפליקציה אין אנשי קשר, חדשות או תחזית פיקטיביים; מקורות ואנשי קשר מוגדרים בהתקנה הראשונה.
