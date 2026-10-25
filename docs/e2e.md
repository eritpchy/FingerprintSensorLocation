# Physical-device and manufacturer-cloud validation

Last checked: 2026-10-25. Scope: this independent library and its diagnostic, not another application's payment or authentication flow.

## Acceptance and limits

The test chain is Linux build -> APK installation -> ordinary-app launch -> fresh query -> actual display geometry -> rendered reported-region outline -> optional IFAA query -> diagnostic exit. Save original screenshots, JSON, launch/install output and app-only logs for each step. A successful query still needs independent system geometry evidence before claiming location agreement. Physical sensor operation requires separate authorized hardware testing and is not implied by a cloud session.

The diagnostic never enrolls a fingerprint, sets a PIN, starts authentication, invokes root, bypasses hidden-API enforcement or contacts a payment backend. Do not change a manufacturer's device-lock or biometric policy to obtain a test result. Ordinary applications, ADB shell and existing root processes can have different access; they are separate cases.

## Completed physical-device run

Diagnostic APK SHA-256:

```text
6a4f7b1bc6e98a748ac4564b6afbc0e37d66b7e3ff58cca328f7f53bc1015db4
```

| Device and context | Actual display | Default query | Rotation/restore query | Opt-in IFAA | Exit |
| --- | --- | --- | --- | --- | --- |
| OnePlus PKG110, Android 16 / ColorOS V16.1.0, ordinary app | Portrait 1264 x 2780, rotation 0 | `FOUND`, OPlus properties | Landscape 2780 x 1264, rotation 1, then portrait rotation 0; `FOUND` in all cases | Query completed using OPlus properties; IFAA provider itself unavailable | Diagnostic left foreground; another existing task became foreground |
| Lenovo TB375FC, Android 16 / ZUI 17.5.10.044, ordinary app | Landscape 2944 x 1840, rotation 3 | `UNAVAILABLE`, no bounds | Device ignored portrait requests; actual display remained landscape; no portrait pass claimed | Query completed; no geometry available | Diagnostic left foreground; preceding application was restored |

PKG110 portrait bounds were `[534.5,2422.5,729.5,2617.5]`; landscape bounds were `[2422.5,534.5,2617.5,729.5]`. Returning to portrait reproduced the original bounds. The independently collected retained SystemUI window frame was `[534,2422][729,2617]` in portrait, consistent with 0.5 px rounding. This is agreement with a retained system layout, not a newly rendered authentication UI or a measurement of the chip. Screenshots show the diagnostic's outline; that outline is derived from the library and cannot corroborate it independently.

In the earlier command-line run, PKG110 root returned the OPlus bounds while ADB shell returned `UNAVAILABLE`; TB375FC root and ADB shell identified a side sensor. The ordinary APK run therefore materially adds application-process evidence: PKG110 can read the property contract from this app context, while TB375FC cannot read the hidden sensor classification from this app context. These results are firmware/context-specific.

Raw screenshots, XML trees, app logs and JSON are archived locally under `.work/e2e/`, excluded from Git. Device addresses, account information, unrelated applications and restoration screenshots are not included in the public record.

## Manufacturer services checked

No manufacturer-cloud device result is claimed below. A service page, device catalogue or login page is not a completed device test.

| Manufacturer | Official service and verified capability | Access/result at this investigation |
| --- | --- | --- |
| Xiaomi | [TestIt guide](https://dev.mi.com/xiaomihyperos/documentation/detail?pId=1523), [console](https://testit.miui.com/): APK upload, remote real devices, up to 1 hour per session and 30 free sessions per day; devices are not rooted | Console reached; Xiaomi account login required; no device allocated or APK uploaded |
| OPPO | [Remote real devices](https://open.oppomobile.com/product/page?page_name=cloudmachine): interactive devices, screenshots and logs; developer registration/authentication flow | Official cloud entry reached; redirected to HeyTap account login; current account quota and available devices not yet verified |
| vivo | [Cloud testing](https://developers.vivo.com/product/d/cloud): interactive remote devices and comprehensive testing | Official Use action redirected to vivo developer-account login; current account quota and available devices not yet verified |
| Samsung | [Remote Test Lab](https://developer.samsung.com/remote-test-lab): free developer access, device catalogue, browser testing | Device list reached; an on-page notice stated devices were currently inaccessible due to urgent server work; screenshot retained; no reservation or test performed |
| Honor | [Cloud-testing documentation](https://developer.honor.com/cn/kitdoc/devtool/20031/guides/introducing.md): free compatibility/stability tests for registered verified users | Public documentation checked; no authenticated task submitted; automated install/launch success alone would not prove geometry |
| Huawei | [Cloud debugging](https://developer.huawei.com/consumer/cn/agconnect/cloud-adjust): advertised 360 free daily minutes for HarmonyOS 5+ devices | The advertised free HarmonyOS 5+ allowance is not evidence that an Android APK can run there; an Android-compatible device/package path must be confirmed before counting coverage |

Samsung's [official support response](https://forum.developer.samsung.com/t/detail?id=17054-Cannot-acess-samsung-wallet-on-Samsung-Remote-Test-Lab&postNo=134184) also documents remote-device restrictions on lock/biometric setup. These restrictions do not excuse skipping read-only geometry checks after device access becomes available, but prevent treating a cloud query as successful physical-finger authentication.

## Next executable cloud cases

For every accessible model/firmware, upload the exact hashed diagnostic APK, save the service's device identity/OS information, and collect the default query, actual rotation change, restored geometry, opt-in IFAA outcome and exit screenshot. Export complete JSON through Copy JSON, app logs or the provided debug bridge. Validate a reported region against an independent permitted system report. Record access denial, ignored orientation, missing enrollment, capture restrictions and service failures explicitly; do not convert them to passes or guess a model's coordinates.

Accounts and remaining free quota must be established inside the official console. Stop before any paid allocation or identity submission that the user has not completed. Credentials, one-time login codes and account documents do not belong in diagnostic reports.
