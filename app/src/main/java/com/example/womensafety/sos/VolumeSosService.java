package com.example.womensafety.sos;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

/**
 * The "special permission" from the setup screen: an accessibility service that only listens
 * to the Volume Up key. Holding it for 6 seconds triggers SOS from anywhere — inside other apps
 * and on the lock screen.
 *
 * <p>The key is never consumed, so the volume button keeps working normally. Gentle vibration
 * ticks start after 2 seconds so the user can feel the countdown without looking.
 *
 * <p>Note: when the screen is completely off, some phone makers stop delivering volume keys to
 * accessibility services. That is a device limitation, not something an app can override.
 */
public class VolumeSosService extends AccessibilityService {

    private static final String TAG = "VolumeSos";
    private static final long TICK_MS = 250L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final HoldTimer timer = new HoldTimer();
    private int lastSecondBuzzed;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = SystemClock.uptimeMillis();
            if (!timer.isHolding()) return;
            if (timer.shouldFire(now)) {
                Haptics.sosConfirmed(VolumeSosService.this);
                Log.i(TAG, "Volume Up held 6s: triggering SOS");
                SosTrigger.startFromBackground(VolumeSosService.this, SosTrigger.SOURCE_VOLUME);
                return;
            }
            int seconds = timer.secondsHeld(now);
            if (seconds >= 2 && seconds != lastSecondBuzzed) {
                lastSecondBuzzed = seconds;
                Haptics.tick(VolumeSosService.this);
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_VOLUME_UP) return false;
        long now = SystemClock.uptimeMillis();
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (timer.onDown(now)) {
                lastSecondBuzzed = 0;
                handler.removeCallbacks(tick);
                handler.postDelayed(tick, TICK_MS);
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            timer.onUp(now);
            handler.removeCallbacks(tick);
        }
        // Never consume: volume must keep working as usual.
        return false;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not used: this service only filters key events.
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacks(tick);
        timer.reset();
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(tick);
        super.onDestroy();
    }
}
