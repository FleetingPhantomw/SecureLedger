# Changelog

## Unreleased — productization refactor

### Repository and tooling

- Initialized Git on `main` with the verified SecureLedger 1.0 implementation as the baseline.
- Added IDE-neutral formatting rules and architecture documentation.
- Moved the application composition root from the generic `ui` package into `com.secureledger.app`.
- Added ignored local Android SDK configuration for Android Studio/IntelliJ IDEA imports.
- Added a reproducible API 35 emulator test runner for development without a physical phone.

### Data model

- Upgraded the Room database from version 6 to 7 with a non-destructive migration.
- Added user-managed bank-card references that retain only bank name, alias, and the last four digits.
- Added stable payment-channel codes and the fixed eight-category expense taxonomy while preserving legacy display values.
- Extended encrypted record payloads and portable backups compatibly; existing ciphertext, signatures, and hash-chain fields are unchanged.
- Added JVM taxonomy tests and an Android migration test that verifies schema indexes and preservation of existing tables.

### Unlock performance

- Moved Argon2id, Keystore access, backup restore, and Room opening off the Compose main thread.
- Added rotation-safe `Idle` / `Unlocking` / `Success` / `Error` state with immediate indeterminate progress and duplicate-submit prevention.
- Added sanitized stage timing for vault unlock, Room opening, repository readiness, and deferred integrity validation.
- Deferred the full signature/hash-chain/Merkle scan until after the home screen is ready.
- Preserved the existing Argon2id security parameters and documented the API 35 emulator baseline in `docs/PERFORMANCE.md`.
- Hardened the emulator runner so instrumentation failures cannot be reported as successful runs.

### Payments and categories

- Replaced free-text payment entry with fixed Alipay, WeChat, and bank-card choices.
- Replaced free-text primary categories with the fixed eight-category taxonomy.
- Added bank-card add, edit, enable/disable, and confirmed-delete controls in Settings; only bank name, alias, and four trailing digits are accepted.
- New bank-card transactions store the card ID plus a safe display snapshot so later card edits or deletion cannot change historical presentation.
- Kept unknown legacy payment strings under an internal compatibility code that is not offered for new records.
- Verified the complete Settings → add-card → structured-record → detail-snapshot path on the API 35 emulator.

Further functional, database, OCR, accessibility, navigation and UI changes will be recorded here when their implementation and tests are complete.
