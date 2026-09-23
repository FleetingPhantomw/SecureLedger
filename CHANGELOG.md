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

### Product copy

- Rewrote home, project, sync, settlement, correction, backup, and invitation copy in concise consumer-facing Chinese.
- Replaced raw protocol statuses and enum values with actionable Chinese messages.
- Removed cryptographic internals from ordinary home and record-detail views; technical identifiers remain collapsed behind “技术详情” in security-related screens.
- Added friendly unlock errors for incorrect passwords and damaged local security data.

### OCR

- Replaced the production ML Kit Chinese OCR dependency with the official PaddleOCR Android SDK and bundled PP-OCRv6_small ONNX models.
- Added an application-scoped OCR engine that initializes lazily, serializes access to reusable inference sessions, returns text with coordinates, and runs outside the Compose main thread.
- Switched to OpenCV's official Android AAR after the sample package failed to load on the API 35 x86_64 emulator.
- Verified Chinese payment text, amount, bounding boxes, and existing device tests on the API 35 emulator. Model provenance and licenses are recorded in `ppocr-sdk/README.md` and `THIRD_PARTY_LICENSES.md`.

### Automatic payment recognition

- Added an opt-in Android accessibility service limited to user-selected apps and payment-related window events.
- Added editable recognition rules with positive/negative keywords, field patterns, priority, and cooldown; the rule engine ranks actual-payment amounts above unrelated prices.
- Added bounded text extraction that excludes password and input fields, plus in-memory duplicate suppression without saving third-party screen text.
- Added a foreground confirmation sheet and background notification or small timed accessibility overlay. Every candidate opens an editable record review; saving still requires explicit confirmation.
- Added privacy controls and system-service disclosure in Settings, four JVM rule tests, and two Android integration tests.

### Import cleanup

- Removed bank/campus CSV import screens, parser, repository entry point, and Apache Commons CSV dependency.
- Preserved existing encrypted import tables, backup data, and historical reconciliation actions without changing the Room schema.
- Kept the normalized import and reconciliation models available for future data sources.

### Sync transport cleanup

- Removed LAN/P2P and relay UI, service adapters, network permissions, cleartext allowance, and NanoHTTPD dependency.
- Kept the signed sync package, replay protection, record verification, and QR import/export path unchanged at this stage.

### QR sync

- Reduced the sync screen to generating and scanning QR codes, with a project selector where needed.
- Added bounded, checksummed QR frames for packages too large for one code. Scans may arrive out of order; the app only submits a complete package for the existing signature, hash-chain, Merkle, and replay checks.

Further functional, database, OCR, accessibility, navigation and UI changes will be recorded here when their implementation and tests are complete.
