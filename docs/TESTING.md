# Verification matrix

All Android instrumentation tests use an API 35 x86_64 emulator, namespaced test vaults, in-memory Room where applicable, and a test-only Compose activity for modal back behavior. They do not clear the user's main vault. Run `scripts/test-emulator.ps1` to build and execute the suite.

| Area | Evidence |
| --- | --- |
| Accessibility rules, actual amount extraction, negative keywords, duplicate event cooldown | JVM rule-engine tests; Android privacy/extraction tests |
| PaddleOCR Chinese recognition and boxes | Android integration test with a synthetic Chinese payment image |
| Payment channel, last-four-digit card validation, eight-category legacy mapping | JVM taxonomy tests; Room 6→7 migration and card CRUD device tests |
| QR framing, scanning, replay rejection and signed two-replica sync | JVM QR tests; Android sync and scanner tests |
| Unlock states, wrong password, retry, rotation-retained work, duplicate submit | Android Keystore-backed `UnlockViewModelDeviceTest` |
| Editor system back, dialog system back, candidate bottom-sheet system back | `BackNavigationUiTest` against the components used by the app |
| Predictive-back gesture from a dirty editor | API 35 Release AVD manual edge-swipe: discard dialog appeared; back closed it and retained editor draft |
| Release install and first-use vault | Signed APK installed on a fresh API 35 AVD; vault created and home displayed |

The UI automation verifies back *dispatch and destination*, not the visual quality of the predictive-back animation.

## Android 16 phone spot check (2026-09-23)

On a connected Meizu 20 Pro (Android 16 / API 36, arm64), the Debug APK installed under the owner profile and opened the first-use vault screen. The Release APK was rejected with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: an older Debug-signed `com.secureledger` package record remained on the phone, including retained data in the Flyme parallel-space profile. This is a signing-key mismatch, not an Android 16 API or ABI failure. No existing package data was removed. The isolated instrumentation suite was started but interrupted when the phone's foreground switched to other apps; **it must not be counted as a passing physical-device run**. The temporary test APK was removed, while the main Debug app remains installed.

Full physical-device test results, OEM permission behavior, camera capture quality, and OCR on actual payment screenshots remain pending until the phone can stay available for testing.
