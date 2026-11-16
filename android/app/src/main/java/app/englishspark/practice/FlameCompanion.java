package app.englishspark.practice;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/** A small companion on the practice and completion screens. */
final class FlameCompanion extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path outer = new Path();
    private final Path inner = new Path();
    private final Path smile = new Path();
    private final Path star = new Path();
    private ValueAnimator animator;
    private float lift;

    FlameCompanion(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        outer.moveTo(64, 11);
        outer.cubicTo(66, 30, 81, 39, 88, 53);
        outer.cubicTo(101, 79, 82, 104, 59, 104);
        outer.cubicTo(37, 104, 22, 88, 24, 70);
        outer.cubicTo(25, 57, 32, 49, 41, 41);
        outer.cubicTo(42, 51, 47, 54, 50, 55);
        outer.cubicTo(57, 46, 59, 34, 59, 28);
        outer.cubicTo(59, 19, 62, 13, 64, 11);
        outer.close();
        inner.moveTo(59, 56);
        inner.cubicTo(64, 68, 79, 72, 77, 84);
        inner.cubicTo(76, 96, 66, 102, 58, 101);
        inner.cubicTo(48, 101, 40, 94, 40, 85);
        inner.cubicTo(40, 73, 54, 69, 59, 56);
        inner.close();
        smile.moveTo(54, 77);
        smile.quadTo(59, 83, 64, 77);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (ValueAnimator.areAnimatorsEnabled()) {
            animator = ValueAnimator.ofFloat(0f, -4f);
            animator.setDuration(1050);
            animator.setRepeatCount(3);
            animator.setRepeatMode(ValueAnimator.REVERSE);
            animator.setInterpolator(new AccelerateDecelerateInterpolator());
            animator.addUpdateListener(animation -> {
                lift = (float) animation.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = Math.min(getWidth(), getHeight()) / 116f;
        canvas.save();
        canvas.translate((getWidth() - 116f * scale) / 2f, (getHeight() - 116f * scale) / 2f);
        canvas.scale(scale, scale);
        fill(0x260f2923);
        canvas.drawOval(31, 101, 91, 110, paint);
        sparkle(canvas, 21, 36, 7, 0xfff4c879);
        sparkle(canvas, 94, 35, 5, 0xfffef0c4);
        sparkle(canvas, 98, 82, 4, 0xfff4c879);
        canvas.translate(0, lift);
        fill(0xfff29c55);
        canvas.drawPath(outer, paint);
        fill(0xffffd887);
        canvas.drawPath(inner, paint);
        fill(0xff4d2f26);
        canvas.drawOval(47, 69, 51, 75, paint);
        canvas.drawOval(67, 69, 71, 75, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.3f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawPath(smile, paint);
        fill(0xffef966f);
        canvas.drawOval(43, 77, 51, 81, paint);
        canvas.drawOval(67, 77, 75, 81, paint);
        canvas.restore();
    }

    private void fill(int color) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
    }

    private void sparkle(Canvas canvas, float x, float y, float radius, int color) {
        star.rewind();
        star.moveTo(x, y - radius);
        star.lineTo(x + 2, y - 2);
        star.lineTo(x + radius, y);
        star.lineTo(x + 2, y + 2);
        star.lineTo(x, y + radius);
        star.lineTo(x - 2, y + 2);
        star.lineTo(x - radius, y);
        star.lineTo(x - 2, y - 2);
        star.close();
        fill(color);
        canvas.drawPath(star, paint);
    }
}
