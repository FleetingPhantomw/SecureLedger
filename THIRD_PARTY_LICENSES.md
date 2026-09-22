# Third-party components

SecureLedger intentionally uses mature libraries for infrastructure and cryptographic primitives. Versions are pinned in `app/build.gradle.kts`.

| Component | Version | Source | License | Purpose |
|---|---:|---|---|---|
| AndroidX Compose / Material 3 | BOM 2024.09.03 | https://developer.android.com/jetpack/compose | Apache-2.0 | Android UI |
| AndroidX Room | 2.6.1 | https://developer.android.com/training/data-storage/room | Apache-2.0 | SQLite ORM |
| Kotlin Coroutines | 1.9.0 | https://github.com/Kotlin/kotlinx.coroutines | Apache-2.0 | Async and Flow |
| kotlinx.serialization | 1.7.3 | https://github.com/Kotlin/kotlinx.serialization | Apache-2.0 | Payload serialization |
| Bouncy Castle | 1.78.1 | https://www.bouncycastle.org/ | MIT-like permissive license | Ed25519 and Argon2id primitives |
| ZXing Android Embedded | 4.3.0 | https://github.com/journeyapps/zxing-android-embedded | Apache-2.0 | QR generation and camera scanning |
| Apache Commons CSV | 1.14.1 | https://commons.apache.org/proper/commons-csv/ | Apache-2.0 | Standards-compliant bank/campus CSV parsing |
| AndroidX CameraX | 1.5.3 | https://developer.android.com/jetpack/androidx/releases/camera | Apache-2.0 | Lifecycle-aware camera preview and image capture |
| Google ML Kit Text Recognition v2 (Chinese, bundled) | 16.0.1 | https://developers.google.com/ml-kit/vision/text-recognition/v2/android | Google ML Kit Terms | On-device Chinese/Latin OCR; images are not uploaded |
| NanoHTTPD | 2.3.1 | https://github.com/NanoHttpd/nanohttpd | BSD-3-Clause | Embedded LAN-only HTTP server for encrypted sync packages |
| AndroidX Test | 1.6.x | https://developer.android.com/training/testing | Apache-2.0 | Instrumented device tests |

AES-GCM, SHA-256 and secure randomness are provided by the Android/JCA platform. Android Keystore supplies device-bound key protection. OCR, QR, CameraX, CSV, LAN HTTP and relay integrations remain isolated from the application-layer cryptographic protocol.
