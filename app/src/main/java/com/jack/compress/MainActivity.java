package com.jack.compress;

import android.animation.ValueAnimator;
import android.app.*;
import android.content.*;
import android.content.res.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;

@androidx.media3.common.util.UnstableApi
public class MainActivity extends Activity {
  final ArrayList<Uri> files = new ArrayList<>();
  LinearLayout page, list, root;
  EditText target;
  TextView state, percentage, estimate;
  String targetKey = "videoTarget";
  final ArrayList<Button> effortButtons = new ArrayList<>();
  final Map<Uri, MediaDetails> media = new HashMap<>();
  final ExecutorService metadata = Executors.newSingleThreadExecutor();
  int probeGeneration;
  boolean metadataComplete;

  static final class MediaDetails {
    boolean video;
    long bytes;
    double duration;
    int width = 1920, height = 1080;
    float fps = 30;
  }

  Button run, cancel;
  ProgressDial dial;
  boolean settingsOpen, wasBusy;
  int bg, ink, muted, surface, border;
  android.content.SharedPreferences prefs;
  final Handler handler = new Handler(Looper.getMainLooper());
  final Runnable refresh =
      new Runnable() {
        public void run() {
          render();
          handler.postDelayed(this, 200);
        }
      };

  int dp(int v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    prefs = getSharedPreferences("settings", 0);
    if (!prefs.contains("videoTarget")) {
      String legacy = prefs.getString("target", "10");
      prefs
          .edit()
          .putString("videoTarget", legacy.equals("10") ? "20" : legacy)
          .putString("photoTarget", legacy)
          .apply();
    }
    if (saved != null) {
      ArrayList<String> stored = saved.getStringArrayList("files");
      if (stored != null) for (String u : stored) files.add(Uri.parse(u));
      settingsOpen = saved.getBoolean("settings");
    } else receive(getIntent());
    if (settingsOpen) settings();
    else home();
    if (Build.VERSION.SDK_INT >= 33)
      getOnBackInvokedDispatcher()
          .registerOnBackInvokedCallback(
              android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
              () -> {
                if (settingsOpen) home();
                else finish();
              });
  }

  @Override
  protected void onResume() {
    super.onResume();
    handler.post(refresh);
  }

  @Override
  protected void onPause() {
    handler.removeCallbacks(refresh);
    persistTarget();
    super.onPause();
  }

  @Override
  protected void onSaveInstanceState(Bundle b) {
    persistTarget();
    ArrayList<String> s = new ArrayList<>();
    for (Uri u : files) s.add(u.toString());
    b.putStringArrayList("files", s);
    b.putBoolean("settings", settingsOpen);
    super.onSaveInstanceState(b);
  }

  @Override
  protected void onDestroy() {
    metadata.shutdownNow();
    super.onDestroy();
  }

  @Override
  protected void onNewIntent(Intent i) {
    super.onNewIntent(i);
    receive(i);
    home();
  }

  void receive(Intent i) {
    if (Intent.ACTION_SEND.equals(i.getAction())) {
      Uri u = i.getParcelableExtra(Intent.EXTRA_STREAM);
      if (u != null && !files.contains(u)) files.add(u);
    } else if (Intent.ACTION_SEND_MULTIPLE.equals(i.getAction())) {
      ArrayList<Uri> us = i.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
      if (us != null) for (Uri u : us) if (!files.contains(u)) files.add(u);
    }
  }

  void palette() {
    String theme = prefs.getString("theme", "Dark");
    boolean dark =
        theme.equals("Dark")
            || (theme.equals("System")
                && (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                    == Configuration.UI_MODE_NIGHT_YES);
    bg = dark ? 0xff000000 : 0xffffffff;
    ink = dark ? 0xffffffff : 0xff000000;
    muted = dark ? 0xffaaaaaa : 0xff606060;
    surface = dark ? 0xff181818 : 0xfff2f2f2;
    border = dark ? 0xff383838 : 0xffd8d8d8;
    setTheme(dark ? R.style.AppTheme : R.style.LightTheme);
    getWindow().setStatusBarColor(bg);
    getWindow().setNavigationBarColor(bg);
    getWindow()
        .getDecorView()
        .setSystemUiVisibility(
            dark
                ? 0
                : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
  }

  GradientDrawable round(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }

  TextView text(String s, int size, int color) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setFontFeatureSettings("tnum");
    t.setIncludeFontPadding(false);
    return t;
  }

  void space(int h) {
    page.addView(new View(this), new LinearLayout.LayoutParams(1, dp(h)));
  }

  void base(String title) {
    palette();
    root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(bg);
    root.setOnApplyWindowInsetsListener(
        (v, i) -> {
          if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets bars =
                i.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
          } else
            v.setPadding(
                i.getSystemWindowInsetLeft(),
                i.getSystemWindowInsetTop(),
                i.getSystemWindowInsetRight(),
                i.getSystemWindowInsetBottom());
          return i;
        });
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    root.addView(scroll, new LinearLayout.LayoutParams(-1, -1));
    page = new LinearLayout(this);
    page.setOrientation(LinearLayout.VERTICAL);
    page.setPadding(dp(22), dp(18), dp(22), dp(24));
    scroll.addView(page);
    setContentView(root);
    root.requestApplyInsets();
    LinearLayout header = new LinearLayout(this);
    header.setGravity(Gravity.CENTER_VERTICAL);
    TextView h = text(title, 18, ink);
    h.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    header.addView(h, new LinearLayout.LayoutParams(0, -2, 1));
    header.addView(
        makeButton(
            settingsOpen ? "Back" : "Settings",
            () -> {
              persistTarget();
              if (settingsOpen) home();
              else settings();
            },
            false));
    page.addView(header);
    space(8);
  }

  Button makeButton(String s, Runnable action, boolean primary) {
    Button b = new Button(this);
    b.setText(s);
    b.setAllCaps(false);
    b.setTextSize(13);
    b.setTextColor(primary ? bg : ink);
    b.setMinHeight(dp(48));
    b.setMinimumWidth(0);
    b.setElevation(0);
    b.setStateListAnimator(null);
    b.setPadding(dp(14), dp(8), dp(14), dp(8));
    b.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(border), round(primary ? ink : surface, 6), null));
    b.setOnClickListener(v -> action.run());
    return b;
  }

  Button button(String s, Runnable r, boolean primary) {
    Button b = makeButton(s, r, primary);
    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
    lp.topMargin = dp(10);
    page.addView(b, lp);
    return b;
  }

  void line() {
    View v = new View(this);
    v.setBackgroundColor(border);
    LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, dp(1));
    l.setMargins(0, dp(18), 0, dp(18));
    page.addView(v, l);
  }

  void enter() {
    if (!ValueAnimator.areAnimatorsEnabled()) return;
    page.setAlpha(0);
    page.setTranslationY(dp(8));
    page.animate()
        .alpha(1)
        .translationY(0)
        .setDuration(220)
        .setInterpolator(new DecelerateInterpolator())
        .start();
  }

  void home() {
    settingsOpen = false;
    base("Compress");
    dial = new ProgressDial(this);
    dial.palette(ink, border);
    page.addView(dial, new LinearLayout.LayoutParams(-1, -2));
    percentage = text("", 13, muted);
    percentage.setGravity(Gravity.CENTER);
    page.addView(percentage);
    space(20);
    page.addView(text("MAX FILE SIZE", 11, muted));
    space(8);
    LinearLayout size = new LinearLayout(this);
    size.setGravity(Gravity.BOTTOM);
    target = new EditText(this);
    target.setSingleLine();
    targetKey = targetKeyForQueue();
    target.setText(prefs.getString(targetKey, targetKey.equals("videoTarget") ? "20" : "10"));
    target.setTextSize(52);
    target.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
    target.setTextColor(ink);
    target.setInputType(8194);
    target.setBackgroundColor(Color.TRANSPARENT);
    target.setPadding(0, 0, 0, 0);
    target.setSelectAllOnFocus(true);
    target.setContentDescription("Maximum file size in megabytes");
    size.addView(target, new LinearLayout.LayoutParams(0, dp(74), 1));
    TextView unit = text("MB", 18, muted);
    unit.setPadding(0, 0, 0, dp(12));
    size.addView(unit);
    page.addView(size);
    LinearLayout presets = new LinearLayout(this);
    for (String n : new String[] {"5", "10", "20", "50"}) {
      Button b = makeButton(n + " MB", () -> target.setText(n), false);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
      if (presets.getChildCount() > 0) lp.leftMargin = dp(4);
      presets.addView(b, lp);
    }
    page.addView(presets);
    space(16);
    page.addView(text("EFFORT", 11, muted));
    space(8);
    effortButtons.clear();
    LinearLayout efforts = new LinearLayout(this);
    for (String level : CompressionOptions.EFFORTS) {
      Button b =
          makeButton(
              level,
              () -> {
                if (CompressionService.busy) return;
                prefs.edit().putString("effort", level).apply();
                updateEffort();
                updateEstimate();
              },
              false);
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(48), 1);
      if (!effortButtons.isEmpty()) lp.leftMargin = dp(4);
      efforts.addView(b, lp);
      effortButtons.add(b);
    }
    page.addView(efforts);
    estimate = text("Add media for time estimate", 12, muted);
    estimate.setPadding(0, dp(10), 0, 0);
    page.addView(estimate);
    updateEffort();
    target.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

          public void onTextChanged(CharSequence s, int start, int before, int count) {
            updateEstimate();
          }

          public void afterTextChanged(Editable e) {}
        });
    line();
    button(
        "Add photos or videos",
        () -> {
          Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
          i.setType("*/*");
          i.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"image/*", "video/*"});
          i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
          i.addCategory(Intent.CATEGORY_OPENABLE);
          startActivityForResult(i, 1);
        },
        false);
    page.setOnDragListener(
        (v, e) -> {
          if (e.getAction() == DragEvent.ACTION_DRAG_STARTED) return !CompressionService.busy;
          if (e.getAction() == DragEvent.ACTION_DROP) {
            requestDragAndDropPermissions(e);
            for (int j = 0; j < e.getClipData().getItemCount(); j++) {
              Uri u = e.getClipData().getItemAt(j).getUri();
              if (u != null && !files.contains(u)) files.add(u);
            }
            showFiles();
            return true;
          }
          return true;
        });
    list = new LinearLayout(this);
    list.setOrientation(LinearLayout.VERTICAL);
    page.addView(list);
    run = button("Compress", this::start, true);
    cancel = button("Cancel", () -> stopService(new Intent(this, CompressionService.class)), false);
    state = text("", 12, muted);
    state.setPadding(0, dp(14), 0, 0);
    state.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    page.addView(state);
    showFiles();
    render();
    enter();
  }

  void render() {
    if (settingsOpen || state == null) return;
    boolean busy = CompressionService.busy;
    if (wasBusy && !busy) updateEstimate();
    wasBusy = busy;
    String status = CompressionService.status;
    if (!state.getText().toString().equals(status)) state.setText(status);
    run.setEnabled(!busy && !files.isEmpty());
    run.setAlpha(run.isEnabled() ? 1 : .35f);
    target.setEnabled(!busy);
    for (Button b : effortButtons) b.setEnabled(!busy);
    cancel.setVisibility(busy ? View.VISIBLE : View.GONE);
    float p = CompressionService.progress;
    percentage.setText(
        busy
            ? (p < 0 ? "Processing" : Math.round(p * 100) + "%")
            : CompressionService.saved > 0 ? CompressionService.saved + " saved" : "");
    dial.update(busy, p, !busy && CompressionService.saved > 0);
  }

  void persistTarget() {
    if (target != null && !settingsOpen)
      prefs.edit().putString(targetKey, target.getText().toString()).apply();
  }

  void showFiles() {
    if (list == null || settingsOpen) return;
    String key = targetKeyForQueue();
    if (target != null && !targetKey.equals(key)) {
      persistTarget();
      targetKey = key;
      target.setText(prefs.getString(key, key.equals("videoTarget") ? "20" : "10"));
    }
    probeMedia();
    list.removeAllViews();
    for (Uri u : files) {
      TextView t = text(name(u) + "   ×", 13, ink);
      t.setPadding(dp(12), dp(14), dp(12), dp(14));
      t.setBackground(round(surface, 8));
      LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
      lp.topMargin = dp(8);
      list.addView(t, lp);
      t.setContentDescription("Remove " + name(u));
      t.setOnClickListener(
          v -> {
            if (!CompressionService.busy) {
              files.remove(u);
              showFiles();
            }
          });
    }
    render();
  }

  String name(Uri u) {
    try (android.database.Cursor c =
        getContentResolver().query(u, new String[] {"_display_name"}, null, null, null)) {
      if (c != null && c.moveToFirst()) return c.getString(0);
    } catch (Exception ignored) {
    }
    return "Media";
  }

  @Override
  protected void onActivityResult(int req, int res, Intent data) {
    super.onActivityResult(req, res, data);
    if (req == 1 && res == RESULT_OK && data != null) {
      if (data.getClipData() != null)
        for (int k = 0; k < data.getClipData().getItemCount(); k++)
          add(data.getClipData().getItemAt(k).getUri());
      else if (data.getData() != null) add(data.getData());
      showFiles();
    }
  }

  void add(Uri u) {
    try {
      getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
    } catch (Exception ignored) {
    }
    if (!files.contains(u)) files.add(u);
  }

  void start() {
    try {
      double mb = Double.parseDouble(target.getText().toString());
      if (!Double.isFinite(mb) || mb < 0.01 || mb > 100000) throw new NumberFormatException();
      persistTarget();
      Intent i = new Intent(this, CompressionService.class);
      i.putParcelableArrayListExtra("files", new ArrayList<>(files));
      i.putExtra("limit", (long) (mb * 1000000));
      startForegroundService(i);
    } catch (Exception e) {
      new AlertDialog.Builder(this)
          .setMessage("Enter a size from 0.01 to 100000 MB")
          .setPositiveButton("OK", null)
          .show();
    }
  }

  void choice(String label, String key, String[] values, String fallback) {
    TextView caption = text(label, 12, muted);
    caption.setPadding(0, dp(18), 0, dp(6));
    page.addView(caption);
    Spinner s = new Spinner(this);
    ArrayAdapter<String> a =
        new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, values);
    s.setAdapter(a);
    s.setSelection(Math.max(0, Arrays.asList(values).indexOf(prefs.getString(key, fallback))));
    s.setBackground(new RippleDrawable(ColorStateList.valueOf(border), round(surface, 6), null));
    page.addView(s, new LinearLayout.LayoutParams(-1, dp(48)));
    s.setContentDescription(label);
    s.setOnItemSelectedListener(
        new android.widget.AdapterView.OnItemSelectedListener() {
          public void onNothingSelected(android.widget.AdapterView<?> p) {}

          public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
            if (!prefs.getString(key, fallback).equals(values[pos])) {
              prefs.edit().putString(key, values[pos]).apply();
              if (key.equals("theme")) settings();
            }
          }
        });
  }

  void toggle(String label, String key, boolean def) {
    Switch s = new Switch(this);
    s.setText(label);
    s.setTextSize(15);
    s.setTextColor(ink);
    s.setPadding(0, dp(16), 0, dp(16));
    s.setMinHeight(dp(56));
    s.setChecked(prefs.getBoolean(key, def));
    int[][] states = {{android.R.attr.state_checked}, {}};
    s.setTrackTintList(new ColorStateList(states, new int[] {ink, border}));
    s.setThumbTintList(new ColorStateList(states, new int[] {ink, muted}));
    page.addView(s);
    s.setOnCheckedChangeListener((b, c) -> prefs.edit().putBoolean(key, c).apply());
  }

  @Override
  public void onBackPressed() {
    if (settingsOpen) home();
    else super.onBackPressed();
  }

  String targetKeyForQueue() {
    if (files.isEmpty()) return "videoTarget";
    for (Uri u : files) {
      String type;
      try {
        type = getContentResolver().getType(u);
      } catch (RuntimeException ignored) {
        type = null;
      }
      if (type == null || type.startsWith("video/")) return "videoTarget";
    }
    return "photoTarget";
  }

  void updateEffort() {
    String effort = prefs.getString("effort", "Balanced");
    for (int i = 0; i < effortButtons.size(); i++) {
      Button b = effortButtons.get(i);
      boolean selected = CompressionOptions.EFFORTS[i].equals(effort);
      b.setSelected(selected);
      b.setTextColor(selected ? bg : ink);
      b.setBackground(
          new RippleDrawable(
              ColorStateList.valueOf(border), round(selected ? ink : surface, 6), null));
    }
  }

  void probeMedia() {
    metadataComplete = false;
    int generation = ++probeGeneration;
    ArrayList<Uri> snapshot = new ArrayList<>(files);
    updateEstimate();
    metadata.execute(
        () -> {
          Map<Uri, MediaDetails> found = new HashMap<>();
          for (Uri uri : snapshot) {
            MediaDetails d = new MediaDetails();
            try {
              String type = getContentResolver().getType(uri);
              d.video = type != null && type.startsWith("video/");
              try (android.os.ParcelFileDescriptor fd =
                  getContentResolver().openFileDescriptor(uri, "r")) {
                if (fd != null) d.bytes = fd.getStatSize();
              }
              if (d.video) {
                MediaMetadataRetriever r = new MediaMetadataRetriever();
                try {
                  r.setDataSource(this, uri);
                  d.duration =
                      Double.parseDouble(
                              r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))
                          / 1000;
                  d.width =
                      Integer.parseInt(
                          r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                  d.height =
                      Integer.parseInt(
                          r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
                  String rotation =
                      r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                  if ("90".equals(rotation) || "270".equals(rotation)) {
                    int tmp = d.width;
                    d.width = d.height;
                    d.height = tmp;
                  }
                  String fps =
                      r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);
                  if (fps != null) d.fps = Float.parseFloat(fps);
                } finally {
                  r.release();
                }
              }
              found.put(uri, d);
            } catch (Exception ignored) {
            }
          }
          handler.post(
              () -> {
                if (!isDestroyed() && generation == probeGeneration) {
                  media.clear();
                  media.putAll(found);
                  metadataComplete = true;
                  updateEstimate();
                }
              });
        });
  }

  void updateEstimate() {
    if (settingsOpen || estimate == null) return;
    if (files.isEmpty()) {
      estimate.setText("Add media for time estimate");
      return;
    }
    if (!metadataComplete) {
      estimate.setText("Estimating…");
      return;
    }
    try {
      double mb = Double.parseDouble(target.getText().toString());
      if (!Double.isFinite(mb) || mb < .01 || mb > 100000) throw new NumberFormatException();
      long cap = (long) (mb * 1000000);
      if (cap <= 0) throw new NumberFormatException();
      String effort = prefs.getString("effort", "Balanced"),
          codec = prefs.getString("codec", "H.264");
      double total = 0;
      boolean learned = prefs.contains(CompressionOptions.timingKey(codec, effort));
      for (Uri uri : files) {
        MediaDetails d = media.get(uri);
        if (d == null || d.video && d.duration <= 0) {
          estimate.setText("Time estimate unavailable");
          return;
        }
        if (prefs.getBoolean("copy", true) && d.bytes > 0 && d.bytes <= cap) {
          total += Math.max(1, d.bytes / 100000000.0);
          continue;
        }
        if (!d.video) {
          total += effort.equals("Fast") ? 1 : effort.equals("Best") ? 4 : 2;
          continue;
        }
        if (!CompressionOptions.encoderAvailable(codec)) {
          estimate.setText(codec + " encoding unavailable");
          return;
        }
        int audio =
            prefs.getBoolean("mute", false)
                ? 0
                : Integer.parseInt(prefs.getString("audio", "96")) * 1000;
        long budget = (long) (cap * 8 * .94 / d.duration) - audio;
        if (budget < 32000) {
          estimate.setText("Target too small for video");
          return;
        }
        int h =
            CompressionOptions.height(
                d.height, Integer.parseInt(prefs.getString("height", "1080")), budget, effort);
        int w = Math.max(2, (int) (d.width * (double) h / d.height));
        double factor =
            CompressionOptions.hardware(codec)
                ? (codec.equals("AV1") ? 1.2 : codec.equals("HEVC") ? .8 : .6)
                : (codec.equals("AV1") ? 20 : codec.equals("HEVC") ? 10 : 4);
        if (effort.equals("Best")) factor *= 1.8;
        factor = prefs.getFloat(CompressionOptions.timingKey(codec, effort), (float) factor);
        total += Math.max(2, CompressionOptions.work(d.duration, w, h, d.fps) * factor) + 1;
      }
      long low = Math.max(1, (long) (total * .5)),
          high = Math.max(low + 1, (long) Math.ceil(total * 2));
      estimate.setText(
          "Estimated " + time(low) + "–" + time(high) + (learned ? "" : " · approximate"));
    } catch (NumberFormatException e) {
      estimate.setText("Enter a valid file size");
    }
  }

  static String time(long seconds) {
    return seconds < 60
        ? seconds + " s"
        : String.format(java.util.Locale.US, "%d:%02d", seconds / 60, seconds % 60);
  }

  void settings() {
    settingsOpen = true;
    target = null;
    state = null;
    dial = null;
    base("Settings");
    choice("Theme", "theme", new String[] {"Dark", "Light", "System"}, "Dark");
    line();
    choice("Video codec", "codec", new String[] {"H.264", "HEVC", "AV1"}, "H.264");
    choice(
        "Maximum video height",
        "height",
        new String[] {"2160", "1440", "1080", "720", "480"},
        "1080");
    choice("Audio bitrate · kbps", "audio", new String[] {"64", "96", "128", "192"}, "96");
    toggle("Remove audio", "mute", false);
    line();
    choice("Photo format", "photo", new String[] {"JPEG", "WebP"}, "JPEG");
    choice(
        "Maximum photo edge",
        "edge",
        new String[] {"8192", "4096", "2560", "1920", "1280"},
        "4096");
    choice("Photo quality ceiling", "quality", new String[] {"95", "90", "85", "80"}, "95");
    toggle("Copy files already under limit", "copy", true);
    line();
    page.addView(text("Pictures/Compress · Movies/Compress", 12, muted));
    enter();
  }
}
