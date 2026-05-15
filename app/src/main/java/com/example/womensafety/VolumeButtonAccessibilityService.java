package com.example.womensafety;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Set;

public class VolumeButtonAccessibilityService extends AccessibilityService {
    
    private static final String TAG = "VolumeButtonService";
    private static final long HOLD_DURATION = 6000; // 6 seconds
    
    private Handler handler;
    private Runnable sosRunnable;
    private boolean isVolumeUpPressed = false;
    private long pressStartTime = 0;
    private boolean sosTriggered = false;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        
        // Wake lock to keep service alive
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WomenSafety:VolumeButtonWakeLock"
        );
        wakeLock.acquire();
        
        Log.d(TAG, "✅ VolumeButtonAccessibilityService Created");
        showToast("Volume Button SOS Active - Hold Volume Up for 6 sec");
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        int action = event.getAction();

        // Only handle Volume Up button
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            
            if (action == KeyEvent.ACTION_DOWN && !isVolumeUpPressed) {
                // Volume Up pressed
                isVolumeUpPressed = true;
                pressStartTime = System.currentTimeMillis();
                sosTriggered = false;
                
                Log.d(TAG, "🔊 Volume Up PRESSED - Starting timer");
                vibrateShort();
                showToast("Hold for 6 seconds to trigger SOS...");
                
                // Start countdown
                sosRunnable = new Runnable() {
                    int countdown = 6;
                    
                    @Override
                    public void run() {
                        if (isVolumeUpPressed && !sosTriggered) {
                            countdown--;
                            
                            if (countdown > 0) {
                                vibrateShort();
                                showToast("SOS in " + countdown + " seconds...");
                                handler.postDelayed(this, 1000);
                            } else {
                                // 6 seconds completed!
                                triggerSOS();
                            }
                        }
                    }
                };
                
                handler.postDelayed(sosRunnable, 1000);
                return true; // Consume event
                
            } else if (action == KeyEvent.ACTION_UP && isVolumeUpPressed) {
                // Volume Up released
                long holdDuration = System.currentTimeMillis() - pressStartTime;
                
                Log.d(TAG, "🔊 Volume Up RELEASED after " + holdDuration + "ms");
                
                if (handler != null && sosRunnable != null) {
                    handler.removeCallbacks(sosRunnable);
                }
                
                if (holdDuration < HOLD_DURATION && !sosTriggered) {
                    showToast("Hold for 6 seconds to trigger SOS");
                }
                
                isVolumeUpPressed = false;
                return true; // Consume event
            }
        }
        
        return super.onKeyEvent(event);
    }

    private void triggerSOS() {
        if (sosTriggered) return;
        
        sosTriggered = true;
        isVolumeUpPressed = false;
        
        Log.d(TAG, "🚨🚨🚨 SOS TRIGGERED via Volume Hold!");
        
        vibrateLong();
        showToast("🚨 SOS ACTIVATED! Sending emergency alerts...");
        
        // Check if contacts exist
        SharedPreferences prefs = getSharedPreferences("WomenSafetyPrefs", MODE_PRIVATE);
        Set<String> contactsSet = prefs.getStringSet("emergency_contacts", new HashSet<>());
        
        if (contactsSet == null || contactsSet.isEmpty()) {
            showToast("❌ No emergency contacts added!");
            return;
        }
        
        // Start SOS Service
        Intent serviceIntent = new Intent(this, SOSService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        
        showToast("✅ SOS Alert sent to " + contactsSet.size() + " contacts!");
        
        // Wake up screen to show notification
        wakeUpScreen();
    }

    private void wakeUpScreen() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK | 
            PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "WomenSafety:WakeUp"
        );
        wl.acquire(3000); // Wake for 3 seconds
        wl.release();
    }

    private void vibrateShort() {
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(100);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Vibration error", e);
        }
    }

    private void vibrateLong() {
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    long[] pattern = {0, 500, 200, 500, 200, 500};
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
                } else {
                    long[] pattern = {0, 500, 200, 500, 200, 500};
                    vibrator.vibrate(pattern, -1);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Vibration error", e);
        }
    }

    private void showToast(String message) {
        handler.post(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not needed for volume button detection
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        
        if (handler != null && sosRunnable != null) {
            handler.removeCallbacks(sosRunnable);
        }
        
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        
        isVolumeUpPressed = false;
        sosTriggered = false;
        
        Log.d(TAG, "🛑 VolumeButtonAccessibilityService Destroyed");
    }
}