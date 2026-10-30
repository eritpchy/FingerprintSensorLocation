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
| Positive power-button sensor resource | Implemented | `config_is_powerbutton_fps=true` supplies type-only `SIDE` on the default display; false/missing does not imply another placement |
| Side fingerprint location resource | Implemented | Valid nested `config_sfps_sensor_props` arrays, matched to the current display; type-only, never UDFPS coordinates |
| Framework UDFPS resource overlay | Implemented | Default display only; exactly three integer values; resource may be absent |
| AOSP SystemUI report | Opt-in caller bridge | Scoped AuthController and UdfpsController blocks, actual sensor ID, registered state and matching display configuration required; current-natural bounds; no automatic shell/root access |
| OPlus icon properties | Implemented | Optical/ultrasonic type plus both size and center-bottom properties required; properties can be denied to ordinary apps |
| Xiaomi legacy FOD properties | Implemented | Both Android 8 and Android 9+ property names; default display only |
| Xiaomi/Samsung ROM AIDL properties | Implemented | Explicit sensor type or independently known type required; display association preserved |
| Samsung named position API | Runtime type observed on two stock cloud firmwares | `semGetSensorPosition` is interpreted through the same class's named `SEM_SENSOR_POSITION_*` fields, separately from rectangle access; ordinary apps on SM-A315N/API31 and SM-S928U1/API34 returned generic `UNDER_DISPLAY`; no subtype or geometry inferred; full harness rerun pending |
| Samsung ROM fingerprint features XML | Seven-stage ordinary-app type evidence on eight recent models, API35/36/37; six on final APK | Exact `optical_display_phone` / `optical_display_tablet` / `capacitive_powerkey_phone` / `ultrasonic_display_phone` declarations; type only, no geometry; hidden API and optional specifications are not required |
| Samsung One UI icon-rectangle API | Implemented, provisional | `semGetFingerIconRectInDisplay`; current logical space; stock versions need validation |
| Vivo exported Messenger service | Implemented and ordinary-app checked | Neo9 Pro API 34 formal-library default, landscape, portrait restore, IFAA and default restore returned generic UDFPS and transformed icon bounds. Z7x API 33 bind returned false; details in [research](research.md#vivo-alternative-channels-verified---2026-10-26) |
| Vivo module-property classifier | Implemented and ordinary-app checked | Z7x API 33 formal-library five-stage run classified `SIDE` from `persist.sys.fptype`; no guessed rear/home/subtype mapping |
| Vivo helper API | Implemented, provisional | Numeric display-ID variant; sensor width/height extensions read when present; stock versions need validation |
| Huawei hardware-position API | Implemented, provisional | In-screen sensor predicate required; native coordinate transform; stock versions need validation |
| IFAA location extension | Opt-in, provisional | Requires explicit IFAA screen fingerprint capability (`0x11`); default display only; pixel bounds; current-natural convention must be validated for the target implementation |
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

The 0.2.0 instrumentation run repeated the six query stages on both local physical devices. PKG110 retained its OPlus result; the SystemUI bridge correctly rejected the empty rectangle as `INVALID_DATA`. TB375FC supplied no supported SystemUI UDFPS report and remained `UNAVAILABLE`. These negative cases validate rejection and fallback behavior, not a positive physical-device SystemUI geometry path. Its landscape request changed rotation from 3 to 1, but the subsequent portrait request returned rotation 3 while retaining landscape dimensions; portrait coverage is not claimed. See the versioned [E2E record](e2e.md).

- Coordinates must be finite, ordered and inside the selected display after transformation.
- Side/rear/home sensors are classified before using geometry.
- An explicit sensor selection cannot use an unbound fallback.
- Conflicting source centers fail with `CONFLICT` rather than silently preferring one.
- Empty/missing properties cannot acquire example constants.
- Device configuration, rotation and resolution are re-read for each call; results are not model-cached.

The library returns reported geometry. It cannot guarantee a location for every closed-source firmware or circumvent access restrictions. Preserve an explicit unsupported path in the application.

## Type and location are independent

From 0.4.0, `sensorType`, `typeStatus` and `typeEvidence` expose classification even when geometry cannot be used. Framework type survives missing/invalid location fields, a failed location accessor and an unconfirmed constructor placeholder. Vendor optical/ultrasonic declarations survive absent icon geometry. A registered, display-matched SystemUI sensor declaration may supply type when its bounds are null or invalid; its geometry still has to pass all existing checks. No system command is run by the library.

The AOSP numeric contract is 1 rear, 2 ultrasonic, 3 optical, 4 power button and 5 home button. The 0.4.0 adapter fixes the optical/ultrasonic reversal in previous releases. Historical evidence files retain the values emitted by their original binaries. Type-only evidence cannot corroborate coordinates. Optical and ultrasonic conflicts, side/rear/home conflicts and multiple sensor IDs remain explicit instead of selecting an arbitrary report.

The 0.3.0 cloud instrumentation round on 2026-10-26 completed on OnePlus 10T `CPH2415` (API 34), motorola edge 30 (API 34), Galaxy A31 `SM-A315N` (API 31), Pixel 8 Pro (API 35) and Nothing Phone (4a) `A069` (API 36). All five harnesses passed. The first two returned OPlus-property and framework-resource bounds respectively; the other three returned unavailable in both ordinary-app and explicit instrumentation-shell-report queries. None establishes independently calibrated sensor positions, and the two successful queries do not establish successful SystemUI parsing. See [E2E evidence](e2e.md).


On 2026-10-26, the 0.4.0 ordinary-app diagnostic also ran on the official vivo cloud's iQOO Neo9 Pro `V2339A`, Android 14. Default, rotation/restore and IFAA-opt-in queries completed with location `UNAVAILABLE` and type `UNKNOWN`; Close restored the launcher. This confirms the negative-result path in that app/firmware context, not successful Vivo provider access or physical sensor placement. X300 Pro was allocated but produced no app query and is not counted as validated. See [the vivo E2E record](e2e.md).


## Historical vivo ordinary-app coverage before service integration

The 0.4.0 follow-up completed default, actual landscape, restored portrait, opt-in IFAA and Close on iQOO Z7x `V2272A` (Android 13) and vivo X20Plus A (Android 8.1). Both returned type `UNKNOWN` and location `UNAVAILABLE`. Public `android.hardware.fingerprint` confirms only a feature declaration, not placement. Product specifications independently describe side and rear placement respectively; the library does not infer either from model names. X20Plus A exercised that collector revision; Z7x predates the atomic validation of malformed SFPS arrays. Firmware identifiers, exact hashes, capture limits and eight complete records are documented in [E2E](e2e.md#readiness-follow-up---2026-10-26).

## Vivo production integration follow-up

The service/property-integrated 0.4.0 diagnostic completed five fresh ordinary-app queries and Close on Neo9 Pro and Z7x on 2026-10-26. Neo9 Pro returned `FOUND` / `UNDER_DISPLAY` / `VIVO_SERVICE`, with portrait `[519,2422,741,2644]`, landscape `[2422,519,2644,741]` and restored portrait bounds. Z7x returned `NOT_UNDER_DISPLAY` / `SIDE` / `VIVO_PROPERTIES` without coordinates in every stage. Neither IFAA opt-in changed the classification. These results supersede the earlier negative collector results for the tested firmware only. They do not independently calibrate the physical chip or prove optical/ultrasonic subtype. Exact firmware, hashes, captures and remaining gaps are in [E2E](e2e.md#vivo-production-library-integration---2026-10-26).

## Optional specification catalogue

Disabled by default. Exact names are intentionally not widened to model prefixes. Only product type is declared; firmware, hardware health and current geometry are not validated by these entries.

| Manufacturer / exact Build.MODEL | Type | Official evidence |
| --- | --- | --- |
| Google / `Pixel 8 Pro` | `UNDER_DISPLAY` | [Google hardware specifications](https://support.google.com/pixelphone/answer/7158570?hl=en), Pixel 8 Pro authentication section; no subtype inferred |
| samsung / `SM-A315N` | `OPTICAL` | [Samsung Korea model identity](https://www.samsung.com/sec/support/model/SM-A315NZSAKOO/) and [Samsung service's optical on-screen declaration](https://www.samsungsvc.co.kr/solution/25853) |
| samsung / `SM-S928U1` | `ULTRASONIC` | [Samsung firmware model identity](https://doc.samsungmobile.com/SM-S928U1/028699240131/eng.html) and [US S24 series specification sheet](https://images.samsung.com/is/content/samsung/assets/us/2401/business/mobile/phones/galaxy/Galaxy_S24_Series_B2B_Spec_Sheet.pdf) |
| vivo / `vivo X20Plus A` | `REAR` | [vivo X20Plus product](https://www.vivo.com.cn/vivo/x20plus/) and [variant specifications](https://www.vivo.com.cn/vivo/param/x20plus); screen-fingerprint UD variant is excluded |
| vivo / `V2158A` | `SIDE` | [vivo Y32 official specifications](https://www.vivo.com.cn/vivo/param/y32), exact model and side fingerprint declaration; ordinary-app Android 11 runtime query was unavailable on the tested firmware |
| OPPO / `PEQM00` | `UNDER_DISPLAY` | [Reno6 official specifications](https://www.oppo.com/cn/smartphones/series-reno/reno6/specs/), exact model and under-display declaration, checked 2026-10-28; no optical/ultrasonic subtype inferred |
| OPPO / `PFVM10` | `SIDE` | [A56 official specifications](https://www.oppo.com/cn/smartphones/series-a/a56/specs/), exact model and side fingerprint declaration, checked 2026-10-28; regional A55/A93 names are not aliases |
| Xiaomi / `23127PN0CG` | `UNDER_DISPLAY` | [Official Xiaomi 14 specifications](https://www.mi.com/sg/product/xiaomi-14/specs/) and [GCF model certification](https://www.globalcertificationforum.org/pub_product/11842.html); no subtype inferred |
| HUAWEI / `VOG-L29` | `UNDER_DISPLAY` | [Huawei P30 Pro specifications](https://consumer.huawei.com/se/phones/p30-pro-back-up/specs/), explicit model and in-screen fingerprint declaration; no subtype inferred |
| Nothing / `A069` | `OPTICAL` | [Nothing Phone (4a) product page](https://nothing.tech/products/phone-4a), supplier-authored sensor table in returned HTML, [EPREL exact model record](https://eprel.ec.europa.eu/fiches/smartphonestablets20231669/Fiche_2555643_EN.pdf) and prior Firebase model identity; `A069P` excluded |
| Sony / `A402SO` | `SIDE` | [Sony carrier-model identity](https://support.sony.jp/electronics/support/japan-softbank/xperia-10-vi-a402so), [SoftBank key/sensor manual](https://www.softbank.jp/mobile/support/manual/smartphone/xperia10m6/detail/158378/) and [Sony side placement diagram](https://helpguide.sony.net/mobile/xperia-10m6/v1/ja/contents/part_names_24b.html), checked 2026-10-29. Optional type only; no carrier-stock runtime API or screen geometry success claimed. `SOG14` and `XQ-ES44` are not aliases. |
| SHARP / `SH-01L` | `FRONT` | [SHARP exact-model front sensor declaration](https://jp.sharp/products/sh01l/usability.html), checked 2026-10-30. Optional type only, default off; navigation gestures do not establish Android HOME-button type. No screen bounds. New fallback physical E2E pending. |


| HONOR / `BVL-N49` | `UNDER_DISPLAY` | [HONOR Magic6 Pro official manual](https://www.honor.com/content/dam/honor/pl/support/product-manual/honor-magic6-pro-ug.pdf), printed page 137 describes the on-screen reader and final page identifies BVL-N49; checked 2026-10-27, no subtype or geometry inferred |

Sources checked 2026-10-26 except where stated. Catalogue parsing/fallback tests do not count as physical-device runs. The 2026-10-27 partial Firebase run observed Pixel 8 Pro changing from runtime `UNKNOWN` to attributed `UNDER_DISPLAY` when specifications were enabled. Both Samsung devices retained their generic runtime type instead of replacing it with a catalogue subtype. All three still had unavailable geometry. A harness click assertion interrupted later stages and Close, so these observations are not complete E2E passes.

The R8 specification-enabled diagnostic was then run on X20Plus A API 27. Three complete ordinary-app JSON records confirm default `UNKNOWN` / service `ACCESS_DENIED`, option-on `REAR` / `DEVICE_SPECIFICATION`, and actual landscape retaining that option and type. No bounds were generated; the service denial remained visible. Portrait restore was visually observed, but subsequent complete app log records were unavailable. The session was interrupted before option-off, Close and lease-release verification; the full E2E is pending. This is partial validation, not a completed physical-device scenario.

## Latest R8 vivo retest

The latest seven-profile/Samsung-collector R8 binary, SHA-256 `92be442404537a498b2fd7596431fa385226f08139e4b7fdd4f1520c4ef75e83`, completed the seven ordinary-app query stages, Close and lease release on X20Plus A API 27 and Neo9 Pro API 34. X20Plus A's explicit option supplies attributed `REAR`; disabling it restores runtime `UNKNOWN`/access-denied. Neo9 Pro retains its `VIVO_SERVICE` UDFPS region through both option states, IFAA and rotation/restore. This full follow-up supersedes the earlier partial X20Plus run for the latest binary. It does not turn specification metadata into runtime hardware detection or prove all mainstream models. See [the latest R8 E2E record](e2e.md#latest-r8-vivo-cloud-follow-up---2026-10-26).

Latest-R8 Z7x follow-up on 2026-10-27 confirmed runtime `SIDE` from `persist.sys.fptype` on `V2272A` / API 33 / `eng.compil.20230324.004350`. One complete default JSON is retained; later option/rotation/IFAA stages are screenshot-only because the cloud log stream stalled, so they are not a full seven-stage pass. Close and lease release were verified. This is sensor-form metadata, with no side-sensor geometry.

## QR-enabled R8 X90 follow-up - 2026-10-27

Latest R8 diagnostic `22741904fcc3f7d6135ce55673140227465b083ddabb0bf0345f112d05b2614f`, including the Honor exact profile, completed seven ordinary-app queries, Close and release on vivo X90 `V2241A` API34 / `eng.compil.20231031.001223`. Complete JSON came from integrity-checked offline QR screenshots when platform logs failed. All stages retained generic `UNDER_DISPLAY` / `VIVO_SERVICE`; actual portrait 1260 x 2800 region `[519,2399,741,2621]`, landscape `[2399,519,2621,741]`, exact portrait restoration. Optional specifications did not replace runtime evidence. IFAA remained unavailable. This is configured icon metadata, not independent physical calibration; the catalogue resolution differed from the actual display and was not used to infer geometry. See [QR E2E](e2e.md#offline-qr-diagnostic-export---2026-10-27).

The QR-enabled R8 also completed seven complete stages, Close and lease release on Z7x `V2272A` API33 / `eng.compil.20260114.152636`: runtime `SIDE` from `persist.sys.fptype`, actual rotation/restoration, no bounds and no specification override. This adds current-binary evidence on a newer firmware to the earlier 20230324 cases, whose capture limits remain separately recorded.

The same QR R8 completed all seven stages, Close and release on X20Plus A API27 / `eng.compil.20250428.004459`: optional `REAR` / `DEVICE_SPECIFICATION`, default/off `UNKNOWN` / `ACCESS_DENIED`, no bounds, actual rotation/restoration. This supersedes partial QR evidence without changing its specification-only attribution.

Separate cloud snapshot apps also queried iQOO15 `V2505A` API36 / `PD2505_A_16.0.25.7.W10` / `compiler260203214207` while their own public show-when-locked window was above keyguard. Independent runtime/default-off and specifications-on package IDs both returned generic `UNDER_DISPLAY` / `VIVO_SERVICE`, `[600,2303,840,2543]` at 1440 x 3168. The optional fallback did not replace runtime evidence; no optical/ultrasonic subtype or hardware calibration is inferred. Snapshot behavior is separate from interactive rotation/option/Close E2E. The initial P30Pro snapshot attempt remains an application-capture environment block, with zero JSON and no compatibility verdict.

The independent runtime snapshot subsequently recovered a complete ordinary-app result on P30Pro `HUAWEI VOG-AL00` / API29 / `10.1.0.162C00`: generic under-display and `[492,2087,587,2180]` from `HUAWEI_API`, specification fallback off, keyguard still locked. This is positive named-firmware runtime evidence, with no technology subtype, physical calibration or interactive rotation proof. Earlier cloud capture failures are retained as environment observations.

The independent runtime snapshot also queried actual Android17/API37 on iQOO15 V2505A / `PD2505_A_17.0.3.30.W10` / `260630115704`: `VIVO_SERVICE` supplied generic under-display and `[600,2303,840,2543]` at 1440 x 3168, default specifications off. A complete QR JSON was recovered; interaction forwarding remained unverified, so this is snapshot query coverage rather than a full interactive sequence. P20 still supplied zero app records and no compatibility verdict.

X300Pro `V2502A` Android17/API37 / `PD2502_A_17.0.3.21.W10` / `260630162123` completed a new ordinary-app automated seven-stage sequence after a normal nonsecure swipe opened the launcher. Runtime `VIVO_SERVICE` supplied generic under-display and portrait `[519,2036,741,2258]`, actual landscape `[2036,519,2258,741]`, with exact portrait restoration and no specification override. Full JSON for all stages was retained. This is automatic app-control coverage, not cloud touch forwarding or physical calibration; see the automatic-sequence E2E record.


## Explicit community ROM coordinate profile

`CommunityGeometryProfile.INFINIX_X6739_8B45A3C8` is an optional report from the pinned X6739 community ROM XML, with native center `(540, 2172)` and radius `95`. Default queries do not enable it. Its exact product identity is taken from the same revision's `lineage_X6739.mk`: manufacturer `INFINIX`, model `Infinix X6739`. The profile is limited to the primary 1080 x 2400 native frame with matching aspect ratio and declared fingerprint hardware, and uses the common rotation/scaling transform. The caller must explicitly select this ROM report; the library cannot establish stock firmware compatibility from a model match. Existing runtime geometry, conflicts and non-screen types are preserved.

The selected source is `COMMUNITY_ROM_CONFIGURATION`, never independent corroboration or hardware calibration. This adds an explicit community-coordinate option, not a stock-device E2E result. X6525 and KI5k do not match and receive no profile coordinates. See the usage and provenance warning in the README.


## TECNO KI5k exact placement declaration - 2026-10-30

The manufacturer-authored [SPARK 10C user manual](https://device.report/m/03a3cb22f3a0b30d66c3f2ab2a4eea5bb22c2f2d0865b64d01baf8ace389680a_optim.pdf), page 4, labels the power key as the side fingerprint sensor. Page 8 binds the manual to KI5k / FCC ID 2ADYY-KI5K. The [manufacturer product page](https://www.tecno-mobile.in/products/mobile/spark-10c/) separately confirms side placement for SPARK 10C. The optional DEVICE_SPECIFICATION catalogue matches only manufacturer TECNO and the observed full Android model TECNO KI5k, returning SIDE without geometry when runtime type is unavailable. It remains disabled by default and does not match KI5q, bare KI5k or neighbouring models. New-binary physical option/restore validation is pending; the manual is a placement declaration, not runtime discovery or hardware-coordinate calibration.
