# Compatibility and evidence

Evidence levels are separate:

1. **Implemented**: a read-only query/parsing path exists.
2. **Configuration-tested**: source configuration or a regression fixture validates that path's format/geometry.
3. **Device-checked**: the actual library was run on a named model and firmware and compared with an independent system geometry report.

None of these labels implies that every firmware of a brand is covered.

| Mechanism | Implementation | Evidence and limitations |
| --- | --- | --- |
| Android 12 center/radius | Implemented | Hidden/system API access required; malformed and example-default coordinates are rejected |
| Android 13+ location list | Implemented | Stable and vendor numeric display IDs; multiple sensors require selection |
| Framework UDFPS resource overlay | Implemented | Default display only; exactly three integer values; resource may be absent |
| OPlus icon properties | Implemented | Optical/ultrasonic type plus both size and center-bottom properties required; properties can be denied to ordinary apps |
| Xiaomi legacy FOD properties | Implemented | Both Android 8 and Android 9+ property names; default display only |
| Xiaomi/Samsung ROM AIDL properties | Implemented | Explicit sensor type or independently known type required; display association preserved |
| Samsung One UI icon-rectangle API | Implemented, provisional | `semGetFingerIconRectInDisplay`; current logical space; stock versions need validation |
| Vivo helper API | Implemented, provisional | Numeric display-ID variant; sensor width/height extensions read when present; stock versions need validation |
| Huawei hardware-position API | Implemented, provisional | In-screen sensor predicate required; native coordinate transform; stock versions need validation |
| IFAA location extension | Opt-in, provisional | Default display only; pixel bounds; current-natural convention must be validated for the target implementation |
| Trusted system-window geometry | Caller extension | Caller validates window owner/display, meaningful child bounds and lifecycle |
| Honor MagicOS-specific APIs | Open gap | No universal authoritative stock-coordinate interface established by this investigation |
| Meizu Flyme-specific APIs | Open gap | No universal authoritative stock-coordinate interface established by this investigation |
| Unknown OEM/firmware | Explicit unavailable result | No percentage-based or model-name guess |

The synchronous query can block in a vendor Binder/factory. The caller owns cancellation and lifecycle scheduling; perform queries off the UI thread. IFAA is disabled by default because creating a vendor manager can initialize its service.

## Device checks

Actual library probe results are added only after the probe has run. Research-only observations or a successful authentication in another project are not library validation. The diagnostic must not start authentication, alter a real payment page, restart an application or change device settings.

Checked on 2026-10-25 with the library's own diagnostic:

| Model | Firmware | Display | Execution context | Result |
| --- | --- | --- | --- | --- |
| OnePlus PKG110 | Android 16, ColorOS V16.1.0 | 1264 x 2780, rotation 0 | Existing root process | `FOUND`, `OPLUS_PROPERTIES`, `[534.5,2422.5,729.5,2617.5]` |
| OnePlus PKG110 | Same firmware | Same display | ADB shell | `UNAVAILABLE`; required vendor values were not exposed |
| OnePlus PKG110 | Same firmware | Portrait 1264 x 2780, rotation 0; landscape 2780 x 1264, rotation 1 | Diagnostic APK, ordinary app process | `FOUND`; default and IFAA-opt-in calls completed; portrait bounds restored after rotation; IFAA provider itself unavailable |
| Lenovo TB375FC | Android 16, ZUI 17.5.10.044 | 2944 x 1840, rotation 3 | Existing root process and ADB shell | `NOT_UNDER_DISPLAY`, framework sensor 0 classified `SIDE` |
| Lenovo TB375FC | Same firmware | Same display; portrait requests ignored by device | Diagnostic APK, ordinary app process | `UNAVAILABLE`, no bounds; hidden sensor classification unavailable from this context |

On PKG110, the independently read WindowManager report identified a trusted `com.android.systemui` `OnScreenFingerprintPressedIcon` window on display 0, with retained frame `[534,2422][729,2617]`. Each library edge differs by 0.5 px, consistent with integer rounding; both regions are 195 x 195 px. This window was not visible during collection, so this verifies agreement with its retained layout, not a newly rendered authentication screen or the physical chip. The larger animation window was excluded. `corroborated` remained false because the library used only its property provider; this external comparison was performed by the investigator.

On TB375FC, the framework supplied the side-sensor classification. This checks that the library rejects it as UDFPS; it does not establish a screen coordinate or independently measure the hardware.

The APK checks add ordinary application-process evidence for these two devices, but do not establish every display configuration, firmware version, or compatibility with other models. No authentication, enrollment or payment action was performed. APK orientation requests affected only the diagnostic activity. Raw device identifiers are excluded from the public record. See [the E2E record](e2e.md) for the exact tested APK hash, actual steps and manufacturer-cloud access blockers.

## Cloud physical-device checks

The same diagnostic APK was executed through Firebase Test Lab's free Spark allowance on 2026-10-25. Actual application logs confirmed `FOUND` on OnePlus 11 `CPH2449` (Android 14), `NOT_UNDER_DISPLAY`/`SIDE` on OPPO A79 `CPH2557` (Android 14), and `UNAVAILABLE` on Samsung S24 Ultra `SM-S928U1` (Android 16) and Pixel 11 (Android 17). Xiaomi 14's Android 15 firmware blocked the Robo stub's Activity launch, so the library was not exercised on that device. No independent hardware-position calibration was performed on cloud devices. Full evidence and the distinction between platform success and usable geometry are in [the E2E record](e2e.md).

## Geometry acceptance

- Coordinates must be finite, ordered and inside the selected display after transformation.
- Side/rear/home sensors are classified before using geometry.
- An explicit sensor selection cannot use an unbound fallback.
- Conflicting source centers fail with `CONFLICT` rather than silently preferring one.
- Empty/missing properties cannot acquire example constants.
- Device configuration, rotation and resolution are re-read for each call; results are not model-cached.

The library returns reported geometry. It cannot guarantee a location for every closed-source firmware or circumvent access restrictions. Preserve an explicit unsupported path in the application.
