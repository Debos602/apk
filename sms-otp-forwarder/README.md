# SMS OTP Forwarder (Android)

ফোনের SIM-এ আসা SMS পড়ে, মেসেজ থেকে OTP বের করে, আপনার দেওয়া API endpoint-এ
JSON আকারে POST করে পাঠিয়ে দেয়। সব কিছু অ্যাপের ভেতরের **Settings** স্ক্রিন থেকে
কনফিগার করা যায় — কোড পরিবর্তন করতে হয় না।

> Reads incoming SMS, extracts the OTP, and forwards it to your API as JSON.
> Everything is configurable from the in-app settings screen.

---

## Stack
- Kotlin + Jetpack Compose (Material 3)
- WorkManager — background POST with automatic retry (survives app being closed)
- No third-party networking lib (uses `HttpURLConnection`)
- `minSdk 26` (Android 8), `targetSdk 34`, `compileSdk 34`

## Open & build

1. Open Android Studio → **Open** → select this `sms-otp-forwarder` folder.
2. Let Gradle sync (first sync downloads dependencies).
3. Run on a **physical phone** (emulators usually can't receive real SMS).
   - Build an installable APK: **Build → Build Bundle(s)/APK(s) → Build APK(s)**,
     or from a terminal in this folder: `./gradlew assembleDebug`
   - APK output: `app/build/outputs/apk/debug/app-debug.apk`

## First run (on the phone)
1. Open the app → tap **Grant SMS permission** (and allow notifications on Android 13+).
2. Fill in:
   - **API URL** — your POST endpoint, e.g. `https://api.example.com/otp`
   - **Auth header** (optional) — e.g. name `Authorization`, value `Bearer xxxxx`
   - **Sender filter** (optional) — comma-separated fragments; blank = all senders
   - **OTP regex** — default `\b(\d{4,8})\b` matches 4–8 digit codes
3. Turn **Forwarding enabled** ON.
4. Tap **Save & send test request** to confirm your server receives it.

## Payload sent to your API

```
POST <your API URL>
Content-Type: application/json

{
  "sender":    "VERIFY",
  "message":   "Your verification code is 123456",
  "otp":       "123456",
  "timestamp": 1730731200000,
  "device":    "Pixel 7"
}
```
If no OTP is matched, `"otp"` is `null` (the message is still forwarded unless you
turn off **Forward even if no OTP matched**).

## Notes / limitations
- **Android only.** iOS does not allow apps to read SMS, so this can't be ported to iPhone.
- The receiver uses the normal `SMS_RECEIVED` broadcast, so the app does **not** need to
  be the default SMS app and does not delete or alter your messages.
- Google Play restricts apps that use SMS permissions. This is built for **sideloading**
  (install the APK directly) / your own use.
- HTTPS is strongly recommended for your endpoint. Plain HTTP is allowed (for a local/test
  server) via `network_security_config.xml`, but OTPs over HTTP are not secure.
- Treat this as a tool for **your own device / your own server**. Forwarding other people's
  OTPs without consent is unsafe and likely illegal.

## Customize
- Change the JSON shape: `ForwardWorker.kt` → `doWork()`.
- Change OTP detection default: `Prefs.DEFAULT_OTP_REGEX`.
- Change package name: `com.freedev.smsotpforwarder` (update `applicationId` in
  `app/build.gradle.kts`, the `namespace`, and the folder path).
