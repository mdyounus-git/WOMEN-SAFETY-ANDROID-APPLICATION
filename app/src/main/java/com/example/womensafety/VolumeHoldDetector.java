package com.example.womensafety;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * FINAL VERSION - Volume Hold Detector
 * Location: app/src/main/java/com/example/womensafety/VolumeHoldDetector.java
 */
public class VolumeHoldDetector {
    private static final String TAG = "VolumeHoldDetector";
    private static final long HOLD_DURATION = 6000; // 6 seconds
    
    private Context context;
    private Handler handler;
    private Runnable sosRunnable;
    private boolean isVolumePressed = false;
    private long pressStartTime = 0;
    private boolean sosTriggered = false;
    
    public VolumeHoldDetector() {
        this.handler = new Handler(Looper.getMainLooper());
    }
    
    public void setContext(Context context) {
        this.context = context;
    }
    
    public boolean onKeyDown(int keyCode, KeyEvent event, Context ctx) {
        if (this.context == null) this.context = ctx;
        
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && !isVolumePressed) {
            isVolumePressed = true;
            pressStartTime = System.currentTimeMillis();
            sosTriggered = false;
            
            Log.d(TAG, "Volume Up pressed - starting timer");
            showToast("Hold for 6 seconds to trigger SOS...");
            
            sosRunnable = new Runnable() {
                @Override
                public void run() {
                    if (isVolumePressed && !sosTriggered) {
                        long holdDuration = System.currentTimeMillis() - pressStartTime;
                        if (holdDuration >= HOLD_DURATION) {
                            triggerSOS();
                        }
                    }
                }
            };
            
            handler.postDelayed(sosRunnable, HOLD_DURATION);
            return true;
        }
        return false;
    }
    
    public boolean onKeyUp(int keyCode, KeyEvent event, Context ctx) {
        if (this.context == null) this.context = ctx;
        
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && isVolumePressed) {
            long holdDuration = System.currentTimeMillis() - pressStartTime;
            
            Log.d(TAG, "Volume Up released after " + holdDuration + "ms");
            
            if (handler != null && sosRunnable != null) {
                handler.removeCallbacks(sosRunnable);
            }
            
            if (holdDuration < HOLD_DURATION && !sosTriggered) {
                showToast("Hold for 6 seconds to trigger SOS");
            }
            
            isVolumePressed = false;
            return true;
        }
        return false;
    }
    
    private void triggerSOS() {
        if (sosTriggered || context == null) return;
        
        sosTriggered = true;
        isVolumePressed = false;
        
        Log.d(TAG, "SOS TRIGGERED via Volume Hold!");
        
        vibratePhone();
        showToast("🚨 SOS ACTIVATED! Sending alerts...");
        
        SharedPreferences prefs = context.getSharedPreferences("WomenSafetyPrefs", Context.MODE_PRIVATE);
        boolean sosEnabled = prefs.getBoolean("sos_active", false);
        
        if (!sosEnabled) {
            showToast("Please activate SOS first in the app!");
            return;
        }
        
        Set<String> contactsSet = prefs.getStringSet("emergency_contacts", new HashSet<>());
        
        if (contactsSet == null || contactsSet.isEmpty()) {
            showToast("No emergency contacts added!");
            return;
        }
        
        ArrayList<String> contactsList = new ArrayList<>(contactsSet);
        
        Intent serviceIntent = new Intent(context, SOSService.class);
        serviceIntent.putStringArrayListExtra("contacts", contactsList);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
        
        showToast("✅ SOS Alert sent to " + contactsList.size() + " contacts!");
    }
    
    private void vibratePhone() {
        try {
            Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(500);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error vibrating phone", e);
        }
    }
    
    private void showToast(String message) {
        if (handler != null && context != null) {
            handler.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
        }
    }
    
    public void cleanup() {
        if (handler != null && sosRunnable != null) {
            handler.removeCallbacks(sosRunnable);
        }
        isVolumePressed = false;
        sosTriggered = false;
    }
}