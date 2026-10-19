package com.example.womensafety.location;

import android.content.Context;
import android.location.Location;
import android.os.BatteryManager;

import androidx.annotation.NonNull;

import com.example.womensafety.core.Backend;
import com.google.firebase.database.ServerValue;

import java.util.HashMap;
import java.util.Map;

/** Writes the user's position to {@code live/{uid}/location} for her circle to see. */
public final class LiveLocation {

    private LiveLocation() {
    }

    public static void publish(@NonNull Context context, @NonNull String uid, @NonNull Location loc) {
        Map<String, Object> m = new HashMap<>();
        m.put("lat", loc.getLatitude());
        m.put("lng", loc.getLongitude());
        m.put("acc", loc.hasAccuracy() ? (double) loc.getAccuracy() : null);
        m.put("speed", loc.hasSpeed() ? (double) loc.getSpeed() : null);
        m.put("battery", batteryPercent(context));
        m.put("ts", ServerValue.TIMESTAMP);
        Backend.location(uid).setValue(m);
    }

    public static int batteryPercent(@NonNull Context context) {
        BatteryManager bm = context.getSystemService(BatteryManager.class);
        if (bm == null) return -1;
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }
}
