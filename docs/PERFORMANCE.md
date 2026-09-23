# Performance baseline

Measurements use `SystemClock.elapsedRealtime()` and sanitized `SecureLedgerPerf` log entries. Logs contain durations and record counts only; passwords, private keys, master keys, and plaintext ledger data are never logged.

## API 35 emulator baseline

Environment: Android 15 / API 35 x86_64 Google APIs AVD, Windows host with WHPX, debug build. Emulator measurements are useful for regression detection but are not a substitute for a physical-device benchmark.

| Stage | Observed time |
| --- | ---: |
| Cold launch to unlock screen | 2,889–2,911 ms |
| Existing-vault unlock flow | 2,939 ms |
| Keystore unwrap | included in instrumented profile; 9 ms in isolated run |
| Argon2id | 2,697 ms in UI run; 1,230 ms in isolated run |
| Master-key decrypt | <1 ms in isolated run |
| Identity verification/decrypt | 22 ms in isolated run |
| Room open | 82 ms after existing-vault unlock |
| Required repository initialization | 37 ms |
| Personal-project readiness | 42 ms |
| Deferred integrity/Merkle validation, empty ledger | 389 ms |
| PP-OCRv6_small first SDK initialization | 731 ms |
| PP-OCRv6_small ONNX model load (included above) | 406 ms |
| PP-OCRv6_small first inference, synthetic 1080 × 440 Chinese payment image | 311 ms, 2 lines |

Argon2id remains configured at 64 MiB, 3 iterations, parallelism 1. Parameters were not weakened based on emulator-only data.

## Threading and startup policy

- Argon2id and key operations run on `Dispatchers.Default`.
- Backup I/O and Room opening run on `Dispatchers.IO`.
- Compose owns only input, progress state, and navigation.
- The home screen waits for Room and the personal project, but full integrity validation runs after navigation.
- OCR model loading is lazy and runs on `Dispatchers.IO`; it is not part of vault unlock. The application-scoped engine reuses its sessions for subsequent scans.

## Remaining measurements

Before release, repeat on at least one representative physical phone and record cold start, unlock, Room open, home first-interactive time, PaddleOCR model load, and first/subsequent OCR inference times on actual order screenshots.
