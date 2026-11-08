package com.jack.compress;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.*;
import android.os.SystemClock;
import android.view.View;

/** A frame-paced dial. Only runs while attached, visible, and processing. */
public final class ProgressDial extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF arc = new RectF();
  private boolean active, success;
  private float progress = -1, visual, settle;
  private long lastFrame;
  private int ink = Color.WHITE, muted = 0xff383838;
  private ValueAnimator completion;

  public ProgressDial(Context context) {
    super(context);
    setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
  }

  public void palette(int foreground, int border) {
    ink = foreground;
    muted = border;
    invalidate();
  }

  public void update(boolean running, float fraction, boolean done) {
    boolean finished = active && !running && done;
    active = running;
    progress = fraction;
    success = done;
    if (finished && ValueAnimator.areAnimatorsEnabled()) {
      if (completion != null) completion.cancel();
      completion = ValueAnimator.ofFloat(0, 1);
      completion.setDuration(420);
      completion.addUpdateListener(
          a -> {
            settle = (float) a.getAnimatedValue();
            invalidate();
          });
      completion.start();
    } else if (!running && (completion == null || !completion.isRunning())) settle = done ? 1 : 0;
    setContentDescription(
        running ? "Compression in progress" : done ? "Compression complete" : "Ready");
    invalidate();
  }

  @Override
  protected void onMeasure(int w, int h) {
    setMeasuredDimension(
        MeasureSpec.getSize(w), (int) (248 * getResources().getDisplayMetrics().density));
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    long now = SystemClock.uptimeMillis();
    float dt = lastFrame == 0 ? .016f : Math.min(.05f, (now - lastFrame) / 1000f);
    lastFrame = now;
    boolean motion = ValueAnimator.areAnimatorsEnabled();
    float target = success && !active ? 1 : Math.max(0, progress);
    visual = motion ? visual + (target - visual) * (1 - (float) Math.exp(-dt * 9)) : target;
    float scale = Math.min(getWidth(), getHeight()) / 300f;
    canvas.save();
    canvas.translate(getWidth() / 2f, getHeight() / 2f);
    canvas.scale(scale, scale);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(1);
    paint.setColor(muted);
    canvas.drawCircle(0, 0, 119, paint);
    for (int i = 0; i < 72; i++) {
      canvas.save();
      canvas.rotate(i * 5);
      paint.setColor(i < visual * 72 ? ink : muted);
      paint.setStrokeWidth(i % 6 == 0 ? 1.8f : 1);
      canvas.drawLine(0, -112, 0, i % 6 == 0 ? -100 : -106, paint);
      canvas.restore();
    }
    arc.set(-128, -128, 128, 128);
    paint.setColor(ink);
    paint.setStrokeWidth(2);
    paint.setStrokeCap(Paint.Cap.ROUND);
    if (active && progress < 0) {
      float angle = motion ? (now % 2400) / 2400f * 360 : -90;
      canvas.drawArc(arc, angle, 44, false, paint);
      canvas.drawArc(arc, angle + 180, 18, false, paint);
    } else if (visual > .001f) canvas.drawArc(arc, -90, 360 * visual, false, paint);
    paint.setStrokeCap(Paint.Cap.BUTT);
    if (success && !active) {
      Path path = new Path();
      path.moveTo(-18, 0);
      path.lineTo(-4, 14);
      path.lineTo(23, -16);
      PathMeasure pm = new PathMeasure(path, false);
      Path draw = new Path();
      pm.getSegment(0, pm.getLength() * settle, draw, true);
      paint.setStrokeWidth(2.5f);
      canvas.drawPath(draw, paint);
    } else {
      float breathe = active && motion ? (float) (1 + Math.sin(now / 450.0) * .05) : 1;
      canvas.save();
      canvas.scale(breathe, breathe);
      paint.setStrokeWidth(1.7f);
      // Opposing arrows converge toward the centre.
      float offset = active && motion ? (float) Math.sin(now / 600.0) * 3 : 0;
      canvas.drawLine(-27 - offset, -27 - offset, -7, -7, paint);
      canvas.drawLine(-7, -7, -7, -21, paint);
      canvas.drawLine(-7, -7, -21, -7, paint);
      canvas.drawLine(27 + offset, 27 + offset, 7, 7, paint);
      canvas.drawLine(7, 7, 7, 21, paint);
      canvas.drawLine(7, 7, 21, 7, paint);
      canvas.restore();
    }
    canvas.restore();
    if (active && motion && isShown()) postInvalidateOnAnimation();
    else if (Math.abs(visual - target) > .002 && motion && isShown()) postInvalidateOnAnimation();
  }

  @Override
  protected void onDetachedFromWindow() {
    if (completion != null) completion.cancel();
    lastFrame = 0;
    super.onDetachedFromWindow();
  }
}
