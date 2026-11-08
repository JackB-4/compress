# Validation · Compress 1.1

Executed on Windows with JDK 17, Gradle 8.11.1, SDK 35 and a Pixel-profile Android 15 x86_64 emulator.

- Release compilation and Android lint passed (zero errors; non-blocking dependency/localization/backup warnings remain).
- One Android instrumentation test passed with 27 assertions covering photo encoding, video encoding, under-limit copy, light/dark themes and queue retention.
- A seeded 1536 × 1536 noise image was compressed to at most 80,000 bytes and decoded successfully.
- An 8-second synthetic 640 × 360 H.264/AAC clip was compressed to at most 400,000 bytes and had a readable nonzero output duration.
- Each successful export produced exactly one new MediaStore row, nonzero bytes at or below its limit, and `IS_PENDING=0`.
- Release APK signature verified. Package `com.jack.compress`, versionName `1.1`, versionCode `2`; non-debuggable release, signed with the original local development identity.
- Actual release home screen captured as `docs/home.png`.

Very small video budgets can fail safely. A 180,000-byte target for the 8-second synthetic clip with audio was rejected; no oversized file was published. The successful test budget is not a quality recommendation.

Run on a connected emulator/device:

```powershell
./gradlew.bat connectedDebugAndroidTest
```

The test creates synthetic media, runs the actual service, checks published outputs, and cleans up media it creates. Test-only libraries and the video fixture are excluded from the application APK.

Pixel 10 Pro hardware throughput, HDR behavior, subjective quality, full input-format coverage, Google Photos indexing/backup and Obtainium installation have not been tested. Emulator encoding uses emulator codecs and does not predict Pixel performance.

APK SHA-256: `08cb4d6b1c738a02a9aef3d1ca0bc12861eb876fc743622f48bd541476cb29f7`.

Certificate SHA-256: `f7b42229a3fd4b5b7624694df4fa5cb0ea4755faa8c86021b8a8df95b5f55b4f`.
