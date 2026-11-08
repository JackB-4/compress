# Validation · Compress 1.2

Executed with JDK 17, Gradle 8.11.1, SDK 35 and an Android 15 x86_64 Pixel-profile emulator.

- Release compilation and lint passed with zero errors; non-blocking warnings remain.
- Two Android instrumentation tests passed with 66 assertions.
- Actual AV1 MP4 export succeeded; MediaExtractor verified the emitted video MIME was `video/av01`. This emulator uses a software AV1 encoder; Pixel hardware speed remains untested.
- H.264 exports passed in Fast, Balanced and Best modes. The tests verify requested output codec, readable duration, exactly one published MediaStore output, positive bytes below the cap, and cleared pending state.
- An 8-second 640 × 360 synthetic clip exported within 400,000 bytes. A one-second 320 × 180 clip exported with AV1 and the effort-mode checks within 200,000 bytes.
- A seeded 1536 × 1536 noise image exported within 80,000 bytes and decoded successfully. Already-small copying remains covered.
- Video default migration to 20 MB, three effort buttons, persisted effort, custom limit retention, no fabricated time without media, timing calibration, effort planning, light/dark themes and queue retention passed.
- Final release installed/launched on the emulator and home layout checked. Release APK signature verified against the original local certificate; package `com.jack.compress`, versionName `1.2`, versionCode `3`, non-debuggable.

Run: `./gradlew.bat connectedDebugAndroidTest` with a connected Android emulator/device. Tests create and remove synthetic media; test-only libraries and video fixtures are excluded from the application APK.

Pixel 10 Pro hardware throughput/quality, real-world estimate accuracy, full codec/input coverage, Google Photos indexing and Obtainium installation remain untested. Initial timing ranges are deliberately broad heuristics; completed video jobs calibrate subsequent estimates. Very small size budgets can be rejected without publishing an oversized result.

APK SHA-256: `7ffb1c55dced7eef06a0964cd6cf0e36fcf7c5f4ed0dd75ad59d10e1fe665847`.

Certificate SHA-256: `f7b42229a3fd4b5b7624694df4fa5cb0ea4755faa8c86021b8a8df95b5f55b4f`.
