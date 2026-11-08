package com.jack.compress;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import androidx.media3.common.*;
import androidx.media3.effect.Presentation;
import androidx.media3.transformer.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

@androidx.media3.common.util.UnstableApi
public class CompressionService extends Service {
  public static volatile boolean busy = false;
  public static volatile String status = "";
  public static volatile float progress = -1;
  public static volatile int saved = 0;
  final ExecutorService worker = Executors.newSingleThreadExecutor();
  final Handler main = new Handler(Looper.getMainLooper());
  volatile boolean cancelled;
  Transformer transformer;
  Map<String, ?> options;
  String effort, codec;
  long videoStarted;
  double videoWork;
  File bestVideo;

  String option(String key, String fallback) {
    Object v = options.get(key);
    return v instanceof String ? (String) v : fallback;
  }

  boolean option(String key, boolean fallback) {
    Object v = options.get(key);
    return v instanceof Boolean ? (Boolean) v : fallback;
  }

  void rememberTiming() {
    if (videoWork > 0) {
      double ratio = (SystemClock.elapsedRealtime() - videoStarted) / 1000.0 / videoWork;
      String key = CompressionOptions.timingKey(codec, effort);
      android.content.SharedPreferences prefs = getSharedPreferences("settings", 0);
      float previous = prefs.getFloat(key, (float) ratio);
      prefs.edit().putFloat(key, (float) (previous * .65 + ratio * .35)).apply();
      videoWork = 0;
    }
  }

  ArrayList<Uri> queue;
  int index = 0, success = 0;
  long limit;
  File current;
  StringBuilder errors = new StringBuilder();

  @Override
  public IBinder onBind(Intent i) {
    return null;
  }

  @Override
  public int onStartCommand(Intent i, int flags, int id) {
    if (busy) return START_NOT_STICKY;
    if (i == null) {
      stopSelf();
      return START_NOT_STICKY;
    }
    busy = true;
    cancelled = false;
    saved = 0;
    progress = -1;
    options = new HashMap<>(getSharedPreferences("settings", 0).getAll());
    effort = option("effort", "Balanced");
    codec = option("codec", "H.264");
    queue = i.getParcelableArrayListExtra("files");
    limit = i.getLongExtra("limit", 20000000);
    NotificationManager nm = getSystemService(NotificationManager.class);
    nm.createNotificationChannel(
        new NotificationChannel("compression", "Compression", NotificationManager.IMPORTANCE_LOW));
    Intent open = new Intent(this, MainActivity.class);
    Notification n =
        new Notification.Builder(this, "compression")
            .setSmallIcon(com.jack.compress.R.drawable.icon)
            .setContentTitle("Compress")
            .setContentText("Processing media")
            .setContentIntent(
                PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE))
            .setOngoing(true)
            .build();
    startForeground(1, n);
    next();
    return START_NOT_STICKY;
  }

  void next() {
    if (cancelled) return;
    if (queue == null || index >= queue.size()) {
      status = success + " saved" + (errors.length() > 0 ? "\n" + errors : "");
      finish();
      return;
    }
    progress = -1;
    videoWork = 0;
    bestVideo = null;
    Uri u = queue.get(index);
    status = (index + 1) + " / " + queue.size() + " · Compressing";
    worker.execute(
        () -> {
          try {
            String mime = getContentResolver().getType(u);
            if (mime == null) throw new IOException("Unknown media format");
            if (option("copy", true)) {
              try (android.os.ParcelFileDescriptor d =
                  getContentResolver().openFileDescriptor(u, "r")) {
                if (d != null && d.getStatSize() > 0 && d.getStatSize() <= limit) {
                  File f = File.createTempFile("copy", ".tmp", getCacheDir());
                  current = f;
                  try (InputStream in = getContentResolver().openInputStream(u);
                      OutputStream out = new FileOutputStream(f)) {
                    byte[] buf = new byte[262144];
                    int count;
                    while ((count = in.read(buf)) != -1) {
                      if (cancelled) throw new IOException("Cancelled");
                      out.write(buf, 0, count);
                    }
                  }
                  save(f, mime);
                  complete();
                  return;
                }
              }
            }
            if (mime.startsWith("image/")) {
              photo(u);
              complete();
            } else if (mime.startsWith("video/")) {
              video(u);
            } else throw new IOException("Unsupported media format");
          } catch (Exception e) {
            failed(e);
          }
        });
  }

  void complete() {
    if (cancelled) return;
    rememberTiming();
    if (bestVideo != null) {
      bestVideo.delete();
      bestVideo = null;
    }
    success++;
    saved = success;
    index++;
    if (current != null) current.delete();
    main.post(this::next);
  }

  void failed(Exception e) {
    if (cancelled) {
      if (current != null) current.delete();
      return;
    }
    if (bestVideo != null) {
      File fallback = bestVideo;
      bestVideo = null;
      worker.execute(
          () -> {
            try {
              save(fallback, "video/mp4");
              fallback.delete();
              complete();
            } catch (Exception failure) {
              fallback.delete();
              failed(failure);
            }
          });
      return;
    }
    errors
        .append("File ")
        .append(index + 1)
        .append(": ")
        .append(e.getMessage() == null ? "Unsupported media or encoder" : e.getMessage())
        .append("\n");
    index++;
    if (current != null) current.delete();
    main.post(this::next);
  }

  void photo(Uri uri) throws Exception {
    progress = -1;
    int max = Integer.parseInt(option("edge", "4096"));
    ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
    Bitmap bitmap =
        ImageDecoder.decodeBitmap(
            source,
            (decoder, info, s) -> {
              int w = info.getSize().getWidth(), h = info.getSize().getHeight();
              double scale = Math.min(1, (double) max / Math.max(w, h));
              decoder.setTargetSize(Math.max(1, (int) (w * scale)), Math.max(1, (int) (h * scale)));
              decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
              decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));
            });
    boolean webp = option("photo", "JPEG").equals("WebP");
    Bitmap.CompressFormat format = webp ? Bitmap.CompressFormat.WEBP : Bitmap.CompressFormat.JPEG;
    if (!webp && bitmap.hasAlpha()) {
      Bitmap flat =
          Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
      Canvas c = new Canvas(flat);
      c.drawColor(Color.WHITE);
      c.drawBitmap(bitmap, 0, 0, null);
      bitmap.recycle();
      bitmap = flat;
    }
    File out = File.createTempFile("photo", webp ? ".webp" : ".jpg", getCacheDir());
    current = out;
    try {
      int ceiling = Integer.parseInt(option("quality", "95"));
      for (int pass = 0; pass < 20 && !cancelled; pass++) {
        int lo = 35, hi = ceiling, best = -1;
        int steps = 0;
        while (lo <= hi && steps++ < CompressionOptions.photoSteps(effort)) {
          int q = (lo + hi) / 2;
          encode(bitmap, format, q, out);
          if (out.length() <= limit) {
            best = q;
            lo = q + 1;
          } else hi = q - 1;
        }
        if (best < 0) {
          encode(bitmap, format, 35, out);
          if (out.length() <= limit) best = 35;
        }
        if (best >= 0) {
          encode(bitmap, format, best, out);
          if (out.length() <= limit) {
            save(out, webp ? "image/webp" : "image/jpeg");
            return;
          }
        }
        if (bitmap.getWidth() <= 16 || bitmap.getHeight() <= 16) break;
        Bitmap smaller =
            Bitmap.createScaledBitmap(
                bitmap,
                Math.max(1, (int) (bitmap.getWidth() * .78)),
                Math.max(1, (int) (bitmap.getHeight() * .78)),
                true);
        bitmap.recycle();
        bitmap = smaller;
      }
      throw new IOException("Target too small");
    } finally {
      bitmap.recycle();
    }
  }

  void encode(Bitmap b, Bitmap.CompressFormat f, int q, File out) throws IOException {
    try (OutputStream s = new FileOutputStream(out)) {
      if (!b.compress(f, q, s)) throw new IOException("Photo encoding failed");
    }
  }

  void video(Uri uri) throws Exception {
    if (!CompressionOptions.encoderAvailable(codec))
      throw new IOException(
          codec + " encoding unavailable" + (codec.equals("AV1") ? " (requires Android 14+)" : ""));
    videoStarted = SystemClock.elapsedRealtime();
    MediaMetadataRetriever r = new MediaMetadataRetriever();
    double seconds;
    int height, width;
    float fps = 30;
    try {
      r.setDataSource(this, uri);
      seconds =
          Long.parseLong(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) / 1000.0;
      int w = Integer.parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
      int h = Integer.parseInt(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
      String rotation = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
      height = ("90".equals(rotation) || "270".equals(rotation)) ? w : h;
      width = ("90".equals(rotation) || "270".equals(rotation)) ? h : w;
      String rate = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);
      if (rate != null) fps = Float.parseFloat(rate);
    } finally {
      r.release();
    }
    if (seconds <= 0) throw new IOException("Invalid duration");
    int audio = option("mute", false) ? 0 : Integer.parseInt(option("audio", "96")) * 1000;
    long budget = (long) (limit * 8 * .94 / seconds) - audio;
    if (budget < 32000) throw new IOException("Target too small; remove audio or raise limit");
    int h =
        CompressionOptions.height(
            height, Integer.parseInt(option("height", "1080")), budget, effort);
    videoWork =
        CompressionOptions.work(seconds, Math.max(2, (int) (width * (double) h / height)), h, fps);
    attempt(uri, (int) Math.min(budget, 80000000), audio, h, 0);
  }

  void attempt(Uri uri, int bitrate, int audio, int height, int pass) throws IOException {
    File out = File.createTempFile("video", ".mp4", getCacheDir());
    out.delete();
    current = out;
    main.post(
        () -> {
          if (cancelled) return;
          try {
            DefaultEncoderFactory factory =
                new DefaultEncoderFactory.Builder(this)
                    .setVideoEncoderSelector(
                        type ->
                            type.equals(CompressionOptions.mime(codec))
                                ? EncoderSelector.DEFAULT.selectEncoderInfos(type)
                                : com.google.common.collect.ImmutableList.of())
                    .setRequestedVideoEncoderSettings(
                        new VideoEncoderSettings.Builder().setBitrate(bitrate).build())
                    .setRequestedAudioEncoderSettings(
                        new AudioEncoderSettings.Builder()
                            .setBitrate(Math.max(32000, audio))
                            .build())
                    .build();
            EditedMediaItem item =
                new EditedMediaItem.Builder(MediaItem.fromUri(uri))
                    .setRemoveAudio(audio == 0)
                    .setEffects(
                        new Effects(
                            Collections.emptyList(),
                            Collections.singletonList(
                                Presentation.createForHeight(Math.max(2, height / 2 * 2)))))
                    .build();
            transformer =
                new Transformer.Builder(this)
                    .setEncoderFactory(factory)
                    .setVideoMimeType(CompressionOptions.mime(codec))
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .addListener(
                        new Transformer.Listener() {
                          @Override
                          public void onCompleted(Composition c, ExportResult result) {
                            transformer = null;
                            progress = -1;
                            worker.execute(
                                () -> {
                                  try {
                                    if (cancelled) return;
                                    if (out.length() > limit) {
                                      long size = out.length();
                                      android.util.Log.i(
                                          "Compress",
                                          "Retry "
                                              + pass
                                              + ": "
                                              + size
                                              + " bytes at "
                                              + bitrate
                                              + " bps, height "
                                              + height);
                                      out.delete();
                                      if (pass >= CompressionOptions.retries(effort))
                                        throw new IOException("Target not reached; raise limit");
                                      int next = (int) (bitrate * (double) limit / size * .88);
                                      if (next < 32000) throw new IOException("Target too small");
                                      status =
                                          (index + 1) + " / " + queue.size() + " · Adjusting size";
                                      attempt(
                                          uri,
                                          next,
                                          audio,
                                          Math.max(144, (int) (height * .75)),
                                          pass + 1);
                                    } else {
                                      if (effort.equals("Best")
                                          && pass == 0
                                          && out.length() > 0
                                          && out.length() < limit * .9) {
                                        bestVideo = out;
                                        int refined =
                                            (int)
                                                Math.min(
                                                    80000000,
                                                    bitrate * (double) limit / out.length() * .97);
                                        if (refined > bitrate * 1.05) {
                                          status =
                                              (index + 1) + " / " + queue.size() + " · Refining";
                                          attempt(uri, refined, audio, height, pass + 1);
                                          return;
                                        }
                                      }
                                      save(out, "video/mp4");
                                      complete();
                                    }
                                  } catch (Exception e) {
                                    failed(e);
                                  }
                                });
                          }

                          @Override
                          public void onError(
                              Composition c, ExportResult result, ExportException e) {
                            transformer = null;
                            failed(e);
                          }
                        })
                    .build();
            transformer.start(item, out.getAbsolutePath());
            final Transformer exporting = transformer;
            main.post(
                new Runnable() {
                  public void run() {
                    if (cancelled || transformer != exporting) return;
                    ProgressHolder holder = new ProgressHolder();
                    if (exporting.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE)
                      progress = (index + holder.progress / 100f) / queue.size();
                    else progress = -1;
                    main.postDelayed(this, 200);
                  }
                });
          } catch (Exception e) {
            failed(e);
          }
        });
  }

  void save(File f, String mime) throws Exception {
    progress = -1;
    status = (index + 1) + " / " + queue.size() + " · Saving";
    if (cancelled) throw new IOException("Cancelled");
    if (f.length() == 0 || f.length() > limit) throw new IOException("Output exceeds limit");
    boolean video = mime.startsWith("video/");
    String ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
    if (ext == null) ext = video ? "mp4" : "jpg";
    ContentValues v = new ContentValues();
    v.put(
        MediaStore.MediaColumns.DISPLAY_NAME, "Compress_" + System.currentTimeMillis() + "." + ext);
    v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
    v.put(MediaStore.MediaColumns.RELATIVE_PATH, (video ? "Movies" : "Pictures") + "/Compress");
    v.put(MediaStore.MediaColumns.IS_PENDING, 1);
    Uri dest =
        getContentResolver()
            .insert(
                video
                    ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    : MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                v);
    if (dest == null) throw new IOException("Cannot create output");
    try {
      try (InputStream in = new FileInputStream(f);
          OutputStream out = getContentResolver().openOutputStream(dest)) {
        if (out == null) throw new IOException("Cannot write output");
        byte[] buffer = new byte[262144];
        int n;
        while ((n = in.read(buffer)) != -1) {
          if (cancelled) throw new IOException("Cancelled");
          out.write(buffer, 0, n);
        }
      }
      v.clear();
      v.put(MediaStore.MediaColumns.IS_PENDING, 0);
      getContentResolver().update(dest, v, null, null);
    } catch (Exception e) {
      getContentResolver().delete(dest, null, null);
      throw e;
    }
  }

  void finish() {
    progress = success > 0 ? 1 : -1;
    busy = false;
    stopForeground(STOP_FOREGROUND_REMOVE);
    stopSelf();
  }

  @Override
  public void onDestroy() {
    cancelled = true;
    main.removeCallbacksAndMessages(null);
    if (transformer != null) transformer.cancel();
    worker.shutdownNow();
    if (current != null) current.delete();
    if (bestVideo != null) bestVideo.delete();
    if (busy) status = "Cancelled";
    busy = false;
    super.onDestroy();
  }
}
