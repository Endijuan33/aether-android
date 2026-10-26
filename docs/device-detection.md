# Device detection

AETHER captures public `Build` fields: manufacturer, brand, model, device,
product, fingerprint, Android release/API, build ID/display and security patch.
`AndroidDeviceInfoProvider` runs on `Dispatchers.IO`. A pure
`DeviceProfileDetector` selects the manufacturer family, OEM family, version,
profile, confidence and limitations. The process-scoped catalogue repository
loads one snapshot; retry refreshes it without reconnecting ADB or scanning apps.

Optional software versions come from a fixed allowlist of `/system/bin/getprop`
keys. Each process has a 200 ms timeout and a bounded result. No shell
interpolation, hidden API, reflection, root or privileged permission is used.
Unavailable properties remain unavailable. Samsung's version is displayed as the
raw OEM property, not converted into an invented marketing version.

Xiaomi/Redmi/POCO share the Xiaomi manufacturer family. HyperOS requires an
observed `ro.mi.os.version.name`; MIUI uses `ro.miui.ui.version.name`. Samsung,
OPPO, realme, OnePlus and vivo have separate software/version observations;
OnePlus and vivo are not assigned an ambiguous ROM from brand alone. Motorola,
ASUS, Sony and Pixel are represented too. Unknown versions, missing models,
conflicting brands and unrecognized software select `android-baseline` and
lower confidence. A recognized manufacturer is not proof of the installed ROM.

The POCO F6 profile requires Xiaomi/POCO, observed HyperOS, API 36, and model
`POCO F6`, `24069PC21G` or `24069PC21I`; a reported codename must be `peridot`.
Google's [supported-device list](https://storage.googleapis.com/play_public/supported_devices.csv),
reviewed 2026-10-26, lists:

| Brand | Marketing name | Device | Model |
|---|---|---|---|
| POCO | POCO F6 | peridot | 24069PC21G |
| POCO | POCO F6 | peridot | 24069PC21I |

This identity evidence does not establish HyperOS operation safety. The F6 Pro
is excluded. Build metadata can be changed by custom ROMs; confidence describes
the consistency of available observations, not firmware authenticity.

Device Information is available from the dashboard's device/recommendations
button. It shows model, manufacturer, codename, Android, skin, version, profile,
confidence and limitations. Fingerprints and build identifiers remain internal
and are not rendered or exported by this feature. No device identifiers or
catalogue data are sent to a server.

Tests cover the target profile and model aliases, other supported OEM families,
unknown models, missing properties, confidence, conflicting identity and Android
version mismatches. Automatic device/OEM detection is **USER-VERIFIED ON PHYSICAL
DEVICE** on POCO F6 / Android 16 / HyperOS, according to developer-reported manual
testing confirmed on 2026-10-26. Exact displayed fields, HyperOS build and
confidence value were not supplied. Automated instrumentation execution and
other-OEM validation remain pending; see the [hardware record](device-management-report.md#hardware-validation--developer-reported-manual-testing).
