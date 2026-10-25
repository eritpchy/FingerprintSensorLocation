# Contributing

Keep providers read-only and independent of authentication, enrolled templates and payment applications. Do not invoke root, change hidden-API policy or modify settings from library code.

A provider contribution should include:

- Its API/property/resource contract and source reference.
- Sensor type, sensor/display association, coordinate space and unit.
- Required caller privileges and explicit failure behavior.
- Tests for missing/malformed data, display mismatch, rotation and resolution scaling.
- Sanitized device evidence when claiming a stock model/firmware is supported.

Do not add guessed screen-height percentages, arbitrary fallback constants, a blanket brand support claim or a production model-coordinate database. Configuration fixtures belong in test resources with provenance and do not prove physical-device coverage.

Run `./gradlew :core:test :core:jar :android:testDebugUnitTest :android:assembleRelease :android:lintRelease` on Linux with JDK 21. Build the read-only probe with `bash tools/build-probe.sh`. Device reports should omit serial numbers, IP addresses, stable personal identifiers, fingerprint enrollment data and authentication history.

Build the diagnostic and instrumentation APKs with `:diagnostic:assembleDebug :diagnostic:assembleDebugAndroidTest :diagnostic:lintDebug`. Use the explicit physical-device procedure in [README](README.md). Keep ordinary-app queries and instrumentation-shell report queries separate. A passing harness with `UNAVAILABLE` is a diagnostic behavior result, not a sensor-position pass. Raw SystemUI reports, screenshots of unrelated applications and cloud account URLs must stay out of public evidence.
