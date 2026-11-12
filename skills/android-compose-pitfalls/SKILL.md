---
name: android-compose-pitfalls
description: قائمة أخطاء مثبتة (بناء، تشغيل، Firebase، CI، رندر 3D، رفع GitHub) يجب تفاديها عند إنشاء أو إصلاح أي مشروع أندرويد Kotlin/Compose وبناء APK عبر GitHub Actions. استخدمها قبل كتابة Gradle أو المانيفست أو واجهات Compose أو الـ workflow.
---

# Android Compose Pitfalls (v2)

طبّق هذه القواعد قبل كتابة المشروع، وراجعها قبل الرفع. كل بند وقع فعلاً.

## 0. كيف تقرأ سجل فشل البناء (ابدأ من هنا)
- افتح ملف الخطوة `Build Debug APK`، وابحث عن `^e: ` (أخطاء Kotlin) و`FAILED` و`What went wrong`.
- إن لم يوجد ملف لخطوة Gradle أصلاً فالفشل في خطوة سابقة (SDK/JDK)، وانظر آخر سطور تلك الخطوة.
- لا تعتبر "اكتمل البناء" = "التطبيق يعمل": اطلب نص الانهيار (انظر القسم 7).

## 1. الإصدارات
- **مشاريع جديدة بسيطة:** AGP 8.2.2 + Kotlin 1.9.22 + compose compiler 1.5.8 + BOM 2024.02.00 + Gradle 8.5 + JDK 17، compileSdk/targetSdk 34، minSdk 26.
- **مشاريع AI Studio / حديثة موجودة:** AGP 9.1.1 + Kotlin 2.2.10 (plugin `kotlin.compose`) + Gradle 9.3.1 + JDK 21. **لا تُنزّلها** إلى الإصدارات القديمة؛ احترم ما في `libs.versions.toml`.
- لا تغيّر إصداراً واحداً منفرداً. أضف `android.useAndroidX=true` في gradle.properties.
- أضف `kotlinx-coroutines-android` و`compose.foundation` صراحة في المشاريع الجديدة.

## 2. المانيفست والموارد
- `android:screenOrientation` بحروف صغيرة: `sensor` (وليس `Sensor`).
- لا تشِر إلى `@mipmap/ic_launcher` أو `@drawable/...` ما لم تُنشأ فعلاً (AAPT resource not found). بدون أيقونة احذف `icon`/`roundIcon`.
- **موارد يولّدها مكوّن خارجي لا تُستخدم عبر `R.string.xxx`:** مثل `default_web_client_id` (يولّدها google-services من `google-services.json`). عند غياب الملف يفشل التجميع بـ `Unresolved reference`. استخدم:
  `context.resources.getIdentifier("default_web_client_id","string",context.packageName)` وتحقق أن الناتج ≠ 0.

## 3. Git و .gitignore
- يجب أن يحوي: `build/` `.gradle/` `local.properties` `*.apk` `*.aab` `.idea/` `.kotlin/` (صيغة `/build` وحدها لا تكفي).
- لا PAT داخل أي ملف، ولا `git push --force`.
- لا gradle wrapper في المشاريع المولّدة؛ استخدم `gradle` عبر `gradle/actions/setup-gradle` بإصدار محدد.

## 4. GitHub Actions
- `permissions: contents: read`، و`concurrency` لإلغاء التشغيلات القديمة.
- `upload-artifact@v4` مع `if-no-files-found: error`، والمسار `app/build/outputs/apk/debug/app-debug.apk`.
- **لا تستخدم `android-actions/setup-android@v3` على ubuntu-latest:** فشل بـ `Failed to find package 'tools'` (sdkmanager 16.0). Android SDK مثبّت مسبقاً؛ اقبل التراخيص فقط:
  ```bash
  export ANDROID_HOME="${ANDROID_HOME:-/usr/local/lib/android/sdk}"
  echo "ANDROID_HOME=$ANDROID_HOME" >> "$GITHUB_ENV"
  yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses > /dev/null 2>&1 || true
  ```
- ارفع `actions/setup-java` إلى `@v5` (v4 يعطي تحذير إهمال).
- لا تحذف أسطر KSP/Room بـ `sed` داخل الـ workflow؛ اجعل المشروع نفسه سليماً.

## 5. التخزين المحلي: لا Room بدون KSP
- **لا تكتب `_Impl` لـ Room يدوياً** (يستعمل واجهات داخلية `androidx.room.util.*` هشّة، ويتكسر مع الإصدارات).
- إن لم يتوفر KSP فاستخدم `SQLiteOpenHelper` أو DataStore بنفس الواجهة العامة (Entity كـ data class، Dao كـ interface، Flow يُحدَّث بعد الكتابة عبر `MutableSharedFlow`)، وأضف `createInMemory()` للاختبارات.

## 6. Firebase بدون `google-services.json` (انهيار فوري عند الفتح)
- بدون الملف لا يُهيَّأ `FirebaseApp` تلقائياً، فتنهار أول استدعاءات `Firebase.app` / `Firebase.auth` / `Firebase.appCheck` في `onCreate`.
- في أول `onCreate` استدعِ تهيئة احتياطية:
  ```kotlin
  if (FirebaseApp.getApps(ctx).isEmpty()) FirebaseApp.initializeApp(ctx,
      FirebaseOptions.Builder().setApplicationId("1:000000000000:android:0000000000000000")
          .setApiKey("offline-placeholder-key").setProjectId("app-offline").build())
  ```
  وغلّف `configureAppCheck` بـ `runCatching`. وعند وجود `google-services.json` تُستخدم إعداداته الحقيقية.
- الميزات السحابية (Firestore/Auth/AI) تحتاج مشروع Firebase حقيقياً؛ وضّح للمستخدم أن بدونه يعمل التطبيق محلياً/ضيفاً فقط.

## 7. انهيار التطبيق بعد اكتمال البناء
- أضف دائماً مُسجّل انهيار داخل التطبيق: `Thread.setDefaultUncaughtExceptionHandler` يكتب الـ stack trace في `filesDir`، وفي التشغيل التالي تعرضه شاشة Compose قابلة للنسخ (`SelectionContainer`) مع زر "متابعة". يغني عن adb.

## 8. أخطاء Compose / الرسم
- داخل `pointerInput` اقرأ الحالة الأحدث عبر `rememberUpdatedState`، لا قيمة ملتقطة قديمة.
- شبكة 2D: أول خط `floor(-offset/step)` وليس `offset % x`.
- لا تجمع مساحة الخزائن العلوية مع الأرضية. استخدم `String.format(Locale.US, ...)`. اختر العنصر الأعلى ارتفاعاً عند التحديد بما يطابق ترتيب الرسم.

## 9. رندر 3D واقعي (Canvas)
- **إخفاء الأوجه الخلفية (back-face culling):** ارسم فقط الأوجه التي `nx*sin(yaw) + ny*cos(yaw) > 0`؛ يحل تداخل الأوجه عند أي زاوية دوران، ثم رتّب المرئي من الأبعد للأقرب والسطح العلوي أخيراً.
- **لا تعتمد على قيمة افتراضية للـ yaw** في دالة رسم الصندوق (كان -34° ثابتة لمعظم الاستدعاءات)؛ خزّن yaw الإطار الحالي في كائن إعدادات المشهد (`SceneShading`) يُضبط مرة في بداية الإطار.
- **إضاءة Lambert لكل وجه:** `k = ambient + diffuse*max(0, n·L)` مع `L` عكس اتجاه إزاحة الظل، وتلوين خفيف بلون الضوء (Kelvin) ووضع الليل بإضاءة محيطية أعلى وانتشار أقل.
- ألوان معتمة (alpha=1) للمجسمات الحقيقية؛ الشفافية للـ ghost/wireframe فقط.
- تدرج رأسي أغمق قرب الأرض (AO)، لمعة قطرية على الأسطح العلوية، حافة Fresnel على الحواف العليا.
- **ظلل ناعمة:** ملء بشفافية + 3 حلقات `Stroke` متدرجة (penumbra) بدل ظل حاد؛ BlurMaskFilter لا يصلح لـ minSdk < 28.
- **Vignette** نهائي: `radialGradient` من شفاف إلى أسود خفيف عند الحواف.
- الجدران بالموضع الثابت (x=0, y=0) لا تتبع الدوران؛ إن لزم اختر الجدارين الأبعد عن الكاميرا حسب yaw.

## 10. سير الرفع إلى GitHub (حساب abdfaith82)
1. `create_repository` قد يعطي 403 → لا تكرر؛ اطلب من المستخدم إنشاء مستودع فارغ.
2. `add_repo` بصلاحية push (اسم المستودع غير حساس لحالة الأحرف)، ثم `git clone` إلى مسار **غير مشغول**.
3. إن طلب المستخدم "الملفات المعدّلة فقط": قارن `diff -rq` بين مشروعك والنسخة المستنسخة وانسخ المختلف فقط (استثنِ README/ملفات المستخدم).
4. `git fetch` + rebase قبل الدفع، ثم commit وpush، ثم تحقق بـ `git log --oneline -1` و`git status -sb`.
5. لا `push_files` / `create_or_update_file` (403).

## 11. قبل التسليم
- البناء المحلي غير متاح دون إنترنت؛ الحكم النهائي من تبويب Actions. اطلب نص الخطأ إن فشل، وصحّح خطأً واحداً في كل دورة ثم أعد القراءة.
- أبلغ المستخدم بما لم يُجرَّب فعلياً.
