# SecureLedger

SecureLedger 1.0 is a local-first Android secure ledger for personal records and multi-user AA projects. It is an operational application, not a UI-only prototype: security checks reject invalid data before persistence and the debug build is exercised on a physical Android device.

## Trust model

```text
password --Argon2id--> password key
Android Keystore ----> device wrapping key
random master key ---> personal data + wrapped private material
random project key --> each shared project's records and settlements

canonical payload -> AES-256-GCM -> SHA-256 record hash
                  -> per-project/per-author hash chain -> Ed25519 signature
                  -> deterministic Merkle tree
```

Original records are append-only. A correction is a separately encrypted and signed record referencing its target, so the effective value can change while audit history remains intact. Synchronization uses signed `SL1` packages with timestamp/nonce/message replay checks. Project invitations use recipient-bound ephemeral X25519 key agreement (`SLJ1`/`SLI1`). Portable backups use the password-protected `SLB1` format. AA co-signing uses `SLS1` packages and independent member Ed25519 signatures.

## Implemented product flows

- Create/unlock a Keystore-protected vault; encrypted backup, restore and device migration.
- Income, expense and transfer records with real transaction time, account, channel, tags, vouchers and combined payments.
- Immutable corrections, record detail/audit view and a security centre with exact failures and last-check time.
- Multi-user project creation, member list, signed join/invitation exchange and independent project keys.
- AA balances, deterministic transfer plan, project Merkle-bound proof and QR/clipboard multi-member co-signing.
- QR, tokenized LAN and opaque HTTPS relay transports over one validated secure package format.
- UTF-8/GB18030 bank and campus-card CSV import, encrypted staging, four-level business deduplication and reconciliation/supplement flows.
- Bundled offline Chinese OCR from camera, image, Android share intent or clipboard text, followed by mandatory confirmation.
- Room migrations through schema v6; prior vault and legacy project data remain readable.

## Build and test

Use JDK 17 and Android SDK 35:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

The installable debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

Physical-device instrumentation is intentionally isolated in `com.secureledger.test` and uses in-memory Room databases plus namespaced test vaults. It does not clear the production package or its data:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell pm grant com.secureledger.test android.permission.CAMERA
adb shell am instrument -w -r com.secureledger.test/androidx.test.runner.AndroidJUnitRunner
adb uninstall com.secureledger.test
```

The device suite covers real Android Keystore/Room persistence, tamper detection, CSV reconciliation, LAN transfer, backup round-trip, bundled OCR/CameraX, QR scanning, recipient-bound project invitation, cross-replica sync and two-user AA co-signing.

## Privacy and recovery

Ledger data, imported statements, screenshots and private keys remain local unless the user explicitly exports or synchronizes them. LAN/relay payloads are already encrypted and signed; relay endpoints must use HTTPS except explicit localhost development endpoints. Forgotten vault passwords cannot be bypassed: recovery requires an independently password-protected `.sldb` backup. Restore is accepted only into an empty vault and is validated before commit.

Third-party components, versions, sources, licenses and purposes are recorded in [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).
