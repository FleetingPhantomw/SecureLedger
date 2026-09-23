# PaddleOCR Android SDK provenance

This module vendors the official PaddleOCR Android SDK source from
[`PaddlePaddle/PaddleOCR`](https://github.com/PaddlePaddle/PaddleOCR/tree/dab3fe35379033fdcb2d0e9572fac0b36c9a9ebf/deploy/ppocr-android/ppocr-sdk)
at commit `dab3fe35379033fdcb2d0e9572fac0b36c9a9ebf`. The Java/Kotlin SDK
source and upstream ProGuard rules are unmodified. The Gradle file adapts the
module to SecureLedger's existing Android Gradle Plugin and Kotlin versions.
The SDK is licensed under Apache-2.0; see `LICENSE`.

Bundled PP-OCRv6_small model files come from the official
[Android deployment guide](https://github.com/PaddlePaddle/PaddleOCR/blob/main/docs/version3.x/inference_deployment/cross_platform/android_deployment.md):

| Asset | SHA-256 |
|---|---|
| `models/det/inference.onnx` | `D73E0058B7A8086BBD57F3D10B8BCD4FF95363F67E06E2762B5E814FE9C9410E` |
| `models/rec/inference.onnx` | `5435FD747C9E0EFE15A96D0B378D5BD157E9492ED8FD80EDF08F30D02FA24634` |
| `models/rec/inference.yml` | `AB078671BB49F06228EADCCD34F1BB501E157F7A047095FFB943BA81512C77D1` |

The module uses ONNX Runtime 1.21.1. OpenCV uses the official Android AAR
4.9.0 because the older sample dependency's x86_64 library failed to load on
the project's API 35 emulator (`__lttf2` unresolved). Model inference remains
fully local.
