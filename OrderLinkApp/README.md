# تطبيق رابط النظام (Order Link App)

تطبيق أندرويد يفتح رابط الدخول الخاص بكل مستخدم (مندوب، مجهز، صاحب محل) ويحفظه ليفتح تلقائياً في كل مرة.

## آلية العمل

1. **أول مرة**: يطلب التطبيق إدخال الرابط (مرة واحدة فقط).
2. **حفظ الرابط**: يُخزَّن الرابط في الجهاز.
3. **بعد إغلاق وفتح التطبيق**: يفتح التطبيق الرابط المحفوظ تلقائياً داخل WebView.
4. **تغيير الرابط**: من القائمة (⋮) اختر «تغيير الرابط» لإدخال رابط جديد.

## أمثلة روابط

- مندوب: `https://d.ksebstor.site/deliveryman/2f6efd90aa8420a922e8da73`
- مجهز: `https://d.ksebstor.site/vendors/e6eadedbc1fb506f09a5d343`

---

## بناء ملف APK بدون Android Studio (مجاناً)

يتم البناء تلقائياً على **GitHub** ثم تنزيل ملف الـ APK.

### الخطوات

1. **إنشاء حساب على GitHub** (إن لم يكن لديك):  
   [github.com](https://github.com) → Sign up

2. **إنشاء مستودع جديد (New repository)**  
   - اسم المستودع مثلاً: `OrderLinkApp`  
   - اختر **Private** أو **Public**  
   - لا تضف README أو .gitignore  
   - اضغط **Create repository**

3. **رفع مشروع التطبيق**
   - في صفحة المستودع اختر **uploading an existing file**
   - اسحب مجلد المشروع بالكامل (المجلد الذي فيه `build.gradle` و `app` و `.github`) أو ارفع الملفات كلها داخل المجلد نفسه بحيث يكون في الجذر: `build.gradle`, `settings.gradle`, مجلد `app`, مجلد `.github`, مجلد `gradle`
   - اضغط **Commit changes**

4. **تشغيل البناء**
   - اذهب إلى تبويب **Actions** في المستودع
   - من الجهة اليسرى اختر **Build APK**
   - إما أن يبدأ البناء تلقائياً بعد الـ Push، أو اضغط **Run workflow** ثم **Run workflow**

5. **تنزيل الـ APK**
   - بعد انتهاء البناء (علامة خضراء) ادخل على آخر تشغيل (run)
   - في قسم **Artifacts** ستجد **app-debug-apk**
   - اضغط عليه لتحميل ملف مضغوط يحتوي على `app-debug.apk`
   - فك الضغط وانسخ `app-debug.apk` إلى الجوال وثبّته

### ملاحظة

- المجلد **`.github`** ضروري (فيه سير العمل الذي يبني التطبيق). تأكد أنه مرفوع داخل المستودع.
- إذا رفعت الملفات من داخل مجلد المشروع، تأكد أن `build.gradle` و `app` و `.github` في **جذر** المستودع وليس داخل مجلد فرعي.

---

## بناء APK محلياً (إن كان لديك Android Studio)

1. افتح المجلد `OrderLinkApp` في Android Studio.
2. **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
3. الـ APK يظهر في: `app/build/outputs/apk/debug/app-debug.apk`

## المتطلبات

- minSdk 24 (Android 7.0)
- targetSdk 34

## صلاحيات التطبيق

- **INTERNET**: لتحميل الموقع.
- **ACCESS_NETWORK_STATE**: للتحقق من الاتصال.
