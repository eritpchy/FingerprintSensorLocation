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
