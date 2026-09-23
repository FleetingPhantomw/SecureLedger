# SecureLedger

SecureLedger 1.0 is a local-first Android secure ledger for personal records and multi-user AA projects. Security checks reject invalid data before persistence. The current productization build is verified on an API 35 emulator and installs on an Android 16 phone; full physical-device revalidation remains pending.

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
- Signed QR sync packages with replay protection, record validation, and multi-code framing for larger ledgers.
- Opt-in payment candidate recognition in selected apps, with editable rules and mandatory review before saving.
- Bundled PP-OCRv6_small Chinese OCR from camera, image, Android share intent or clipboard text, followed by mandatory confirmation.
- Historical encrypted statement imports remain readable and reconcilable; new CSV imports have been removed.
- Room migrations through schema v7; prior vault and legacy project data remain readable.

## Build and test

Use JDK 17 and Android SDK 35:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

The installable debug APK is `app/build/outputs/apk/debug/app-debug.apk`.

Build a separately signed, installable Release APK with:

```powershell
.\scripts\build-release.ps1
```

The result is `app/build/outputs/apk/release/app-release.apk`. On first use, the script creates a local signing key in the ignored `.signing/` directory and protects its randomly generated password with Windows DPAPI. **Back up the `.p12` key and its password separately** before installing this version for long-term use; the DPAPI file is bound to this Windows user and cannot be used alone on another computer. A different signing key cannot update an existing installation without uninstalling it, which would remove its local vault unless a portable encrypted backup has been made. Debug and Release are signed by different keys and likewise cannot update one another in place. See [release signing guidance](docs/RELEASE_SIGNING.md).

Physical-device instrumentation is intentionally isolated in `com.secureledger.test` and uses in-memory Room databases plus namespaced test vaults. It does not clear the production package or its data:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb install -r app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk
adb shell pm grant com.secureledger.test android.permission.CAMERA
adb shell am instrument -w -r com.secureledger.test/androidx.test.runner.AndroidJUnitRunner
adb uninstall com.secureledger.test
```

When no phone is connected, Android Studio can run the same suite on the API 35 AVD configured for this workspace:

```powershell
.\scripts\test-emulator.ps1
```

Open the repository root as a Gradle project in Android Studio or IntelliJ IDEA, select JDK 17, and use the Gradle wrapper. Local SDK and IDE metadata remain untracked.

The device suite covers Android Keystore/Room persistence, tamper detection, OCR/CameraX, accessibility privacy filtering, backup round-trip, QR scanning, recipient-bound project invitation, cross-replica sync, two-user AA co-signing, and rotation-safe unlock state with wrong-password retry.
The [verification matrix](docs/TESTING.md) distinguishes automated, manually checked, and still-pending physical-device coverage.

## Privacy and recovery

Ledger data, imported statements, screenshots and private keys remain local unless the user explicitly exports or synchronizes them. Sync QR content is encrypted and signed. Forgotten vault passwords cannot be bypassed: recovery requires an independently password-protected `.sldb` backup. Restore is accepted only into an empty vault and is validated before commit.

Third-party components, versions, sources, licenses and purposes are recorded in [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).
