package com.example.womensafety.sos;

import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import androidx.annotation.NonNull;

/** Vibration patterns so the user can feel what is happening without looking at the screen. */
public final class Haptics {

    private Haptics() {
    }

    private static Vibrator vibrator(@NonNull Context c) {
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager vm = c.getSystemService(VibratorManager.class);
            return vm == null ? null : vm.getDefaultVibrator();
        }
        return c.getSystemService(Vibrator.class);
    }

    /** A light tick: countdown in progress. */
    public static void tick(@NonNull Context c) {
        vibrate(c, VibrationEffect.createOneShot(35, 120));
    }

    /** Three strong pulses: SOS has started. */
    public static void sosConfirmed(@NonNull Context c) {
        vibrate(c, VibrationEffect.createWaveform(new long[]{0, 400, 150, 400, 150, 600}, -1));
    }

    /** Two soft pulses: SOS stopped. */
    public static void sosStopped(@NonNull Context c) {
        vibrate(c, VibrationEffect.createWaveform(new long[]{0, 120, 120, 120}, -1));
    }

    private static void vibrate(@NonNull Context c, @NonNull VibrationEffect effect) {
        try {
            Vibrator v = vibrator(c);
            if (v != null && v.hasVibrator()) v.vibrate(effect);
        } catch (Exception ignored) {
            // Vibration is a nicety; never let it break SOS.
        }
    }
}
