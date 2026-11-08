Compress 1.2

- Video and mixed queues default to 20 MB; photo-only limits remain separate.
- AV1 encoding in Settings → Video codec, with native hardware preference and capability checks (Android 14+).
- Fast, Balanced and Best effort controls directly below maximum size, with approximate time ranges that calibrate from completed video jobs.
- Best mode can refine undersized results while retaining a valid fallback. Job options are frozen when processing starts.
- Updated home layout and screenshot; fixed Android SDK package setup in CI.

Install Compress-1.2.apk over the previous version. Package/signing identity is unchanged; versionCode is 3. Obtainium can discover this release from the same repository.

Release build, lint, signature verification and 66 emulator assertions passed, including actual AV1 output codec/byte-limit checks. Pixel hardware speed and real-world time-estimate accuracy remain untested.
