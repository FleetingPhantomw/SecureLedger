# SecureLedger architecture

SecureLedger is opened as a Gradle Android project in Android Studio or IntelliJ IDEA. Source folders follow package names and are grouped by responsibility rather than by Android component type.

## Package boundaries

```text
com.secureledger
├── app/          application composition, navigation host and app-scoped state
├── core/         platform-light security and business rules
│   ├── backup/
│   ├── crypto/
│   ├── ledger/
│   ├── merkle/
│   ├── ocr/
│   ├── protocol/
│   └── settlement/
├── data/         Room entities, DAO interfaces and repository implementations
├── feature/      Compose screens and feature-specific presentation logic
└── integration/  Android and third-party adapters such as OCR and accessibility
```

Dependencies point inward: `feature` and `integration` may use `core`; repositories bridge `data` and `core`; `core` never imports Compose or Android UI. `app` is the composition root and may depend on all feature entry points.

## IDE setup

- Open the repository root, not the `app` folder.
- Use the Gradle wrapper and JDK 17.
- The local Android SDK path belongs in ignored `local.properties`.
- Generated `.idea`, `*.iml`, `.gradle`, build and SDK directories are intentionally not versioned.

## Git history

`main` contains the last verified milestone. Productization work should use focused commits after each build-and-test checkpoint. Database and security changes must never be mixed with unrelated visual refactors in one commit.

## Migration discipline

Room schema changes require an explicit migration and migration test. Existing encrypted ledger envelopes and signature/hash canonicalization are compatibility contracts; changing them requires a versioned protocol rather than an in-place reinterpretation.
