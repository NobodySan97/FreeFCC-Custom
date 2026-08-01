# Helper APK Provenance for DJI RC2

Verification Date: 2026-07-24.

Checked local archive `freefcc-helpers.zip`:

- APK content size: 16,589,690 bytes;
- Archive SHA-256:
  `f2f8d77ab384c9c0a0ebd9d6f00115b7dfbd3bbe0de56dca9967e83cf64b9fa2`.

## Package Composition & Signatures

| File | Package / Version | APK SHA-256 | Signer Certificate SHA-256 | Conclusion |
|---|---|---|---|---|
| `01_PackageInstaller.apk` | `com.android.packageinstaller`, `11` (`30`) | `523361acbe62587fa61e00a92369e87daa0d812232b8942deba67771ccf2633a` | `a4aa1cdd2ea580cbbe67486b5f6f3cfea83f488889995afa70793daa516687da` | DJI-signed system package; byte-for-byte matches RC Pro 2 OTA builds 139 and 576 |
| `02_FileManager.apk` | `com.android.documentsui`, `0.20.12.23-7ab9a2e1` (`121`) | `b7a943cf1af7351da9135eeabfa3554f4ca5c9174ebcfb547e21bca030011b69` | `a4aa1cdd2ea580cbbe67486b5f6f3cfea83f488889995afa70793daa516687da` | DJI-signed DocumentsUI; exact source DJI build not found in local corpus |
| `03_ATVLauncher.apk` | `ca.dstudio.atvlauncher.pro`, `0.1.21-pro` | `4bd6891e6762907857b9ad3d3182af4eac05bba1e33a128ababb72796e9e9d27` | `00dab5f09ba1aa2eff972d1c1f5ad14a9172ce09c51c588d10f63bb7fa9f9eb2` | Third-party launcher, not signed by DJI |
| `04_Edge Gestures.apk` | `com.ss.edgegestures`, `2.0.1` | `7c5c6ec02ba45f09a392b5249e0f1f668f285397dfd61657d566851075aa6864` | `3b61c2a82aff9f7652ffe0b04be3c8f248b5e1aa7063f1a3846f0cf5c778628a` | Third-party app; not needed in current install flow |

DJI certificate DN for the first two APKs:
`EMAILADDRESS=dji@dji.com, CN=DJI, OU=DJI, O=DJI, L=ShenZhen, ST=GuangDong, C=CN`.

The signature proves origin from the owner of the private DJI signing key, but does not in itself prove which public firmware build the file was extracted from.

## Comparison with Local OTAs

`01_PackageInstaller.apk` byte-for-byte matched:

- RC Pro 2 `V55.31.01.39/139`:
  `system/system/priv-app/PackageInstaller/PackageInstaller.apk`;
- RC Pro 2 `V55.31.05.76/576`:
  `system/system/priv-app/PackageInstaller/PackageInstaller.apk`.

This provides strong local evidence that the first helper is taken from an official DJI system image rather than just re-signed with a matching certificate.

`02_FileManager.apk` uses DJI package `com.android.documentsui` and contains DJI provider `com.dji.providers.media.documents`. Available RC Pro 2 OTAs contain an earlier `dpad_documentsui.apk`:

| Field | Helper | OTA build 139/576 |
|---|---|---|
| Version | `0.20.12.23-7ab9a2e1` (`121`) | `0.20.03.18-55be80c7` (`113`) |
| SHA-256 | `b7a943cf...011b69` | `6b46b41d...14e21` |
| Certificate | DJI `a4aa1cdd...687da` | Same DJI certificate |

Therefore, FileManager is indeed DJI-signed, but comes from another or newer DJI system build not present in the local corpus. Searching for the exact version, provider, and hashes yielded no publicly indexed match; this prevents identifying the exact source model/firmware.

## Minimal Installation Chain

The current RC2 SD-card flow requires:

1. `01_PackageInstaller` — installed first;
2. `02_FileManager` — enables standard APK selection after restart;
3. `03_ATVLauncher` — opens Files and then FreeFCC Custom;
4. FreeFCC Custom APK.

`04_Edge Gestures` is not required: after its initial manual launch, FreeFCC Custom starts via its boot receiver, and its interface can be opened from the persistent notification. Combining DJI system packages and a third-party launcher into a single regular APK without system signature/privileges is impossible: Android does not allow a single APK to install or replace other packages and acquire their platform permissions.

The archive contains third-party APKs, so public redistribution rights and corresponding licenses should be verified separately from technical signatures.
