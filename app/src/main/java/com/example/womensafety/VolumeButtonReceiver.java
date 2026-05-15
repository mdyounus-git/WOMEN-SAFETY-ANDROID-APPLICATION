package com.example.womensafety;

import android.view.KeyEvent;
import android.util.Log;

public class VolumeButtonReceiver {
    
    private static final String TAG = "VolumeButtonReceiver";
    private static final long TIMEOUT_MS = 500; // 500ms window to press both buttons
    
    private static boolean volumeUpPressed = false;
    private static boolean volumeDownPressed = false;
    private static long lastVolumeUpTime = 0;
    private static long lastVolumeDownTime = 0;
    
    public static void onVolumeKeyPressed(int keyCode) {
        long currentTime = System.currentTimeMillis();
        
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            volumeUpPressed = true;
            lastVolumeUpTime = currentTime;
            Log.d(TAG, "🔊 Volume UP pressed");
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            volumeDownPressed = true;
            lastVolumeDownTime = currentTime;
            Log.d(TAG, "🔉 Volume DOWN pressed");
        }
    }
    
    public static void onVolumeKeyReleased(int keyCode) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            volumeUpPressed = false;
            Log.d(TAG, "🔊 Volume UP released");
        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            volumeDownPressed = false;
            Log.d(TAG, "🔉 Volume DOWN released");
        }
    }
    
    public static boolean areBothButtonsPressed() {
        long currentTime = System.currentTimeMillis();
        
        // Check if both buttons were pressed within the timeout window
        boolean bothPressed = volumeUpPressed && volumeDownPressed;
        boolean withinTimeout = (currentTime - lastVolumeUpTime < TIMEOUT_MS) && 
                                (currentTime - lastVolumeDownTime < TIMEOUT_MS);
        
        if (bothPressed && withinTimeout) {
            Log.d(TAG, "✅ BOTH VOLUME BUTTONS DETECTED!");
            
            // Reset to prevent multiple triggers
            volumeUpPressed = false;
            volumeDownPressed = false;
            
            return true;
        }
        
        return false;
    }
    
    public static void reset() {
        volumeUpPressed = false;
        volumeDownPressed = false;
        lastVolumeUpTime = 0;
        lastVolumeDownTime = 0;
        Log.d(TAG, "🔄 VolumeButtonReceiver reset");
    }
}