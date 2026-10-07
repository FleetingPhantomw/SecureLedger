# Verification matrix

## Current regression 2026-10-07

Debug build: `testDebugUnitTest assembleDebug assembleDebugAndroidTest` succeeded;45 JVM tests,zero failures. Updated APKs installed with `-r` on API35/x86_64 emulator and Meizu20Pro Android16/API36 arm64 phone without clearing main-package data.

Initial instrumentation runs discovered19 tests and completed without failures (24.367s emulator,45.9s phone). Subsequent regressions passed on the emulator (25.263s,27.559s) and on the phone (44.392s,45.665s,45.605s). **17 executed per device;2 optional scenarios skipped**: private receipt-fixture benchmark and explicitly opt-in real two-device scenario. Skips are not passing execution results. Overlay prompt tests executed on both devices with their permitted overlay state.

One intervening phone run reported2 failures before the two passing repeats. The abbreviated output did not preserve failure stacks and test-runner logcat no longer contained them. The cause is unresolved, not evidence of a fixed production defect; repeat physical-device UI regression and retain complete output before release.

The separate two-device scenario passed six stages: phone request, emulator invitation, phone join/write/sync, emulator selected settlement, phone independent approval, emulator merge. The labelled fixture retains expenses12000/4501/7500 cents,total24001 cents; equal shares12000/12001 cents,phone pays45.00 difference,two valid independent signatures. Exchange used test-file transfer through ADB, not a claim of manual completion of every OEM share sheet. Ordinary suites remain isolated; only this explicitly authorized scenario writes its labelled fixture project in the real vault.

Main-app UI checks: persisted appearance settings; emulator monthly project totals, bars/donut, selected-bill AA preview and2/2 confirmations. The emulator background measured #000000. A later phone capture instead showed #2E2E2E despite the dark preference; the device-specific cause is unresolved. Native night resources and standard Force Dark opt-outs compile and all45 JVM tests pass, but the final compatibility change has not received a new device-suite run or screenshot verification. Further investigation stopped at the user's request; do not claim the phone color issue fixed. Parser tests cover notification rejection and cross-source fusion. Direct AA-file routing only prefills explicit review; it never signs automatically.

Remaining: actual protected payment pages,sustained OEM background behavior,real-camera quality and final Release revalidation. Historical notes below are retained, not current claims that the phone is unavailable.

The ordinary Android instrumentation suite uses an API35/x86_64 emulator or API36/arm64 phone, namespaced test vaults, in-memory Room where applicable, and a test-only Compose activity for modal back behavior. It does not clear the user's main vault. The explicitly opted-in two-device scenario writes only its labelled fixture project. Run `scripts/test-emulator.ps1` to build and execute the emulator suite.

| Area | Evidence |
| --- | --- |
| Accessibility rules, actual amount extraction, negative keywords, duplicate event cooldown | JVM rule-engine tests; Android privacy/extraction tests |
| Overlay delivery and user-choice prompt | API 35 `PromptDispatcherDeviceTest` with overlay app-op; Android 16 OEM protected-payment behavior still requires a phone check |
| PaddleOCR Chinese recognition and boxes | Android integration test with a synthetic Chinese payment image |
| Payment channel, last-four-digit card validation, eight-category legacy mapping | JVM taxonomy tests; Room 6→7 migration and card CRUD device tests |
| QR framing, scanning, replay rejection and signed two-replica sync | JVM QR tests; Android sync and scanner tests |
| Unlock states, wrong password, retry, rotation-retained work, duplicate submit | Android Keystore-backed `UnlockViewModelDeviceTest` |
| Editor system back, dialog system back, candidate bottom-sheet system back | `BackNavigationUiTest` against the components used by the app |
| Predictive-back gesture from a dirty editor | API 35 Release AVD manual edge-swipe: discard dialog appeared; back closed it and retained editor draft |
| Release install and first-use vault | Signed APK installed on a fresh API 35 AVD; vault created and home displayed |
| Bank/marketplace OCR and independent bulk shopping records | Optional 15-screenshot private fixture benchmark; JVM parser tests; Android all-or-nothing batch transaction test |

The UI automation verifies back *dispatch and destination*, not the visual quality of the predictive-back animation.

## Android 16 phone spot check (2026-09-23)

On a connected Meizu 20 Pro (Android 16 / API 36, arm64), the Debug APK installed under the owner profile and opened the first-use vault screen. The Release APK was rejected with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: an older Debug-signed `com.secureledger` package record remained on the phone, including retained data in the Flyme parallel-space profile. This is a signing-key mismatch, not an Android 16 API or ABI failure. No existing package data was removed. The isolated instrumentation suite was started but interrupted when the phone's foreground switched to other apps; **it must not be counted as a passing physical-device run**. The temporary test APK was removed, while the main Debug app remains installed.

At that checkpoint physical-device validation was incomplete. The2026-10-07 regression above supersedes installation and normal-suite status; protected-payment behavior and actual capture quality still require separate checks.
