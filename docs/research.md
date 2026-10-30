# Research and provenance

Research date: 2026-10-25. The search covered GitHub repository/code search for Android fingerprint sensor location, UDFPS libraries, OEM location APIs and IFAA location extensions, plus Maven Central searches for `udfps` and `fingerprint sensor location` (both returned no artifacts). This is a bounded search, not proof that no other implementation exists.

## Reusable projects considered

| Project | Relevant feature | Difference from this library |
| --- | --- | --- |
| [AdvancedBiometricPromptCompat](https://github.com/sergeykomlach/AdvancedBiometricPromptCompat) | Authentication compatibility; internal `FingerprintSensorDetector` reads configured sensor type/placement | No standalone public multi-OEM bounds query in the inspected detector |
| [AOSP](https://android.googlesource.com/platform/frameworks/base/) | Sensor locations, UDFPS overlay transforms | System implementation, restricted APIs |
| [LineageOS hardware/oplus](https://github.com/LineageOS/android_hardware_oplus) | Property-backed IFAA geometry | ROM component rather than an application library |
| [LineageOS hardware/xiaomi](https://github.com/LineageOS/android_hardware_xiaomi) | Property-backed AIDL sensor locations | ROM HAL component |
| [LineageOS hardware/samsung](https://github.com/LineageOS/android_hardware_samsung) | Property-backed AIDL sensor locations | ROM HAL component |

## Contracts consulted

- [AOSP Android 14 AuthController](https://github.com/aosp-mirror/platform_frameworks_base/blob/android14-release/packages/SystemUI/src/com/android/systemui/biometrics/AuthController.java) and [UdfpsController](https://github.com/aosp-mirror/platform_frameworks_base/blob/android14-release/packages/SystemUI/src/com/android/systemui/biometrics/UdfpsController.java): dump field scopes, registration state, cached display information, already-scaled current-natural UDFPS rectangle and controller sensor ID/type. The opt-in report parser checks these fields together; generic fingerprint points and empty OEM rectangles cannot supply geometry. This is a format contract, not universal stock-SystemUI coverage.

- [Android 12 sensor properties](https://android.googlesource.com/platform/frameworks/base/+/android-12.0.0_r1/core/java/android/hardware/fingerprint/FingerprintSensorPropertiesInternal.java): legacy center/radius fields.
- [Android 13 sensor properties](https://android.googlesource.com/platform/frameworks/base/+/android-13.0.0_r1/core/java/android/hardware/fingerprint/FingerprintSensorPropertiesInternal.java): per-display location list, stable display identifiers and constructor placeholder.
- [AOSP SensorLocation](https://android.googlesource.com/platform/hardware/interfaces/+/main/biometrics/fingerprint/aidl/android/hardware/biometrics/fingerprint/SensorLocation.aidl): display association, center/radius and sensor shape.
- [AOSP UDFPS transformations](https://android.googlesource.com/platform/frameworks/base/+/aa4b1a18da3e/packages/SettingsLib/src/com/android/settingslib/udfps/UdfpsUtils.java): resolution and rotation coordinate spaces.
- [Xiaomi OEM guidance](https://dev.mi.com/distribute/doc/details?pId=1610): legacy FOD top-left/size properties and the requirement to re-query for each authentication.
- [OPlus IFAA implementation](https://github.com/LineageOS/android_hardware_oplus/blob/5699c308e35206efd474982d887a136462f3fc34/packages/IFAAService/src/org/ifaa/aidl/manager/IfaaService.kt): center-bottom offset, icon diameter and display scaling. Documentation example defaults are deliberately not used as fallback coordinates.
- [Xiaomi ROM property schema](https://github.com/LineageOS/android_hardware_xiaomi/blob/7dd87795288bf6713795f112056b1bf549355c81/aidl/fingerprint/fingerprint.sysprop): `x|y|radius|display`, comma-separated entries.
- [Samsung ROM property schema](https://github.com/LineageOS/android_hardware_samsung/blob/fa202d96b62362e756f160e60a80f675add412a0/aidl/fingerprint/fingerprint.sysprop): `ro.vendor.fingerprint.sensor_location`; the implementation accepts semicolon-separated entries.

Samsung, Vivo and Huawei reflection adapters are independently written against inspected framework-method signatures. Decompiled framework mirrors provided version-specific investigation leads, not an OEM-wide compatibility guarantee. No vendor binary or decompiled implementation is included in this repository. These adapters require stock-firmware/device validation before a specific version can be marked device-verified.

## Corpus

`core/src/test/resources/device-profiles.tsv` contains 122 location definitions retrieved from 127 configuration records across 123 LineageOS device/platform repositories. Five records did not define a location (empty overlays or initialization/schema files). Each retained row includes its original URL and is marked as ROM configuration evidence. Side-sensor definitions are included to prevent false UDFPS classification.

These are numeric configuration facts, not copied vendor code. They are not installed into runtime artifacts and must not become a model-coordinate fallback database. A LineageOS profile does not establish the same coordinates for every stock ROM, regional variant or firmware version.

## Classification contracts checked for 0.4.0

[AOSP FingerprintSensorProperties](https://github.com/aosp-mirror/platform_frameworks_base/blob/android15-release/core/java/android/hardware/fingerprint/FingerprintSensorProperties.java) defines rear=1, ultrasonic=2, optical=3, power-button=4 and home-button=5. Android 12 defines the same numeric mapping. The framework and SystemUI adapters share this conversion; string-valued ROM properties retain their separate schema.

[AOSP framework resources](https://github.com/aosp-mirror/platform_frameworks_base/blob/android14-release/core/res/res/values/config.xml) describe `config_is_powerbutton_fps` as a positive declaration of a power-button fingerprint sensor, default false. Only true supplies placement evidence. `config_udfps_sensor_props` is a separate geometry report and may be absent on AIDL devices. Neither a false power-button flag nor a missing UDFPS resource establishes rear/home placement or absence of a fingerprint sensor.

[LineageOS Xiaomi Fingerprint](https://github.com/LineageOS/android_hardware_xiaomi/blob/lineage-23.0/aidl/fingerprint/Fingerprint.cpp) distinguishes string `udfps` (ultrasonic) from `udfps_optical`, and sets `ro.hardware.fp.udfps=true` for these sensors. A positive flag supplies only generic under-display placement, without subtype or invented bounds. This is a ROM contract, not a claim about every stock Xiaomi firmware.

The same AOSP resource contract includes `config_sfps_sensor_props`, an array of `[displayId, sensorLocationX, sensorLocationY, sensorRadius]` arrays. Valid, nonempty entries matching the queried display supply type-only `SIDE` evidence. Side-sensor coordinates are not returned as UDFPS bounds. The entire array is validated before classification, so malformed entry order cannot change the result.

vivo's original Neo9 Pro platform log explicitly reports hidden-API blocking for both `FingerprintManager.getSensorPropertiesInternal` and `FingerprintHelper.getFingerprintSensorType`, plus SELinux denials when looking up vendor fingerprint properties. These are actual ordinary-process restrictions. A `NoSuchMethodException` in the app can mean a blocked hidden method, and an empty property can mean denied access; the library does not relabel either as confirmed hardware absence. Public fingerprint feature declarations establish presence only and are included in diagnostic JSON without inferring placement.


## Vivo alternative channels verified - 2026-10-26

The earlier `UNKNOWN`/`UNAVAILABLE` results describe the existing collectors, not proof that vivo exposes no usable channel. A separate ordinary-app probe found two missing channels. The production 0.4.0 library now incorporates both channels. Previously built diagnostic hashes below describe the investigation stage; the integrated build has separate E2E evidence.

### Exported Messenger service

The [2018 BMPrinter Vivo client](https://github.com/cangwang/BMPrinter/blob/341ec6f519320e71bf8624bf8ba505dfeb3c065e/bmprinter/src/main/java/com/bcm/bmprinter/fingerprint/brandfingerprintutils/VivoFingerprintInsets.kt) supplied a concrete service-protocol lead. A [version-specific mirrored WeChat client](https://github.com/AsokaKrsna/DeChat/blob/e6ff0a3c5166eb7811c3fd42b40dd0e9886bdc72/c/com/tencent/kinda/framework/app/FingerprintInsets.java) supplied the newer component and version 1.1. Mirrored/decompiled sources are investigation leads, not a published vendor API guarantee. No vendor/client implementation was copied into runtime source.

| Protocol fact | Value |
| --- | --- |
| Newer component | `com.vivo.fingerprintui/.export.MessengerService` |
| Legacy component | `com.vivo.udfingerprint/.service.MessengerService` |
| Request | Android `Messenger`, message `10000`, major/minor in message arguments and bind Intent |
| Identity | `token` contains the actual caller package; `replyTo` is the caller's own Messenger |
| Reply | Bundle string `query_json`, with version, `has_under_display_fingerprint`, `icon_state`, `icon_position` |
| Cleanup | Message `10002`, followed by unbinding |

An independently written, ignored probe APK used target SDK 36, its own application ID/UID, and no hidden-API exemptions, root, authentication or enrollment. On iQOO Neo9 Pro `V2339A`, Android 14, build `UP1A.231005.007`, incremental `eng.compil.20241023.182820`, the newer system-owned/exported service reported no required permission and replied to the genuine ordinary caller. Package metadata reported `enabled=false`; the probe did not change enablement and actual binding still replied. This metadata alone must not be treated as proof that a bind cannot work.

All three fresh portrait -> landscape -> portrait queries returned protocol 1.1, `has_under_display_fingerprint=true`, `icon_state=-1`, and `icon_position=[519,2422,741,2644]`. Display dimensions/rotation changed 1260 x 2800 / 0 -> 2800 x 1260 / 1 -> portrait / 0. The rectangle remained in the portrait coordinate convention. For this observed configuration, a current-natural transform produces landscape `[2422,519,2644,741]`; feeding the unrotated reply directly to a landscape layout would be wrong. Changed resolution, folds and external displays were not verified. This is a configured UI-icon rectangle, not independently calibrated physical sensor geometry or proof of a currently displayed authentication target.

On iQOO Z7x `V2272A`, Android 13, build `TP1A.220624.014`, incremental `eng.compil.20260114.152636`, the newer service returned `BIND_FALSE` and no rectangle. The legacy service was not found by the probe on either device. Binding failure does not establish sensor placement.

### Vivo module property

The same ordinary Z7x process could read `persist.sys.fptype=sidefp_fpc_fpc1552`. This is explicit side-placement evidence and agrees with the [official Z7x specification](https://www.vivo.com.cn/vivo/param/iqooz7x). Its firmware differs from the earlier 20230324 cloud instance. A subsequent formal-library run also classified SIDE on that earlier firmware; see the production E2E record. The inspected client also recognizes `udfp_` module prefixes. The production parser recognizes explicit `sidefp_` and `udfp_` schemas, retains each property's provenance and rejects conflicting classifications. It must not infer optical/ultrasonic subtype from an opaque module vendor name, or infer rear/home placement from an unrecognized string.

The Neo9 Pro's `sys.fingerprint.boot`, `vendor.fingerprint.boot` and `persist.sys.fptype` read empty. Both devices reported `persist.vivo.fingerprint.front=true`, despite different placement; that flag is insufficient to establish UDFPS. Empty reads cannot distinguish absence from access filtering.

### Other candidates and integration constraints

The probed `FtFeature` key `vivo.hardware.fingerprint` returned false with `support_indisplay=ABSENT` on both devices. This does not establish hardware absence, and does not exhaust other version-specific feature keys. The two tested fingerprint-configuration XML paths were not visible/readable. Historical helper/IFAA implementations contain hard-coded coordinate defaults; those defaults are not acceptable substitutes for a validated report.

The integrated vivo service adapter and module-property classifier use bounded asynchronous callbacks, genuine identity, owner/permission checks, supported-version validation, strict boolean/rectangle parsing, default-display and sensor scoping, cleanup and the existing display-change invalidation. A reported UDFPS flag establishes generic `UNDER_DISPLAY`, not optical versus ultrasonic. A bind failure supplies no negative hardware classification. Fine subtype/rear/home identification still needs explicit metadata or an existing authorized host provider. Main-thread blocking and stale shared caches would undermine the normal query contract.

Complete evidence and exact probe hash are in the private `.work/e2e/vivo-alternatives-20261026/summary.json`. Twenty complete section records cover four fresh stages on two devices; original WebUI, exit and release captures are retained. Section JSON was read from the rendered platform table; no byte-identical platform log export or native device PNG is claimed. The local-only probe lives in `.work/vivo-alternatives/`, builds through an init-script module on Linux, and is excluded from normal builds and Git.

## Further mainstream coverage investigation - 2026-10-26

A [version-specific Samsung framework mirror](https://github.com/488315/samsung_framework/blob/f090fe5941476ffeb9fa685b311de674f2620491/framework/sources/android/hardware/fingerprint/FingerprintManager.java) supplied a further method-signature lead, `semGetSensorPosition`, and named `SEM_SENSOR_POSITION_DISPLAY`, `POWER_KEY`, `REAR` and `HOME_KEY` constants. This mirror is not an official vendor API contract or evidence that third-party access works on current stock firmware. The independently written collector matches the returned integer to available named constants from the live manager class, rather than applying AOSP sensor-type numbers or hard-coded Samsung numbers. It requires a public fingerprint feature declaration and display 0, retains generic under-display placement without inventing technology subtype, and collects type separately from the icon rectangle so a denied icon read does not discard type. Existing resolver sensor scoping and conflicts still apply. APIs 23/31/36 regression fixtures cover non-AOSP constant values, missing constants, denied geometry, unknown values, missing feature, external display and conflicts. Actual Samsung firmware execution of this new path remains pending.

The optional default-off specification catalogue also includes Xiaomi 14 exact model `23127PN0CG`: [GCF's own certification record](https://www.globalcertificationforum.org/pub_product/11842.html) maps the identifier and marketing name, while [Xiaomi's own specification page](https://www.mi.com/sg/product/xiaomi-14/specs/) declares in-screen fingerprint. [Huawei's own P30 Pro specifications](https://consumer.huawei.com/se/phones/p30-pro-back-up/specs/) identify `VOG-L29` and an in-screen fingerprint sensor. Both entries intentionally remain generic `UNDER_DISPLAY`; neither supplies coordinates or a fine optical/ultrasonic subtype. Near regional model names remain unmatched until independently sourced. This is specification fallback, not a new runtime or physical-device result.

The public Android 37.2 SDK and current [BiometricManager reference](https://developer.android.com/reference/android/hardware/biometrics/BiometricManager) expose biometric-strength information, not a general public fingerprint-placement/bounds query. This investigation did not find a new public SDK accessor that resolves the ordinary-app geometry gaps. Restricted system APIs remain restricted; authentication success and fingerprint presence do not identify sensor form.

Nothing's [Phone (4a) product page](https://nothing.tech/products/phone-4a) did not expose its full sensor table through the text-only page extractor. The ordinary public HTML response itself includes the supplier-authored `Fingerprint Sensor` entry with `In-screen optical scanner`. The archived HTML SHA-256 is `2ea2cee0d82f91d999a5cb2ed103fc6ddfb6faab46dc76fdb5418d759aca771d`. The exact `A069` identity is independently present in the [supplier's EPREL product record](https://eprel.ec.europa.eu/fiches/smartphonestablets20231669/Fiche_2555643_EN.pdf), registration 2555643, and the existing Firebase Phone (4a) run reported manufacturer `Nothing`, model `A069`. This supports an optional optical type declaration for that exact model, not a claim that the runtime API was available in the earlier test. The distinct `A069P` identifier remains excluded.

## Honor exact-model declaration - 2026-10-27

HONOR's own [Magic6 Pro manual](https://www.honor.com/content/dam/honor/pl/support/product-manual/honor-magic6-pro-ug.pdf) identifies `BVL-N49` on the final page and describes the on-screen fingerprint reader on printed page 137 (PDF page 140). This supports a generic `UNDER_DISPLAY` entry for exact manufacturer HONOR/model BVL-N49 in the default-off specification fallback. It does not establish optical/ultrasonic subtype, runtime access or coordinates; BVL-AN16 and Huawei/manufacturer mismatches remain unmatched. No Honor hardware E2E is claimed.


## OPPO specification follow-up - 2026-10-28

The ordinary-app cloud corpus includes completed workflows for OPPO `PEQM00` and `PFVM10` with a declared fingerprint feature but runtime `UNKNOWN` / `UNAVAILABLE`. [OPPO Reno6 specifications](https://www.oppo.com/cn/smartphones/series-reno/reno6/specs/) bind PEQM00 to under-display fingerprint, without a technology subtype. [OPPO A56 specifications](https://www.oppo.com/cn/smartphones/series-a/a56/specs/) bind PFVM10 to side fingerprint. These exact entries extend the default-off type-only fallback. Similar product names or regional variants are not model aliases; unreadable APIs and catalogue labels do not establish placement. The prior cloud corpus does not validate the newly extended fallback binary.

### IFAA capability gate - 2026-10-28

A fresh OPPO A56 `PFVM10` / API30 / `PFVM10_11_A.05` seven-stage ordinary-app run exposed a false positive: official specifications identify SIDE, but IFAA returned `[265,1227,455,1417]` on the 720x1600 display. The Oplus [IFAA service source](https://github.com/LineageOS/android_hardware_oplus/blob/d5489134cbc6a7dc6585cc04bd6ecfe4d4100dec/packages/IFAAService/src/org/ifaa/aidl/manager/IfaaService.kt) explains why: `getExtInfo` returns a rectangle independently of capability, with default diameter 190 and bottom offset 278, reproducing this exact rectangle. Its separate capability reports 1 for side/back/front and 17 for optical/ultrasonic. These metadata coordinates alone therefore do not establish an under-display sensor.

The provider now requires a nonnegative integer capability containing both fingerprint and screen fingerprint bits (`0x11`) before reading the extension. Missing, denied, unsupported or malformed capabilities produce no IFAA type or geometry. The provider remains opt-in, reports generic UNDER_DISPLAY only, and does not override runtime evidence with specification data. Tests replay the actual A56 payload, unsupported masks, additional biometric bits, absent methods and permission denial.


## Samsung ordinary-app ROM features - 2026-10-28

Samsung RTL Galaxy A56 actual `SM-A566E`, API35, firmware incremental `A566EXXS4AYE6`, supplied `/system/etc/floating_feature.xml` with the exact `SEC_FLOATING_FEATURE_BIOAUTH_CONFIG_FINGERPRINT_FEATURES` value `optical_display_phone`. Its normal system-file access mode allows the ordinary application to read this firmware declaration without shell identity or reflection. The initial same-device run had seven UNKNOWN/UNAVAILABLE results because hidden Samsung methods were unavailable. A fresh R8 application run with the XML collector returned OPTICAL in all seven stages, including specifications off, actual landscape and restored portrait. Installed APK bytes were independently read back and verified.

The collector requires Samsung manufacturer, declared public fingerprint hardware and the default display. It bounds input size, validates the XML root and closing root, rejects DTDs, duplicate or nested target fields, and accepts only the observed exact token. Unrecognized declarations do not infer subtype or placement. The declaration has no coordinates; unavailable SystemUI rectangles remain unavailable. This is firmware-declared type evidence, not independent physical sensor calibration. Original firmware and ordinary-app JSON are retained in the private Samsung RTL evidence directory.

The separate Flip7 `SM-F766B` / API36 / `F766BXXU1AYFP` firmware declares `capacitive_powerkey_phone`. A fresh installed-byte-verified ordinary-app run of the exact-token extension supplied SIDE in all seven stages with the specification catalogue disabled/enabled, and no geometry. Actual orientation and normal finishing exit were confirmed in JSON/logs; visual cloud output was unavailable. Other capacitive tokens and optical/ultrasonic substrings remain rejected until independently established.

S25 Ultra `SM-S938B` / API36 / `S938BXXU7ZYLF` independently supplied `ultrasonic_display_phone` in its system XML. The exact ULTRASONIC declaration was then verified through seven fresh ordinary-app records with installed-byte-verified R8 APK `d143357207b247b9d69b0983e57e4d4cb34dc5cdb7d5cdc4bf51461a65b77f1a`. The default runtime query and all option stages returned ULTRASONIC while geometry remained UNAVAILABLE. Actual landscape/portrait and matching finishing=true exit were preserved. Native screenshot streaming timed out for one landscape stage and recovery, so the visual record is incomplete. No firmware feature declaration supplies coordinates or independently proves chip calibration.

The different Russian S26 Ultra `SM-S948B` / API37 / `S948BXXS4BZIG` retained the same exact ultrasonic token and passed the final ordinary-app query workflow. Samsung scope is now eight selected marketing models with exact firmware/artifact boundaries recorded in [E2E](e2e.md#samsung-rtl-ordinary-app-regression---2026-10-28). This does not make inaccessible icon geometry available, establish physical calibration or cover all Samsung firmware.


The expanded Samsung catalogue's Tab S12 Ultra actual `SM-X946B` / API37 / `X946BXXU1AZH3` exposes `optical_display_tablet` in its ordinary-readable firmware XML. The exact value was added to the optical classifier and independently exercised through a fresh installed-byte-verified ordinary-app run. This extends firmware type declarations to the observed tablet form; it supplies no coordinates and does not infer unobserved tablet tokens. Landscape-only cloud behavior remains a separate partial orientation result in [E2E](e2e.md#full-samsung-catalogue-follow-up---2026-10-28).


## Transsion optical declaration - 2026-10-28

Static inspection of the public [Transsion framework components](https://github.com/boydaihungst/tecno-reverse-engineering/tree/7c37d72fe21b1f64620ec0bedfc562fd4f098b32) found actual FingerprintManager DEX instructions reading `ro.optical_fingerprint_support` and comparing its raw value with exactly `1`. The collector accepts this positive declaration only for TECNO/Infinix manufacturer names and display 0, emits OPTICAL type evidence with the property name and ROM_PROPERTIES provenance, and leaves geometry unavailable. Other values, missing properties and denied reads cannot imply SIDE, REAR, HOME or hardware absence. Existing type conflict and sensor-scope handling still apply.

The component repository does not establish the original handset or stock build. Ordinary-app readability and applicability on the Firebase-tested X6525/KI5k are not verified. No target coordinate JSON was obtained and no geometry parser or coordinate default was added. Static inspection and Linux regression are not device E2E coverage.


## Pixel 8 Pro exact-firmware coordinate investigation - 2026-10-30

The [husky BP1A.250505.005.B1 vendor import](https://github.com/TheMuppets/proprietary_vendor_google_husky/commit/b172714b02563a3871dc7d04cc217e9da6585b04) names the same build as the archived Pixel 8 Pro run. Read-only analysis of its fingerprint APEX and Goodix configuration library found display-type-specific coordinate tables, selected using CDT platform/product values and panel information. This is stronger provenance than applying a nearby Pixel overlay, but on-device component hashes and the selected branch are not yet verified. No default coordinate profile was added.

The diagnostic now checks the exact public metadata paths `/sys/firmware/devicetree/base/chosen/plat/{platform,product,stage,major,minor,variant}` and `/sys/devices/platform/exynos-drm/primary-panel/panel_name` on Google husky only. It records bounded raw bytes, big-endian CDT values when valid, and explicit missing/denied/error states. Vendor APEX/library SHA-256 values are collected without loading or executing vendor binaries. These files are diagnostic evidence, not a new library permission or a sensor location result. An independently labelled instrumentation-shell capture remains separate from ordinary-app reads.

The harness shell fix follows the [AOSP UiAutomationConnection implementation](https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/java/android/app/UiAutomationConnection.java), which calls `Runtime.exec(command)`. On API31 and later, the test writes fixed scripts to `sh` through public [executeShellCommandRw](https://developer.android.com/reference/android/app/UiAutomation#executeShellCommandRw(java.lang.String)); the older-API fallback uses a script in the test's own external output directory. The library itself does not execute these commands. Android physical validation is pending free quota; host process-contract checks do not replace it.


## Huawei screen technology and logo-point fallback - 2026-10-30

Read-only inspection of pinned [EMUI9 FingerprintManagerEx](https://github.com/dstmath/HWFramework/blob/672bb34094b8780806a10ba9b1d21036fd808b8e/Mate20-9.0/src/main/java/huawei/android/hardware/fingerprint/FingerprintManagerEx.java) and [EMUI11 FingerprintManagerEx](https://github.com/dstmath/HWFramework/blob/672bb34094b8780806a10ba9b1d21036fd808b8e/MATE-20_EMUI_11.0.0/src/main/java/huawei/android/hardware/fingerprint/FingerprintManagerEx.java) shows the vendor numbering1=optical,2=ultrasonic for screen sensors. It differs from Android sensor-type numbering. A failed or unknown type call keeps generic screen evidence and does not suppress valid geometry. Exterior type0 does not identify side/rear/front and is not mapped to an invented placement.

In those published framework/server mirrors, EMUI9 getHardwarePosition queries1110 and the server converts normalized HAL coordinates into initial/native-display pixels. EMUI11 getHardwarePosition queries1130 logo position, which can decode a single packed point twice and yield a zero-area rectangle. The separate public getSpotPosition still queries1110 sensing-area coordinates. The adapter therefore prefers a valid in-frame getSpotPosition rectangle and falls back to a valid legacy getHardwarePosition rectangle. Point-only results remain unavailable; no radius is guessed. Both paths share the existing display transform.

These are versioned decompiled mirrors, not official public SDK guarantees or a byte comparison to the tested P30. File hashes and source paths are retained in .work/research/huawei-runtime-20261030/findings.json. New physical-provider E2E remains pending. Historical P30 generic type/rectangle evidence stays bound to its original binary.
