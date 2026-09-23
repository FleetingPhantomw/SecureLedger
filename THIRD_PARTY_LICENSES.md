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
| AndroidX CameraX | 1.5.3 | https://developer.android.com/jetpack/androidx/releases/camera | Apache-2.0 | Lifecycle-aware camera preview and image capture |
| PaddleOCR Android SDK and PP-OCRv6_small models | upstream `dab3fe35379033fdcb2d0e9572fac0b36c9a9ebf` | https://github.com/PaddlePaddle/PaddleOCR/tree/main/deploy/ppocr-android | Apache-2.0; license copied to `ppocr-sdk/LICENSE` | Bundled on-device Chinese/Latin OCR |
| ONNX Runtime Android | 1.21.1 | https://github.com/microsoft/onnxruntime | MIT | Local OCR inference |
| OpenCV Android (official AAR) | 4.9.0 | https://docs.opencv.org/4.11.0/d5/df8/tutorial_dev_with_OCV_on_Android.html | Apache-2.0 | OCR image preprocessing |
| NanoHTTPD | 2.3.1 | https://github.com/NanoHttpd/nanohttpd | BSD-3-Clause | Embedded LAN-only HTTP server for encrypted sync packages |
| AndroidX Test | 1.6.x | https://developer.android.com/training/testing | Apache-2.0 | Instrumented device tests |

AES-GCM, SHA-256 and secure randomness are provided by the Android/JCA platform. Android Keystore supplies device-bound key protection. OCR, QR, CameraX, LAN HTTP and relay integrations remain isolated from the application-layer cryptographic protocol.

The PaddleOCR SDK source is vendored unmodified in `ppocr-sdk/src/main/java`. PP-OCRv6_small ONNX models were obtained from the official Paddle model distribution linked in the Android deployment guide and bundled in `ppocr-sdk/src/main/assets/models`. Images and recognized text are processed locally.
