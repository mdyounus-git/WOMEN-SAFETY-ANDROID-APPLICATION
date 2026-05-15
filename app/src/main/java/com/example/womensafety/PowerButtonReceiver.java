package com.example.womensafety;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PowerButtonReceiver extends BroadcastReceiver {

    private static final String TAG = "PowerButtonReceiver";
    private static final String PREFS_NAME = "PowerButtonPrefs";
    private static final String KEY_PRESS_TIMES = "pressTimes";
    private static final String KEY_SOS_ENABLED = "sosEnabled";
    private static final long TIME_WINDOW_MS = 2000; // 2 seconds
    private static final int REQUIRED_PRESSES = 3; // 3 presses

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        
        if (action == null) return;

        // Handle boot completed - restore SOS state if needed
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || 
            "android.intent.action.QUICKBOOT_POWERON".equals(action)) {
            Log.d(TAG, "Device booted - checking SOS state");
            checkAndRestoreSOS(context);
            return;
        }

        // Handle screen on/off for power button detection
        if (Intent.ACTION_SCREEN_OFF.equals(action) || Intent.ACTION_SCREEN_ON.equals(action)) {
            
            // Check if SOS is enabled first
            if (!isSOSEnabled(context)) {
                Log.d(TAG, "SOS not enabled, ignoring power button");
                return;
            }

            long currentTime = System.currentTimeMillis();
            List<Long> pressTimes = getPressTimes(context);
            
            // Add current press
            pressTimes.add(currentTime);
            
            // Remove old presses outside time window
            List<Long> validPresses = new ArrayList<>();
            for (Long time : pressTimes) {
                if (currentTime - time <= TIME_WINDOW_MS) {
                    validPresses.add(time);
                }
            }
            
            // Save valid presses
            savePressTimes(context, validPresses);
            
            Log.d(TAG, "Power button presses: " + validPresses.size() + " within 2 seconds");
            
            if (validPresses.size() >= REQUIRED_PRESSES) {
                Log.d(TAG, "🚨🚨🚨 3 POWER BUTTON PRESSES DETECTED!");
                triggerSOS(context);
                clearPressTimes(context);
            }
        }
    }

    private boolean isSOSEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_SOS_ENABLED, false);
    }

    public static void setSOSEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_SOS_ENABLED, enabled).apply();
        Log.d(TAG, "SOS mode " + (enabled ? "ENABLED" : "DISABLED"));
    }

    private List<Long> getPressTimes(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> timesSet = prefs.getStringSet(KEY_PRESS_TIMES, new HashSet<>());
        
        List<Long> times = new ArrayList<>();
        for (String timeStr : timesSet) {
            try {
                times.add(Long.parseLong(timeStr));
            } catch (NumberFormatException e) {
                // Ignore invalid entries
            }
        }
        return times;
    }

    private void savePressTimes(Context context, List<Long> times) {
        Set<String> timesSet = new HashSet<>();
        for (Long time : times) {
            timesSet.add(String.valueOf(time));
        }
        
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putStringSet(KEY_PRESS_TIMES, timesSet).apply();
    }

    private void clearPressTimes(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_PRESS_TIMES).apply();
    }

    private void triggerSOS(Context context) {
        // Get contacts from SharedPreferences
        ArrayList<EmergencyContact> contacts = getStoredContacts(context);
        
        if (contacts.isEmpty()) {
            Log.w(TAG, "⚠️ No emergency contacts configured - cannot trigger SOS");
            return;
        }

        Log.d(TAG, "✅ Triggering SOS with " + contacts.size() + " contacts");

        // Start SOS service
        Intent serviceIntent = new Intent(context, SOSService.class);
        serviceIntent.putExtra("contacts", contacts);
        serviceIntent.putExtra("manual_trigger", true);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            Log.d(TAG, "🚨 SOS Service started from power button!");
        } catch (Exception e) {
            Log.e(TAG, "❌ Failed to start SOS service: " + e.getMessage());
        }
    }

    private void checkAndRestoreSOS(Context context) {
        // If SOS was enabled before reboot, keep it enabled
        if (isSOSEnabled(context)) {
            Log.d(TAG, "SOS was enabled before reboot - keeping enabled");
        }
    }

    private ArrayList<EmergencyContact> getStoredContacts(Context context) {
        ArrayList<EmergencyContact> contacts = new ArrayList<>();
        
        Set<String> contactSet = context.getSharedPreferences("ContactsPrefs", Context.MODE_PRIVATE)
                .getStringSet("emergency_contacts", new HashSet<>());

        for (String data : contactSet) {
            EmergencyContact contact = EmergencyContact.fromStorageString(data);
            if (contact != null) {
                contacts.add(contact);
            }
        }
        
        Log.d(TAG, "Loaded " + contacts.size() + " contacts from storage");
        return contacts;
    }

    public static void reset(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
            .remove(KEY_PRESS_TIMES)
            .remove(KEY_SOS_ENABLED)
            .apply();
        Log.d(TAG, "🔄 PowerButtonReceiver reset");
    }
}