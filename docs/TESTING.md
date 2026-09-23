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

The UI automation verifies back *dispatch and destination*, not the visual quality of the predictive-back animation. Real-phone behavior, permission prompts across OEMs, camera capture quality, and OCR on actual payment screenshots remain unverified because no physical device is currently available.
