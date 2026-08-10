# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

GoodGlasses is a native Android (Kotlin + Jetpack Compose) app that captures a photo from one of three interchangeable camera sources — HTC Vive Glass, Meta smart glasses, or the phone's own camera — and sends it to an external inference server for either out-of-stock detection or expiry-date detection on retail shelf photos.

- Package: `com.example.goodglasses`, applicationId `com.htc.viveglass.viveglasssample`
- minSdk 29, target/compileSdk 36, Kotlin 2.2.10, AGP 9.2.1, Java 11 bytecode
- Single `:app` module, no multi-module structure

## Build / run / test

Build with the Gradle wrapper. `JAVA_HOME` must point at a JDK — Android Studio's bundled JBR works:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
.\gradlew.bat build
.\gradlew.bat test                    # JVM unit tests (app/src/test)
.\gradlew.bat connectedAndroidTest     # instrumented tests, needs a device/emulator
.\gradlew.bat lint
.\gradlew.bat :app:compileDebugKotlin --console=plain   # fast compile-only check
```

There is currently only boilerplate example tests (`ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt`); no real test suite exists yet.

### Required local setup

`local.properties` (gitignored) must contain:
- `sdk.dir` — Android SDK path
- `github_token` — GitHub PAT with package read access, used to resolve the Meta `mwdat-*` dependencies from `https://maven.pkg.github.com/facebook/meta-wearables-dat-android` (see `settings.gradle.kts`). Can also be supplied via the `GITHUB_TOKEN` env var.

The HTC Vive Glass SDK (`com.htc.viveglass.sdk:viveglass_client`) resolves from a local flat maven repo checked into `./repo`, not a remote server.

## Architecture: three interchangeable capture sources, one pipeline

The app's core design is a common `imageReceived: SharedFlow<Bitmap>` contract implemented by three independent managers, all wired together in `CameraTabModel` (`ui/tab/CameraTabModel.kt`), which is the single source of truth for UI state (`CameraUiState`) and reacts to whichever source is active via a `DeviceSource` enum (`HTC` / `META`; phone camera is not part of that enum — it's a manual one-shot fallback).

- **`ViveGlassKitManager`** (HTC Vive Glass) — wraps the AIDL-based `ViveGlassClient` SDK. Supports both real hardware and a bundled `ViveGlassSimulator` (toggled via `setSimulator()`); the two are swapped by reassigning `ViveGlass.adapter`. Also owns the H.264/audio video-streaming pipeline: `H264Decoder` + `AudioDecoder` feed into `StreamingPlayer`, which renders to a `Surface` supplied by a `TextureView` (`StreamPreview` in `CameraTab.kt`). `ViveGlassManager.kt` is an empty stub file — don't confuse it with `ViveGlassKitManager`.
- **`MetaGlassKitManager`** (Meta smart glasses) — wraps Meta's DAT SDK (`com.meta.wearable:mwdat-core`/`mwdat-camera`). Flow is: `Wearables.startRegistration()` → wait for `AutoDeviceSelector.activeDeviceFlow()` → request `Permission.CAMERA` via the Wearables permission contract → `Wearables.createSession()` → once `DeviceSessionState.STARTED`, `addStream()` → once `StreamState.STREAMING`, connected. See the `mwdat-android:*` skills for DAT SDK conventions/patterns (session lifecycle, camera streaming, permissions) before modifying this file.
- **`PhoneCameraManager`** — headless CameraX capture (no preview UI) used only as a fallback when neither glasses source is connected. `PhoneCameraScreen` provides an on-screen full preview + shutter UI for the "手機拍照" (phone photo) button, separate from the headless remote-trigger path.

All three managers are constructed once in `MainActivity.onCreate` and passed down through `SampleApp` → `CameraScreen` → `CameraTabModel`.

### Remote shutter priority

The physical volume-up key is the shutter button (`MainActivity.onKeyUp` → `triggerCaptureFromRemote`). Priority order: HTC connected → Meta connected → phone camera fallback. This priority is independent of which `DeviceSource` is selected in the UI.

### Analysis pipeline

`InventoryRepository` (`data/InventoryRepository.kt`) posts captured bitmaps to a user-configurable `ip:port` inference server (set via the nav-drawer settings UI, default `192.168.51.80:5000`), stored in-memory only:
- `AnalysisMode.INVENTORY` → `POST /check_out_of_stock` (JSON, base64 image) → out-of-stock items per shelf position
- `AnalysisMode.EXPIRY` → `POST /infer` (multipart JPEG) → bounding boxes + OCR'd expiry date strings (Traditional Chinese, format `YYYY年MM月DD日`, parsed by `DATE_STR_REGEX`)

Expiry pass/fail is computed client-side: `ExpiryItem.isExpiryFailing()` compares the parsed date against `now + warningMonths` (user-configurable, default `DEFAULT_EXPIRY_WARNING_MONTHS = 6`). Images taller than 4000px are downscaled 2x before upload to the expiry endpoint; returned bbox coordinates are scaled back up to original-image space in `parseExpiryItems`.

Both HTC and Meta paths speak analysis results back to the user via `ViveGlassKitManager.speakText()` (HTC-only TTS; Meta has no TTS integration here).

### UI structure

Single screen (`CameraScreen`) with a `ModalNavigationDrawer` holding settings (analysis mode toggle, server address, expiry warning threshold) and a `CameraTab` body showing either a live video/image preview (with drawn bounding-box overlays for expiry results) or an inventory/expiry results list. Most UI strings are Traditional Chinese. `ui/components/` holds small reusable Compose primitives (`CustomButton`, `CustomText`, `ViveHeaderSurface`, etc.); `ui/theme/AppColors` is the single color source — don't hardcode `Color(...)` values in feature code.

### Logging

`Logger.instance` is a lateinit global (`util/Logger.kt`) set once in `MainActivity.onCreate` to either `DebugLogger` (real `android.util.Log`) or `NoOpLogger`, controlled by the `enableDebugLog` constant in `MainActivity`. Use `Logger.instance.d/i/w/e(TAG, ...)` rather than `android.util.Log` directly in app code.
