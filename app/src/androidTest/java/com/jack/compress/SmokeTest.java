package com.jack.compress;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import java.io.*;
import java.util.*;

/** Real service + MediaStore tests, executed only by the separate instrumentation APK. */
@androidx.media3.common.util.UnstableApi
public class SmokeTest {
  Instrumentation instrumentation =
      androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();
  Context context;
  int checks;
  final List<Uri> created = new ArrayList<>();

  void check(boolean ok, String message) {
    if (!ok) throw new AssertionError(message);
    checks++;
  }

  @org.junit.Test
  public void compressionAndThemes() throws Throwable {
    try {
      context = instrumentation.getTargetContext();
      Activity activity =
          instrumentation.startActivitySync(
              new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      instrumentation.waitForIdleSync();
      context
          .getSharedPreferences("settings", 0)
          .edit()
          .putBoolean("copy", false)
          .putString("height", "480")
          .putString("codec", "H.264")
          .apply();
      Bitmap bitmap = Bitmap.createBitmap(1536, 1536, Bitmap.Config.ARGB_8888);
      int[] row = new int[1536];
      Random random = new Random(7);
      for (int y = 0; y < 1536; y++) {
        for (int x = 0; x < 1536; x++) row[x] = 0xff000000 | random.nextInt(0xffffff);
        bitmap.setPixels(row, 0, 1536, 0, y, 1536, 1);
      }
      Uri photo = insert(false, "smoke-input.png", "image/png");
      try (OutputStream out = context.getContentResolver().openOutputStream(photo)) {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
      }
      bitmap.recycle();
      compress(photo, 80000, false);
      Uri video = insert(true, "smoke-input.mp4", "video/mp4");
      try (InputStream in = instrumentation.getContext().getAssets().open("sample.mp4");
          OutputStream out = context.getContentResolver().openOutputStream(video)) {
        byte[] b = new byte[65536];
        int n;
        while ((n = in.read(b)) != -1) out.write(b, 0, n);
      }
      compress(video, 400000, true);
      context.getSharedPreferences("settings", 0).edit().putBoolean("copy", true).apply();
      compress(photo, 20000000, false);
      // Theme recreation must retain the queued input and remain usable.
      instrumentation.runOnMainSync(
          () -> {
            MainActivity m = (MainActivity) activity;
            m.files.add(photo);
            m.prefs.edit().putString("theme", "Light").apply();
            m.settings();
            m.home();
            check(m.bg == android.graphics.Color.WHITE, "Light theme");
            check(m.files.contains(photo), "Queue retained");
            m.prefs.edit().putString("theme", "Dark").apply();
            m.home();
            check(m.bg == android.graphics.Color.BLACK, "Dark theme");
          });
      android.util.Log.i("CompressTests", "PASS: " + checks + " checks");
    } finally {
      if (context != null) for (Uri u : created) context.getContentResolver().delete(u, null, null);
    }
  }

  Uri insert(boolean video, String name, String mime) {
    ContentValues v = new ContentValues();
    v.put("_display_name", name);
    v.put("mime_type", mime);
    v.put("relative_path", video ? "Movies/CompressTest" : "Pictures/CompressTest");
    Uri u =
        context
            .getContentResolver()
            .insert(
                video
                    ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    : MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                v);
    created.add(u);
    return u;
  }

  Set<Long> ids(boolean video) {
    Set<Long> ids = new HashSet<>();
    try (Cursor c =
        context
            .getContentResolver()
            .query(
                video
                    ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    : MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[] {"_id"},
                "relative_path = ?",
                new String[] {video ? "Movies/Compress/" : "Pictures/Compress/"},
                null)) {
      while (c != null && c.moveToNext()) ids.add(c.getLong(0));
    }
    return ids;
  }

  void compress(Uri input, long limit, boolean video) throws Exception {
    Set<Long> before = ids(video);
    Intent intent = new Intent(context, CompressionService.class);
    ArrayList<Uri> files = new ArrayList<>();
    files.add(input);
    intent.putParcelableArrayListExtra("files", files);
    intent.putExtra("limit", limit);
    context.startForegroundService(intent);
    long end = SystemClock.elapsedRealtime() + 60000;
    boolean started = false;
    while (SystemClock.elapsedRealtime() < end) {
      if (CompressionService.busy) started = true;
      if (started && !CompressionService.busy) break;
      Thread.sleep(50);
    }
    check(started, "Service started");
    check(!CompressionService.busy, "Service finished");
    check(CompressionService.saved == 1, "Saved: " + CompressionService.status);
    Set<Long> after = ids(video);
    after.removeAll(before);
    check(after.size() == 1, "Exactly one output");
    Uri out =
        ContentUris.withAppendedId(
            video
                ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                : MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            after.iterator().next());
    created.add(out);
    try (Cursor c =
        context
            .getContentResolver()
            .query(out, new String[] {"_size", "is_pending"}, null, null, null)) {
      check(c != null && c.moveToFirst(), "Output indexed");
      check(c.getLong(0) > 0 && c.getLong(0) <= limit, "Output below byte cap");
      check(c.getInt(1) == 0, "Published output");
    }
    if (video) {
      android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
      try {
        r.setDataSource(context, out);
        check(
            Long.parseLong(
                    r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION))
                > 0,
            "Video decodes");
      } finally {
        r.release();
      }
    } else
      try (InputStream in = context.getContentResolver().openInputStream(out)) {
        Bitmap decoded = BitmapFactory.decodeStream(in);
        check(decoded != null, "Photo decodes");
        if (decoded != null) decoded.recycle();
      }
  }
}
