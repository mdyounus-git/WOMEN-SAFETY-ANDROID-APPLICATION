package com.example.womensafety.sos;

/**
 * Pure timing logic for "hold a key for N ms". No Android dependencies, so it is unit-tested.
 *
 * <p>Key repeat events (Android sends repeated DOWN events while a key is held) are ignored;
 * only the first DOWN starts the timer and UP always resets it.
 */
public final class HoldTimer {

    public static final long DEFAULT_HOLD_MS = 6_000L;

    private final long holdMs;
    private long downAt = -1;
    private boolean fired;

    public HoldTimer(long holdMs) {
        this.holdMs = holdMs;
    }

    public HoldTimer() {
        this(DEFAULT_HOLD_MS);
    }

    /** @return true if this DOWN started a new hold (false for key-repeat events). */
    public boolean onDown(long nowMs) {
        if (downAt >= 0) return false;
        downAt = nowMs;
        fired = false;
        return true;
    }

    /** @return how long the key was held, or -1 if no hold was in progress. */
    public long onUp(long nowMs) {
        if (downAt < 0) return -1;
        long held = nowMs - downAt;
        downAt = -1;
        return held;
    }

    public boolean isHolding() {
        return downAt >= 0;
    }

    /** Whole seconds held so far (0 if not holding). */
    public int secondsHeld(long nowMs) {
        if (downAt < 0) return 0;
        return (int) ((nowMs - downAt) / 1000L);
    }

    /**
     * Call periodically while holding. Returns true exactly once, when the hold first reaches
     * the required duration.
     */
    public boolean shouldFire(long nowMs) {
        if (downAt < 0 || fired) return false;
        if (nowMs - downAt >= holdMs) {
            fired = true;
            return true;
        }
        return false;
    }

    public boolean hasFired() {
        return fired;
    }

    public long getHoldMs() {
        return holdMs;
    }

    public void reset() {
        downAt = -1;
        fired = false;
    }
}
