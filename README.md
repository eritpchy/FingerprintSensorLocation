# FingerprintSensorLocation

A dependency-free Android library for querying under-display fingerprint placement. It combines Android sensor properties, OEM APIs, vendor properties and framework overlays, returning current-display pixel bounds with provenance and explicit failure states.

This library queries geometry only. It does not authenticate, access enrolled fingerprints, request root, change hidden-API policy, move the system fingerprint icon or modify another application's UI.

**Status: 0.2.0, experimental.** Available mechanisms depend on the device, firmware and caller privileges. A successful source-code test is not a stock-device validation. No universal coverage claim is made.

中文说明: 这是读取设备实际指纹位置的独立库，支持来源诊断、旋转和分辨率换算。已检查两台本地设备，并在免费云真机上提交五个型号的测试，逐台记录可用、不可用及启动阻塞结果。122 条 ROM 配置样本属于自动回归，不能视为 122 台真机验证。普通应用可能无法读取系统或厂商接口，失败时明确返回不可用，不猜测位置。

## Existing work

[AdvancedBiometricPromptCompat](https://github.com/sergeykomlach/AdvancedBiometricPromptCompat) provides extensive authentication compatibility and an internal sensor-placement classifier. Its detector does not expose a standalone multi-OEM coordinate query. AOSP and LineageOS contain the relevant system implementations, but are not drop-in application libraries. See [research](docs/research.md) for the search scope and primary references.

## Modules

| Module | Output | Purpose |
| --- | --- | --- |
| `core` | Java 8 JAR | Immutable geometry, coordinate transforms, vendor parsers, conflict detection and diagnostics |
| `android` | Android AAR, minSdk 23 | Fresh display capture, Android/OEM reflection, overlay resources and opt-in IFAA |
| `probe` | app_process diagnostic | Read-only command-line use of the same library |
| `diagnostic` | Debug APK | Ordinary-app query, reported-region outline, rotation controls and JSON export for physical/cloud devices |

There are no runtime network calls, analytics, hard-coded model coordinates or authentication dependencies. The research corpus is test-only and is not packaged into the library.

## Use

Build on Linux with JDK 21 and Android SDK 37.2/build-tools 37.0.0. JDK 21 is needed by the Android 16 Robolectric fixtures:

```sh
./gradlew :core:test :core:jar :android:testDebugUnitTest :android:assembleRelease :android:lintRelease
./gradlew publishToMavenLocal
```

After local publication, add `mavenLocal()` to the consuming build's repositories:

```groovy
implementation 'io.github.eritpchy.fingerprintlocation:fingerprint-location-android:0.2.0'
```

This coordinate is for the local publication above. It is not a claim that an artifact is available on Maven Central. CI provides the AAR, core JAR and diagnostic JAR as build artifacts. An AAR used directly must be accompanied by the core JAR.

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
import io.github.eritpchy.fingerprintlocation.FingerprintLocation;
import io.github.eritpchy.fingerprintlocation.core.Geometry;
import io.github.eritpchy.fingerprintlocation.core.Resolution;
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

Ordinary applications may be denied hidden/system APIs or vendor properties. The library does not bypass these restrictions. `ACCESS_DENIED`, `UNAVAILABLE` and per-provider diagnostics identify the outcome. A privileged host can supply a `PropertyReader` using its existing, authorized property bridge:

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

IFAA is opt-in with `includeIfaa(true)`. The query calls only `getExtInfo(GET_SENSOR_LOCATION)`; it does not request enrollment or authentication. Vendor factories may initialize their service and block, so invoke the query off the UI thread and apply the host's lifecycle policy.

## Result contract

| Status | Bounds | Meaning |
| --- | --- | --- |
| `FOUND` | Current logical-display pixels | At least one usable report, with no detected source conflict |
| `NOT_UNDER_DISPLAY` | None | Sensor is reported as side, rear or home-button placement |
| `UNAVAILABLE` | None | Unsupported/inaccessible API, missing geometry, wrong display or unconfirmed placeholder |
| `ACCESS_DENIED` | None | Access was explicitly denied and no usable report was available |
| `AMBIGUOUS` | None | More than one eligible sensor needs selection |
| `CONFLICT` | None | Reports disagree on placement or under-display classification |

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
adb shell 'CLASSPATH=/data/local/tmp/fingerprint-location-probe.jar app_process /system/bin --nice-name=FingerprintLocation-probe io.github.eritpchy.fingerprintlocation.probe.Main'
```

Run under the host's existing privileged context only if it is already authorized. Compare the geometry with an independently read system sensor/icon region. The probe neither invokes `su` nor starts authentication. Its JSON contains display geometry and provider outcomes, never enrolled fingerprint data or payment credentials.

Before sharing a diagnostic, inspect it for display identifiers. See [compatibility](docs/compatibility.md) for implemented mechanisms, evidence levels and open gaps.

## Physical and manufacturer-cloud testing

Build the ordinary-app diagnostic with `./gradlew :diagnostic:assembleDebug :diagnostic:lintDebug` on Linux, or download `diagnostic-debug.apk` from the CI artifact. This APK has no network permission, root invocation, hidden-API exemption or authentication flow. It queries automatically at launch; IFAA requires the separate `Query + IFAA` control. The red outline visualizes the library result and is not independent location evidence.

Install the APK on an authorized device or upload it to a manufacturer's test console. Test the default query, landscape, return to portrait, opt-in IFAA, and Close. A platform can ignore orientation requests, especially on large screens; record the actual display dimensions and rotation rather than marking an ignored request passed. Copy JSON from the app or, where ADB/debuggable-app access is provided, export `adb shell run-as io.github.eritpchy.fingerprintlocation.diagnostic cat files/latest.json`.

For an explicit instrumentation run, build `:diagnostic:assembleDebugAndroidTest` on Linux and install both diagnostic APKs. Run:

```sh
adb shell am instrument -w -e class io.github.eritpchy.fingerprintlocation.diagnostic.DiagnosticE2eTest \
  io.github.eritpchy.fingerprintlocation.diagnostic.test/androidx.test.runner.AndroidJUnitRunner
```

The test launches the Activity from its own instrumentation process, exercises the controls, and separately supplies a report read through the harness's `UiAutomation` shell. That step is labeled `app-with-instrumentation-shell-report`; it does not demonstrate ordinary-app access to SystemUI. JSON and original screenshots are saved under the app's external `files/e2e/` directory. No authentication, root invocation, permission grant or hidden-API exemption is used. Upload the app APK and test APK as an instrumentation test for cloud execution. This is a candidate route around a Robo stub launch restriction, not a claim that Xiaomi has passed.

Record model, firmware, APK SHA-256, execution context, actual rotation, complete JSON, original screenshots and exit state. Compare successful geometry with an independent system sensor/icon report. Installation success, a provider returning unavailable, and a library-drawn rectangle are not a location-accuracy pass. See [E2E evidence and cloud access](docs/e2e.md).

## Tests and license

The test corpus contains 122 public LineageOS device/platform location definitions. Parameterized tests exercise display selection, four rotations and three scales, including side-sensor rejection. Additional tests cover malformed values, access denial, source disagreement, unbound sensor IDs, AOSP placeholder handling and fresh property reads. Android provider fixtures run on Robolectric SDK 31 and 36 and exercise API 31 fields, per-display lists, rectangular sensors, Samsung logical bounds and reflective access denial. These are configuration/geometry regressions, not 122 physical-device tests.

Version 0.2.0 passed 197 Linux regression tests, Android release Lint and diagnostic Lint. The instrumentation flow also ran on two local physical devices; its success is separate from usable sensor geometry. Named-device query results and their privilege limitations are recorded in [compatibility](docs/compatibility.md).

Apache-2.0. New providers and sanitized device evidence are welcome; see [CONTRIBUTING](CONTRIBUTING.md).
