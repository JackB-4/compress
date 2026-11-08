package com.jack.compress;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;

/** Shared encode planning, so the estimate follows the work actually requested. */
final class CompressionOptions {
  static final String[] EFFORTS = {"Fast", "Balanced", "Best"};

  static String mime(String codec) {
    return codec.equals("AV1") ? "video/av01" : codec.equals("HEVC") ? "video/hevc" : "video/avc";
  }

  private static final java.util.Map<String, Integer> support = new java.util.HashMap<>();

  private static synchronized int support(String codec) {
    Integer cached = support.get(codec);
    if (cached != null) return cached;
    int flags = 0;
    if (!codec.equals("AV1") || Build.VERSION.SDK_INT >= 34) {
      try {
        for (MediaCodecInfo info :
            new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos())
          if (info.isEncoder())
            for (String type : info.getSupportedTypes())
              if (type.equalsIgnoreCase(mime(codec))) {
                flags |= 1;
                if (info.isHardwareAccelerated()) flags |= 2;
              }
      } catch (RuntimeException ignored) {
      }
    }
    support.put(codec, flags);
    return flags;
  }

  static boolean encoderAvailable(String codec) {
    return (support(codec) & 1) != 0;
  }

  static boolean hardware(String codec) {
    return (support(codec) & 2) != 0;
  }

  static int height(int source, int ceiling, long bitrate, String effort) {
    int max = effort.equals("Fast") ? Math.min(720, ceiling) : ceiling;
    return Math.max(
        2,
        Math.min(Math.min(source, max), Math.max(144, (int) Math.sqrt(Math.max(1, bitrate) / 4.5)))
            / 2
            * 2);
  }

  static int retries(String effort) {
    return effort.equals("Fast") ? 1 : effort.equals("Best") ? 5 : 3;
  }

  static int photoSteps(String effort) {
    return effort.equals("Fast") ? 3 : effort.equals("Best") ? 8 : 6;
  }

  static String timingKey(String codec, String effort) {
    return "timing_" + codec + "_" + effort + "_" + hardware(codec);
  }

  static double work(double seconds, int width, int height, float fps) {
    return Math.max(
        .05,
        seconds
            * Math.max(1, width)
            * (double) Math.max(1, height)
            / (1920 * 1080)
            * Math.max(1, fps)
            / 30);
  }
}
