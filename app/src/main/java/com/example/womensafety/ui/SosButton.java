package com.example.womensafety.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.example.womensafety.R;

/**
 * The big SOS button. Press and hold for {@link #HOLD_MS} to fire — a ring fills up while you
 * hold, so a pocket-tap never sends an alert by accident. Soft rings pulse around it; they pulse
 * faster and turn deep red while SOS is active (then holding it stops SOS).
 *
 * <p>TalkBack users can't easily "hold", so with touch exploration on, a tap asks for
 * confirmation instead (see {@link Listener#onAccessibleActivate()}).
 */
public class SosButton extends View {

    public static final long HOLD_MS = 2_000L;

    public interface Listener {
        void onHoldStart();

        void onHoldCancelled();

        void onHoldComplete();

        void onAccessibleActivate();
    }

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint innerRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();

    private final ValueAnimator pulse = ValueAnimator.ofFloat(0f, 1f);
    private ValueAnimator hold;
    private float pulsePhase;
    private long shaderKey;
    private float holdProgress;
    private boolean active;
    private boolean holding;
    private int lastTickStep;
    @Nullable
    private Listener listener;
    private String label = "SOS";
    private String sub = "";

    private int colorIdle;
    private int colorIdleDeep;
    private int colorActive;
    private int colorActiveDeep;

    public SosButton(Context context) {
        this(context, null);
    }

    public SosButton(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        colorIdle = ContextCompat.getColor(context, R.color.ws_sos);
        colorIdleDeep = ContextCompat.getColor(context, R.color.ws_sos_deep);
        colorActive = ContextCompat.getColor(context, R.color.ws_sos_deep);
        colorActiveDeep = Color.rgb(120, 12, 24);

        ringPaint.setStyle(Paint.Style.FILL);
        innerRingPaint.setStyle(Paint.Style.STROKE);
        innerRingPaint.setColor(Color.WHITE);
        innerRingPaint.setAlpha(70);
        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setColor(Color.WHITE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        Typeface display = ResourcesCompat.getFont(context, R.font.fraunces_semibold);
        Typeface ui = ResourcesCompat.getFont(context, R.font.manrope_bold);
        labelPaint.setTypeface(display);
        labelPaint.setColor(Color.WHITE);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        subPaint.setTypeface(ui);
        subPaint.setColor(Color.WHITE);
        subPaint.setAlpha(215);
        subPaint.setTextAlign(Paint.Align.CENTER);
        subPaint.setLetterSpacing(0.12f);

        setClickable(true);
        setFocusable(true);
        setHapticFeedbackEnabled(true);

        pulse.setDuration(2600);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new LinearInterpolator());
        pulse.addUpdateListener(a -> {
            pulsePhase = (float) a.getAnimatedValue();
            invalidate();
        });
        updateDescription();
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = listener;
    }

    public void setActive(boolean active) {
        if (this.active == active) return;
        this.active = active;
        pulse.setDuration(active ? 1400 : 2600);
        updateDescription();
        invalidate();
    }

    public boolean isActive() {
        return active;
    }

    public void setLabels(@NonNull String label, @NonNull String sub) {
        this.label = label;
        this.sub = sub;
        invalidate();
    }

    private void updateDescription() {
        setContentDescription(getContext().getString(active
                ? R.string.sos_button_desc_active : R.string.sos_button_desc));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (!pulse.isStarted() && areAnimationsOn()) pulse.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        pulse.cancel();
        cancelHold(false);
        super.onDetachedFromWindow();
    }

    private boolean areAnimationsOn() {
        return ValueAnimator.areAnimatorsEnabled();
    }

    // ------------------------------------------------------------------ touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (isTouchExplorationOn()) return super.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!insideCore(e.getX(), e.getY())) return false;
                startHold();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (holding) cancelHold(true);
                return true;
            default:
                return holding;
        }
    }

    @Override
    public boolean performClick() {
        super.performClick();
        if (isTouchExplorationOn() && listener != null) listener.onAccessibleActivate();
        return true;
    }

    private boolean isTouchExplorationOn() {
        AccessibilityManager am = getContext().getSystemService(AccessibilityManager.class);
        return am != null && am.isEnabled() && am.isTouchExplorationEnabled();
    }

    private boolean insideCore(float x, float y) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float r = coreRadius() * 1.12f;
        return (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r;
    }

    private void startHold() {
        holding = true;
        lastTickStep = 0;
        getParent().requestDisallowInterceptTouchEvent(true);
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        if (listener != null) listener.onHoldStart();
        hold = ValueAnimator.ofFloat(0f, 1f);
        hold.setDuration(HOLD_MS);
        hold.setInterpolator(new LinearInterpolator());
        hold.addUpdateListener(a -> {
            holdProgress = (float) a.getAnimatedValue();
            int step = (int) (holdProgress * 4);
            if (step > lastTickStep) {
                lastTickStep = step;
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            }
            if (holdProgress >= 1f && holding) {
                holding = false;
                holdProgress = 0f;
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                if (listener != null) listener.onHoldComplete();
            }
            invalidate();
        });
        hold.start();
    }

    private void cancelHold(boolean notify) {
        boolean was = holding;
        holding = false;
        if (hold != null) {
            hold.removeAllUpdateListeners();
            hold.cancel();
        }
        hold = null;
        holdProgress = 0f;
        invalidate();
        if (was && notify && listener != null) listener.onHoldCancelled();
    }

    // ------------------------------------------------------------------ drawing

    private float coreRadius() {
        return Math.min(getWidth(), getHeight()) * 0.30f;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float core = coreRadius();
        float maxRing = Math.min(getWidth(), getHeight()) * 0.5f;
        int base = active ? colorActive : colorIdle;
        int deep = active ? colorActiveDeep : colorIdleDeep;

        // Three expanding, fading rings.
        for (int i = 0; i < 3; i++) {
            float p = (pulsePhase + i / 3f) % 1f;
            float r = core + (maxRing - core) * p;
            int alpha = (int) (70 * (1f - p));
            ringPaint.setColor(base);
            ringPaint.setAlpha(alpha);
            canvas.drawCircle(cx, cy, r, ringPaint);
        }

        // Core: radial gradient for depth, slightly pressed while holding.
        float pressScale = holding ? 0.96f : 1f;
        float r = core * pressScale;
        long key = ((long) Float.floatToIntBits(r) << 32) ^ base;
        if (key != shaderKey) {
            shaderKey = key;
            corePaint.setShader(new RadialGradient(cx - r * 0.3f, cy - r * 0.4f, r * 1.5f,
                    base, deep, Shader.TileMode.CLAMP));
        }
        canvas.drawCircle(cx, cy, r, corePaint);

        innerRingPaint.setStrokeWidth(r * 0.025f);
        canvas.drawCircle(cx, cy, r * 0.86f, innerRingPaint);

        // Hold progress.
        if (holdProgress > 0f) {
            progressPaint.setStrokeWidth(r * 0.07f);
            float inset = r * 0.86f;
            arc.set(cx - inset, cy - inset, cx + inset, cy + inset);
            canvas.drawArc(arc, -90f, 360f * holdProgress, false, progressPaint);
        }

        // Labels.
        labelPaint.setTextSize(r * (label.length() > 4 ? 0.34f : 0.52f));
        float labelY = cy + labelPaint.getTextSize() * 0.22f;
        canvas.drawText(label, cx, labelY, labelPaint);
        if (!sub.isEmpty()) {
            subPaint.setTextSize(r * 0.105f);
            canvas.drawText(sub.toUpperCase(), cx, labelY + r * 0.32f, subPaint);
        }
    }
}
