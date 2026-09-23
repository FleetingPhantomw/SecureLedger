# Performance baseline

Measurements use `SystemClock.elapsedRealtime()` and sanitized `SecureLedgerPerf` log entries. Logs contain durations and record counts only; passwords, private keys, master keys, and plaintext ledger data are never logged.

## API 35 emulator baseline (before final UI and navigation refactor)

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

## Final debug build remeasurement (2026-09-23)

Same API 35 x86_64 AVD, Windows host, debug build. The app was force-stopped before each cold launch; the isolated QA Android user contained one encrypted expense record. `am start -W` measured process start to unlock activity display. The unlock measurements begin when the test password is submitted. `homeFirstInteractiveApprox` is the first Compose frame after the home navigation host is composed, not an input-latency or physical-device measurement.

| Stage | Observed time |
| --- | ---: |
| Cold launch to unlock screen (2 runs) | 3,758–4,520 ms |
| Existing-vault unlock flow (3 runs) | 3,797–4,849 ms |
| Argon2id within UI unlock (3 runs) | 3,292–4,429 ms |
| Room open (3 runs) | 151–192 ms |
| Repository readiness (3 runs) | 46–76 ms |
| Personal-project readiness (3 runs) | 55–92 ms |
| Unlock submit to home first interactive frame, approximate (3 runs) | 4,611–5,769 ms |
| PP-OCRv6_small first SDK initialization (instrumented run) | 720 ms |
| PP-OCRv6_small ONNX model load, included above | 416 ms |
| PP-OCRv6_small first inference, synthetic 1080 × 440 image | 405 ms, 2 lines |
| Keystore-backed isolated test vault unlock | 1,659 ms; Argon2id 1,638 ms |

These runs are not a controlled before/after experiment: emulator scheduling and Argon2id time varied substantially. The revised UI adds no synchronous vault derivation or Room opening on the main thread, but the measurements do **not** establish an overall speed improvement. A real phone remains unavailable, so final device-specific latency and subsequent OCR inference are unverified.

Argon2id remains configured at 64 MiB, 3 iterations, parallelism 1. Parameters were not weakened based on emulator-only data.

## Threading and startup policy

- Argon2id and key operations run on `Dispatchers.Default`.
- Backup I/O and Room opening run on `Dispatchers.IO`.
- Compose owns only input, progress state, and navigation.
- The home screen waits for Room and the personal project, but full integrity validation runs after navigation.
- OCR model loading is lazy and runs on `Dispatchers.IO`; it is not part of vault unlock. The application-scoped engine reuses its sessions for subsequent scans.

## Remaining measurements

Before distributing for real daily use, repeat on at least one representative physical phone and record cold start, unlock, Room open, home first-interactive time, PaddleOCR model load, and first/subsequent OCR inference times on actual order screenshots.
