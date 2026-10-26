# PattNG

This build is maintained in [mtpali/PattNG](https://github.com/mtpali/PattNG), based on
upstream [2.3.13-P69](https://github.com/patterniha/PattNG/tree/2.3.13-P69)
(`3b657ff297fbc6570d65a2f4508c59e13f198131`). Upstream licenses and credits are preserved below.

- Application ID: `com.v2ray.ang`.
- Release: `2.3.13-P69.1`; the delivered APK targets `arm64-v8a` only.
- R8 code optimization and resource shrinking are enabled, with Gson/JNI compatibility rules.
- Policy-group exports use standard GeoIP references. Imported P69 CN/private aliases are
  normalized without changing servers, selectors, fallback tags, DNS settings or custom databases.
- The supplied launcher mark is centered inside the Android adaptive-icon safe zone and also
  provided as legacy, round and monochrome icons.

### Build

Use the checked-in Gradle wrapper, JDK 21, Android platform 37.0, build-tools 37.0.0 and
NDK 30.0.16248370. Initialize submodules recursively, run `python3 scripts/prepare_native.py`,
then rebuild HEV using `ABI_FILTERS=arm64-v8a bash compile-hevtun.sh` with `NDK_HOME` set.
Copy `libs/.` into `V2rayNG/app/libs/` and run from `V2rayNG/`:

```sh
./gradlew :app:testPlaystoreDebugUnitTest :app:compilePlaystoreDebugKotlin \
  :app:assemblePlaystoreDebug :app:assemblePlaystoreRelease -PABI_FILTERS=arm64-v8a
```

The Actions workflow performs the same checks and produces one unsigned release APK.
Sign it with your own private keystore using `apksigner`; signing keys are never committed.
The package ID matches official v2rayNG, so installing over that app requires its original
signing key. Export existing profiles before replacing an installation signed with another key.

The Xray AAR and unchanged Aether/Psiphon/transport binaries are pinned to the P69 release,
verified with SHA-256, and matched to the recorded native submodule revisions.
To regenerate launcher resources, install Pillow and run `python3 scripts/generate_launcher_icons.py`.

v2rayNG fork for Iranians

**تغییرات v2rayNG:**

* اضافه شدن cipherSuites و فینگرپرینت unsafe در تنظیمات و شیرلینک.

* اضافه شدن هسته‌ی aether

**تغییرات Xray-core:**

* امکان اتصال به کانفیگ‌های غیر رمزنگاری شده برای آدرس‌های عمومی در VLESS و TROJAN

## حمایت

اگر کارهای بنده باعث دسترسی شما به اینترنت آزاد شده است ممنون میشم حمایتی هم از اینجانب انجام دهید

`USDT (BEP20)`: 0x76a768B53Ca77B43086946315f0BDF21156bF424

`USDT (TRC20)`: TU5gKvKqcXPn8itp1DouBCwcqGHMemBm8o

`TON (TON)`: UQAc-mZB3y7uxWHKiMmq0ORZEYgycWDWZ4V1k73HsXvTJx-i

@patterniha
