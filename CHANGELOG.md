# Changelog

## Unreleased — productization refactor

### Repository and tooling

- Initialized Git on `main` with the verified SecureLedger 1.0 implementation as the baseline.
- Added IDE-neutral formatting rules and architecture documentation.
- Moved the application composition root from the generic `ui` package into `com.secureledger.app`.
- Added ignored local Android SDK configuration for Android Studio/IntelliJ IDEA imports.
- Added a reproducible API 35 emulator test runner for development without a physical phone.

Further functional, database, OCR, accessibility, navigation and UI changes will be recorded here when their implementation and tests are complete.
