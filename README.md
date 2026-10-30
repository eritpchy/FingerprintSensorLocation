# FingerprintSensorLocation

[![Maven Central](https://img.shields.io/maven-central/v/net.xdow/fingerprint-location-android)](https://central.sonatype.com/artifact/net.xdow/fingerprint-location-android)

A dependency-free Android library for identifying fingerprint sensor type and querying under-display fingerprint placement. It combines Android sensor properties, OEM APIs, vendor properties and framework overlays, returning current-display pixel bounds with provenance and explicit failure states.

This library queries sensor metadata and geometry only. It does not authenticate, access enrolled fingerprints, request root, change hidden-API policy, move the system fingerprint icon or modify another application's UI.

**Status: 0.5.0, experimental.** Available mechanisms depend on the device, firmware and caller privileges. A successful source-code test is not a stock-device validation. No universal coverage claim is made.

中文说明: 这是读取设备指纹传感器类型和报告位置的独立库，支持屏下光学、屏下超声波、侧边、后置、Home 键及来源诊断、旋转和分辨率换算。截至 2026-10-28，OPPO 云端已有 124 个目录配置完成七阶段、可视退出和租约释放检查，其中 78 个返回位置、18 个返回非屏下类型、28 个不可用；这些配置不等于 124 台独立硬件，也不代表物理位置已校准。vivo 已有 24 个当前产物完整流程，另有 4 个旧产物验证，保留各自固件和捕获限制。122 条公开 ROM 配置仅用于回归。普通应用无法读取接口时明确返回不可用；官方精确型号资料只作默认关闭的类型回退，不生成坐标。

## Existing work

[AdvancedBiometricPromptCompat](https://github.com/sergeykomlach/AdvancedBiometricPromptCompat) provides extensive authentication compatibility and an internal sensor-placement classifier. Its detector does not expose a standalone multi-OEM coordinate query. AOSP and LineageOS contain the relevant system implementations, but are not drop-in application libraries. See [research](docs/research.md) for the search scope and primary references.

## Modules

| Module | Output | Purpose |
| --- | --- | --- |
| `core` | Java 8 JAR | Immutable geometry, coordinate transforms, vendor parsers, conflict detection and diagnostics |
| `android` | Android AAR, minSdk 23 | Fresh display capture, Android/OEM reflection, overlay resources and opt-in IFAA |
| `probe` | app_process diagnostic | Read-only command-line use of the same library |
| `diagnostic` | Debug APK | Ordinary-app query, reported-region outline, rotation controls and JSON export for physical/cloud devices |

There are no runtime network calls, analytics or authentication dependencies. Default queries do not use model coordinate tables. The research corpus is test-only and is not packaged into the library.

## Use

Version 0.3.0 moves the Java packages and Maven group to `com.surcumference.fingerprintlocation`. Update imports and dependency coordinates when upgrading. The diagnostic application ID is `com.surcumference.fingerprintlocation.diagnostic`, with test package `com.surcumference.fingerprintlocation.diagnostic.test`.

Build on Linux with JDK 21 and Android SDK 37.2/build-tools 37.0.0. JDK 21 is needed by the Android 16 Robolectric fixtures:

```sh
./gradlew :core:test :core:jar :android:testDebugUnitTest :android:assembleRelease :android:lintRelease
./gradlew publishToMavenLocal
```

Use Maven Central in the consuming build. The Android AAR requires compileSdk 37.2 or newer; its runtime minimum remains API 23 (Android 6.0):

```groovy
repositories { mavenCentral() }
implementation 'net.xdow:fingerprint-location-android:0.5.0'
```

The Android artifact transitively includes `net.xdow:fingerprint-location-core:0.5.0`. Java packages remain `com.surcumference.fingerprintlocation`. For development builds published with `publishToMavenLocal`, add `mavenLocal()` explicitly. CI provides the AAR, core JAR and diagnostic JAR as build artifacts. An AAR used directly must be accompanied by the core JAR.

Call on a worker thread:

```java
Resolution result = FingerprintLocation.query(context, display);
if (result.status == Resolution.Status.FOUND) {
    Geometry.Bounds area = result.bounds;
    // Position the application's own content around this current-display region.
}
```

Imports:

```java
import com.surcumference.fingerprintlocation.FingerprintLocation;
import com.surcumference.fingerprintlocation.core.Geometry;
import com.surcumference.fingerprintlocation.core.Resolution;
```

For a selected authentication sensor:

```java
FingerprintLocation.Options options = new FingerprintLocation.Options.Builder()
        .sensorId(selectedSensorId)
        .build();
Resolution result = FingerprintLocation.query(context, display, options);
```

An explicit sensor ID never falls back to an unbound device-wide property. Multiple eligible sensors without a selected ID return an ambiguity rather than choosing the first one.

## Privileges and extension points

Ordinary applications may be denied hidden/system APIs or vendor properties. The library does not bypass these restrictions. `ACCESS_DENIED`, `UNAVAILABLE` and per-provider diagnostics identify the outcome. `exceptionType` records the exception class when retained by the provider; otherwise it is empty. Exception messages are not retained. Android hidden-API enforcement can appear as `NoSuchMethodException`; that exception alone does not distinguish a missing method from a blocked one. Empty vendor properties can likewise mean absent or inaccessible data. A privileged host can supply a `PropertyReader` using its existing, authorized property bridge:

```java
FingerprintLocation.Options options = new FingerprintLocation.Options.Builder()
        .properties(name -> existingPropertyBridge.read(name))
        .build();
```

Only requested property names are read. Root is never invoked by the library. Do not forward arbitrary shell commands through a property bridge.

An already-authorized host can also supply a fresh SystemUI service report:

```java
FingerprintLocation.Options options = new FingerprintLocation.Options.Builder()
        .systemUiDump(() -> existingSystemUiBridge.readFreshReport())
        .build();
```

This bridge is disabled by default. The library does not execute commands, obtain shell access or grant permissions. The caller must obtain a fresh, trusted report for each query; a matching display configuration cannot establish that a saved report is current. The parser accepts the AOSP `AuthController`/`UdfpsController` contract on display 0, requires registration, binds the sensor ID, validates display dimensions/rotation/scale, and rejects empty, null, malformed, duplicate and example-default geometry. `AuthController.udfpsBounds` is already resolution-scaled current-natural geometry, so it is rotated once and never scaled twice. Unrecognized OEM formats remain unavailable. Raw reports can contain unrelated private state; the library does not log or retain them.

`Options.Builder.provider(...)` accepts a caller-owned read-only provider, such as a system-server integration that already has access to a trusted SystemUI sensor window. The caller must validate the owner, display, coordinate space and lifecycle of that observation. A window title or an entire animation surface is insufficient proof of sensor geometry.

On vivo/iQOO, the exported system-owned Vivo Messenger metadata service is queried by default using the application's genuine package identity. This can start the metadata service but never requests authentication or enrollment. The callback wait has a shared 2000 ms budget across both known endpoints; disable it with `includeVivoService(false)` or set `vivoServiceTimeoutMillis(100..5000)`. Call on a worker thread; the service collector reports `WRONG_THREAD` instead of blocking the main looper. A timeout does not guarantee a bound on an OEM's synchronous Binder call. Every attempted binding is released, including timeout, interruption and failed binds. Only supported version 1.0/1.1 replies from an exported system package with already-held permissions are accepted. Generic UDFPS support does not establish optical versus ultrasonic technology. Recognized Vivo module properties supply type-only evidence and never model-based coordinates.

IFAA is opt-in with `includeIfaa(true)`. The query requires `getSupportBIOTypes(Context)` to explicitly include screen fingerprint support (`0x11`) before accepting `getExtInfo(GET_SENSOR_LOCATION)`. Some vendor services return default screen coordinates even on side/rear sensors. Neither metadata call requests enrollment or authentication. Vendor factories may initialize their service and block, so invoke the query off the UI thread and apply the host's lifecycle policy.

## Optional device specifications

`includeDeviceSpecifications(true)` enables an offline, exact-manufacturer/model type-only fallback. It is disabled by default and requires a public fingerprint feature declaration, display 0 and no explicit sensor selection. It runs after all runtime and caller providers, only when runtime type is unknown and no conflict or ambiguity exists. It never overrides runtime placement, supplies coordinates, corroborates geometry or starts network requests. `DEVICE_SPECIFICATION` and the official source URL/date in `typeEvidence` distinguish a product declaration from a runtime hardware report. A feature declaration is not proof the hardware is currently operational. Unknown models and near-matching SKUs remain unknown.

The verified entries include Google `Pixel 8 Pro` (`UNDER_DISPLAY`), Samsung `SM-A315N` (`OPTICAL`), Samsung `SM-S928U1` (`ULTRASONIC`), vivo `vivo X20Plus A` (`REAR`), Xiaomi `23127PN0CG` (`UNDER_DISPLAY`), Huawei `VOG-L29` (`UNDER_DISPLAY`), Nothing `A069` (`OPTICAL`), HONOR `BVL-N49` (`UNDER_DISPLAY`) vivo `V2158A` (`SIDE`), OPPO `PEQM00` (`UNDER_DISPLAY`) OPPO `PFVM10` (`SIDE`), Sony `A402SO` (`SIDE`), vivo `V1831A` (`REAR`), SHARP `SH-01L` (`FRONT`) and TECNO `TECNO KI5k` (`SIDE`). These are a small documented seed catalogue, not broad model-family coverage. Official model mappings and declarations are linked in [compatibility](docs/compatibility.md#optional-specification-catalogue). The diagnostic's unchecked **Use device specifications (type only)** checkbox enables this same production option for comparison; JSON records the choice.

## Optional community ROM coordinates

A community ROM report is available only through explicit profile selection:

```java
import com.surcumference.fingerprintlocation.core.CommunityGeometryProfile;

FingerprintLocation.Options options = new FingerprintLocation.Options.Builder()
        .communityGeometry(CommunityGeometryProfile.INFINIX_X6739_8B45A3C8)
        .build();
Resolution result = FingerprintLocation.query(context, display, options);
```

Review [the pinned ROM configuration](https://github.com/vlxlxlv/android_device_infinix_X6739/blob/8b45a3c8dd7189bf26985ad589681b86a25baaf0/overlay/FrameworksResOverlayX6739/res/values/config.xml#L481) before selecting this profile. It reports center `(540, 2172)` and radius `95` in a 1080 x 2400 native pixel frame. This is a community configuration report, not stock-firmware or physical calibration. Selecting it explicitly accepts that report for the caller's ROM; matching the model alone cannot establish firmware compatibility.

The profile is disabled by default. It requires the exact `INFINIX` / `Infinix X6739` identity, a fingerprint feature declaration, display 0, matching native dimensions and aspect ratio, and no explicit sensor ID. It fills missing geometry only, retains runtime errors, and cannot replace existing runtime bounds, non-screen placement, conflicts or ambiguity. Results use `COMMUNITY_ROM_CONFIGURATION` with the pinned source URL; they never count as independently corroborated geometry. Bounds use the same current-display rotation and resolution conversion as runtime providers. Other models, including X6525 and KI5k, receive no coordinates from this profile. Official type-only specification fallback remains separate and unchanged.

## Result contract

| Status | Bounds | Meaning |
| --- | --- | --- |
| `FOUND` | Current logical-display pixels | At least one usable report, with no detected source conflict |
| `NOT_UNDER_DISPLAY` | None | Sensor is reported as side, rear, home-button, exterior front or explicitly non-under-display placement |
| `UNAVAILABLE` | None | Unsupported/inaccessible API, missing geometry, wrong display or unconfirmed placeholder |
| `ACCESS_DENIED` | None | Access was explicitly denied and no usable report was available |
| `AMBIGUOUS` | None | More than one scoped sensor needs selection |
| `CONFLICT` | None | Reports disagree on geometry or sensor classification |

Sensor classification is independent of coordinate availability:

```java
if (result.typeStatus == Resolution.TypeStatus.KNOWN) {
    Observation.Type type = result.sensorType;
    boolean inScreen = type.underDisplay();
}
```

`sensorType` is `OPTICAL`, `ULTRASONIC`, `UNDER_DISPLAY` (subtype unknown), `SIDE`, `REAR`, `HOME`, `FRONT` (exterior front sensor), `NON_UNDER_DISPLAY` (placement unspecified) or `UNKNOWN`. `typeStatus` is `KNOWN`, `UNKNOWN`, `AMBIGUOUS` or `CONFLICT`; `sensorType` is `UNKNOWN` unless classification is `KNOWN`. `typeEvidence` retains the applicable reports and their sources. Positive framework power-button declarations and validated, display-matching `config_sfps_sensor_props` arrays supply side-placement evidence; empty or malformed resources do not establish a placement. `HOME` denotes a home-button sensor; `FRONT` does not infer home-button integration from an optional navigation gesture. Type-only evidence does not create bounds or change an unavailable location into `FOUND`. A known optical sensor may have unavailable coordinates; a side sensor returns `NOT_UNDER_DISPLAY` without screen coordinates. Coordinate disagreement can return location `CONFLICT` while an agreed type remains `KNOWN`. Conflicting types or multiple sensor IDs block geometry until resolved or explicitly selected.

On supported Samsung firmware, the ordinary application also reads the bounded, system-owned `/system/etc/floating_feature.xml` fingerprint declaration. The verified `optical_display_phone` / `optical_display_tablet`, `capacitive_powerkey_phone` and `ultrasonic_display_phone` values supply `OPTICAL`, `SIDE` and `ULTRASONIC` type evidence from `SAMSUNG_FEATURES`, independently of hidden API access and the default-off specification catalogue. It supplies no screen coordinates. Missing, unreadable, malformed or unrecognized declarations remain unavailable.

The Samsung adapter also tries the firmware's `SemFingerprintManager.createInstance(context).getCharacteristics()` entry point. Named constants classify placement and optical/ultrasonic technology; `getSensorAreaInDisplay()` supplies the current logical icon region without starting authentication. This remains a firmware-dependent API, not an Android SDK guarantee. It does not bypass hidden-API or permission enforcement. The region is not a physical sensor calibration or the expanded keyguard touch area.

When runtime evidence identifies a Samsung under-display sensor but supplies no bounds, the library also attempts an ordinary-process read of `/sys/class/fingerprint/fingerprint/position`. The supported nine-field sysfs layout reports millimetres: bottom margin, lateral offset, physical width and physical height, followed by UI sizes. `SAMSUNG_SYSFS` bounds use the physical area and the current display's millimetre scale, then the shared rotation transform. They do not use the firmware's default dimensions, icon size or expanded keyguard touch area. The initial adapter accepts primary displays with portrait natural orientation and zero lateral offset; unverified layouts, landscape-natural tablets and nonzero offsets remain unsupported. Access is subject to the device's file and SELinux policy. Both new Samsung paths have parser/provider regression coverage; ordinary-app availability on Samsung cloud hardware still requires new-binary validation and is not established by prior type-only test results.

Android sensor types follow AOSP: 2 is ultrasonic and 3 is optical. Version 0.4.0 corrects the reversed mapping in earlier framework and SystemUI adapters. A positive `config_is_powerbutton_fps` resource supplies side placement; its default false value supplies no placement evidence. Explicit vendor types and positive UDFPS flags can supply type without icon properties. Missing resources, hidden APIs and denied access remain unknown; the default runtime query does not infer placement from the model name, enrollment, authentication availability or absence of UDFPS coordinates.

`observations` contains the reported type, sensor/display association, coordinate space, provider and raw bounds. `issues` records missing APIs, denied access, invalid data, wrong displays and unbound sensor IDs. `corroborated` means two provider categories agree on the center. Providers may derive from the same HAL; this is not independent hardware calibration. Framework placeholder aliases cannot corroborate each other.

Bounds may describe an OEM's sensor, icon or reserved authentication region. They are not a measurement of the physical chip. IFAA regions can be larger than the icon. Applications should preserve the system's own fingerprint UI and use these bounds to reserve space in their own layout.

The AOSP constructor example `(540,1636,130)` is treated as unconfirmed until a second provider agrees. The same tuple can be real on a device; it is not unconditionally blacklisted. Empty properties never fall back to sample numbers from vendor documentation.

## Display changes

Query again for each authentication attempt and after rotation, resolution or display changes. Native-natural, current-natural and current-logical coordinates are distinct. AOSP native coordinates use the smaller width/height resolution ratio, matching SystemUI; the OPlus property contract explicitly scales the two axes independently. Already-logical OEM bounds are not scaled twice. Unspecified display IDs apply only to display 0; other displays require an exact unique-ID or a supported vendor numeric-ID match. A display configuration change during collection invalidates the result.

No result is cached by model name. Firmware, overlay, folding and resolution changes can invalidate model-only coordinates.

## Diagnostic

```sh
bash tools/build-probe.sh
adb push .work/probe/fingerprint-location-probe.jar /data/local/tmp/fingerprint-location-probe.jar
adb shell chmod 444 /data/local/tmp/fingerprint-location-probe.jar
adb shell 'CLASSPATH=/data/local/tmp/fingerprint-location-probe.jar app_process /system/bin --nice-name=FingerprintLocation-probe com.surcumference.fingerprintlocation.probe.Main'
```

Run under the host's existing privileged context only if it is already authorized. Compare the geometry with an independently read system sensor/icon region. The probe neither invokes `su` nor starts authentication. Its JSON contains display geometry and provider outcomes, never enrolled fingerprint data or payment credentials.

Before sharing a diagnostic, inspect it for display identifiers. See [compatibility](docs/compatibility.md) for implemented mechanisms, evidence levels and open gaps.

## Physical and manufacturer-cloud testing

Build the ordinary-app diagnostic with `./gradlew :diagnostic:assembleDebug :diagnostic:lintDebug` on Linux, or download `diagnostic-debug.apk` from the CI artifact. This APK has no network permission, root invocation, hidden-API exemption or authentication flow. It queries automatically at launch; IFAA requires the separate `Query + IFAA` control. The red outline visualizes the library result and is not independent location evidence.

Install the APK on an authorized device or upload it to a manufacturer's test console. Test the default query, landscape, return to portrait, opt-in IFAA, and Close. A platform can ignore orientation requests, especially on large screens; record the actual display dimensions and rotation rather than marking an ignored request passed. Copy JSON from the app or, where ADB/debuggable-app access is provided, export `adb shell run-as com.surcumference.fingerprintlocation.diagnostic cat files/latest.json`.

For an explicit instrumentation run, build `:diagnostic:assembleDebugAndroidTest` on Linux and install both diagnostic APKs. Run:

```sh
adb shell am instrument -w -e class com.surcumference.fingerprintlocation.diagnostic.DiagnosticE2eTest \
  com.surcumference.fingerprintlocation.diagnostic.test/androidx.test.runner.AndroidJUnitRunner
```

The test launches the Activity from its own instrumentation process, exercises default/IFAA queries, optional specification on/off, rotation, restoration and Close, and separately supplies a report read through the harness's `UiAutomation` shell. That step is labeled `app-with-instrumentation-shell-report`; it does not demonstrate ordinary-app access to SystemUI. JSON and original screenshots are saved under the app's external `files/e2e/` directory. No authentication, root invocation, permission grant or hidden-API exemption is used. Upload the app APK and test APK as an instrumentation test for cloud execution. This is a candidate route around a Robo stub launch restriction, not a claim that Xiaomi has passed.

For cloud consoles with unreliable logs or clipboard forwarding, **QR JSON** displays the complete current result offline. The payload is `FPSL1:` followed by standard Base64 of GZIP-compressed UTF-8 JSON. Decode the original screenshot, validate the GZIP checksum, and retain the screenshot with the recovered JSON and capture ID. Export controls are disabled while querying, so an earlier result cannot be exported as a new one. Only the diagnostic APK depends on ZXing 3.5.4; its Apache license is packaged in the diagnostic assets. The Android AAR and core JAR remain dependency-free.

For a cloud console that cannot forward swipe input through keyguard, build a diagnostic-only snapshot with `-PdiagnosticCloudQr=runtime` or `-PdiagnosticCloudQr=specifications`. Each uses a distinct app ID/name, shows its own Activity above keyguard with public window flags, exports the first query as QR and closes after 120 seconds. It does not dismiss keyguard or authenticate. JSON identifies the calling package, mode and keyguard state. The default `off` build remains interactive with specifications disabled; use that build for instrumentation. The diagnostic-only `-PdiagnosticCloudQr=sequence` build automatically exercises seven query stages using the same query/options implementation: default, specifications on, actual landscape, portrait restoration, IFAA, default restoration and specifications off. It exports each stage and a complete JSON bundle, then exits after 120 seconds. `workflowStatus=COMPLETE` means the sequence flags, freshness and requested actual orientation completed; evaluate the individual records separately for usable type/geometry. This does not validate cloud touch forwarding or perform authentication.

Record model, firmware, APK SHA-256, execution context, actual rotation, complete JSON, original screenshots and exit state. Compare successful geometry with an independent system sensor/icon report. Installation success, a provider returning unavailable, and a library-drawn rectangle are not a location-accuracy pass. See [E2E evidence and cloud access](docs/e2e.md).

## Tests and license

The test corpus contains 122 public LineageOS device/platform location definitions. Parameterized tests exercise display selection, four rotations and three scales, including side-sensor rejection. Additional tests cover malformed values, access denial, source disagreement, unbound sensor IDs, AOSP placeholder handling and fresh property reads. Android provider fixtures run on Robolectric SDK 31 and 36 and exercise API 31 fields, per-display lists, rectangular sensors, Samsung logical bounds and reflective access denial. These are configuration/geometry regressions, not 122 physical-device tests.

The specification-enabled 0.4.0 build passed 314 Linux regression tests, Android release Lint and diagnostic Lint. Its updated type/result contract passed all six query stages and Close on TB375FC; this firmware still returns `UNKNOWN` in the ordinary app and explicit SystemUI bridge contexts. Earlier versions also ran on PKG110 and ten cloud models; harness success is separate from usable sensor geometry. Named-device query results and their privilege limitations are recorded in [compatibility](docs/compatibility.md).

Apache-2.0. New providers and sanitized device evidence are welcome; see [CONTRIBUTING](CONTRIBUTING.md).


## Maven Central publishing

The shared `gradle/maven-publishing.gradle` configures both modules under `net.xdow`, including Apache-2.0 licensing, developer/SCM metadata, source and Javadoc archives, and optional in-memory signing. `libraryVersion` remains the single version source. Build and publish only on Linux.

GitHub Actions builds and tests on branch pushes and pull requests. To release, bump `libraryVersion` on `codex/initial-library`, push that commit, then push a matching `vX.Y.Z` tag pointing to it. The tag run validates the version and branch ancestry, signs and publishes both modules, and verifies their public artifacts against the built bytes. A manual release dispatch remains available for recovery; use `verify_only` to check an existing release without uploading again. The repository needs `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `MAVEN_SIGNING_KEY`, and `MAVEN_SIGNING_PASSWORD` secrets. Signing credentials are not stored in this repository.

## Optional byte-bound stock firmware geometry

```java
FingerprintLocation.Options options = new FingerprintLocation.Options.Builder()
    .stockFirmwareGeometry(StockFirmwareGeometryProfile.NOTHING_A069_B4_1_260808_1352)
    .build();
```

This independent option is disabled by default. It uses the extracted stock HAL's configuration for Nothing A069/Frogger build B4.1-260808-1352, build ID BQ2A.250913.001-BP2A.250605.031.A3 and incremental2608081352. With the default reader, the ordinary app must read and SHA-256 match the actual vendor fingerprint service before geometry is supplied. A missing/unreadable/different binary, other SKU/build, explicit sensor ID, external display, unsupported native frame or non-screen/conflicting runtime type prevents this fallback. Runtime geometry takes precedence.

The pinned configuration reports native1224x2720 center(612,2485), radius103. The common display transform handles matching-aspect resolution changes and rotation. Results use STOCK_FIRMWARE_CONFIGURATION and retain the [archive provenance](https://github.com/spike0en/nothing_archive/releases/tag/Frogger_B4.1-260808-1352) plus verified HAL hash. This is a firmware configuration report, with no independent corroboration or physical calibration. New-provider device E2E is pending; Linux regression is reported separately from hardware validation. This option is separate from the type-only specification catalogue and the explicitly selected community-ROM profile.

The matching stock ODM SELinux policy currently blocks the untrusted_app domain from reading this HAL executable despite its0755 mode. Consequently this strict read-hash option is not yet established as usable in ordinary apps on that firmware. It fails closed; do not treat Linux regression or a configured rectangle as device success. The explicit caller-reader route below is implemented; its physical-device validation remains pending.

An explicitly authorized caller can supply `Options.Builder.stockFirmwareComponentReader(reader)` alongside the stock profile. The reader must return bytes from the current device and enforce its own timeouts. The library closes the stream, caps it at 4 MiB and computes SHA-256 itself. This produces `CALLER_FIRMWARE_CONFIGURATION`, never ordinary-app provenance or independent corroboration. A reader without a stock profile is inactive. The library does not run shell commands, obtain privileges or accept an archive file as proof of the installed component. Instrumentation separately tests a read-only shell stream and labels those records `app-with-instrumentation-shell-firmware`; success there does not establish ordinary-app availability.

For the exact Pixel 8 Pro/husky build BP1A.250505.005.B1 (incremental13277630), the separate `GOOGLE_PIXEL_8_PRO_BP1A_250505_005_B1` profile requires all three active fingerprint APEX components to match their pinned SHA-256, plus exact four-byte big-endian CDT platform4/product2 read from the current device. A missing component, selector or permission fails closed. The static type11 configuration reports center(672,2261), radius115 on1344x2992; the common transform supports matching-aspect display modes. This optional path has no physical-device verification yet. Imported firmware byte matching and configuration selection are distinct from physical calibration. Defaults remain runtime-only.
