package com.example.womensafety.location;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.core.content.ContextCompat;

import com.example.womensafety.R;
import com.example.womensafety.core.Backend;
import com.example.womensafety.core.Notifications;
import com.example.womensafety.core.Permissions;
import com.example.womensafety.core.Prefs;
import com.example.womensafety.ui.MainActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Always-on location sharing with the user's circle (camera and mic are never touched here).
 * Runs only while she has at least one guardian and sharing is switched on, and always shows a
 * notification naming who can see her — so sharing is never hidden from her.
 */
public class LocationShareService extends Service {

    private static final String TAG = "LocationShare";
    private static final long INTERVAL_MS = 30_000L;
    private static final long FASTEST_MS = 15_000L;
    private static final float MIN_DISTANCE_M = 15f;

    private FusedLocationProviderClient fused;
    private LocationCallback callback;
    private String uid;
    private DatabaseReference guardiansRef;
    private ValueEventListener guardiansListener;
    private ChildEventListener newGuardianListener;
    private final List<String> guardianNames = new ArrayList<>();
    private long startedAt;
    private boolean foreground;

    /** Starts or stops sharing to match the user's settings. Safe to call any time. */
    public static void sync(@NonNull Context context) {
        Prefs prefs = Prefs.get(context);
        boolean shouldRun = prefs.isSharingEnabled() && prefs.hasGuardians()
                && Backend.isConfigured(context) && Permissions.canStartLocationServiceNow(context);
        Intent i = new Intent(context, LocationShareService.class);
        if (shouldRun) {
            try {
                ContextCompat.startForegroundService(context, i);
            } catch (Exception e) {
                Log.w(TAG, "Could not start sharing from here", e);
            }
        } else {
            context.stopService(i);
        }
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (!foreground) {
            try {
                ServiceCompat.startForeground(this, Notifications.ID_SHARING, buildNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
                foreground = true;
            } catch (Exception e) {
                // e.g. started after reboot without "Allow all the time" location permission.
                Log.w(TAG, "Not allowed to share location right now", e);
                stopSelf();
                return START_NOT_STICKY;
            }
            startedAt = System.currentTimeMillis();
            Backend.ensureSignedIn(this, new Backend.UidCallback() {
                @Override
                public void onReady(@NonNull String id) {
                    uid = id;
                    watchGuardians();
                    startUpdates();
                }

                @Override
                public void onError(@NonNull Exception e) {
                    Log.w(TAG, "Sign-in failed; stopping", e);
                    stopSelf();
                }
            });
        }
        return START_STICKY;
    }

    private void watchGuardians() {
        guardiansRef = Backend.guardians(uid);
        guardiansListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                guardianNames.clear();
                for (DataSnapshot g : snapshot.getChildren()) {
                    String name = g.child("name").getValue(String.class);
                    guardianNames.add(name == null ? "?" : name);
                }
                Prefs.get(LocationShareService.this).setHasGuardians(!guardianNames.isEmpty());
                if (guardianNames.isEmpty()) {
                    stopSelf();
                } else {
                    Notifications.notifySafely(LocationShareService.this, Notifications.ID_SHARING,
                            buildNotification());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Guardian list unavailable: " + error.getMessage());
            }
        };
        guardiansRef.addValueEventListener(guardiansListener);

        // Tell her whenever someone new joins her circle (protects against a leaked QR code).
        newGuardianListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot s, @Nullable String prev) {
                Long since = s.child("since").getValue(Long.class);
                if (since == null || since < startedAt) return;
                String name = s.child("name").getValue(String.class);
                notifyNewGuardian(name == null ? "Someone" : name);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot s, @Nullable String prev) {
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot s) {
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot s, @Nullable String prev) {
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        };
        guardiansRef.addChildEventListener(newGuardianListener);
    }

    @SuppressLint("MissingPermission")
    private void startUpdates() {
        if (!Permissions.hasLocation(this)) {
            stopSelf();
            return;
        }
        fused = LocationServices.getFusedLocationProviderClient(this);
        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, INTERVAL_MS)
                .setMinUpdateIntervalMillis(FASTEST_MS)
                .setMinUpdateDistanceMeters(MIN_DISTANCE_M)
                .build();
        callback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location loc = result.getLastLocation();
                // During SOS the SOS service publishes faster, more precise fixes.
                if (loc != null && uid != null && !Prefs.get(LocationShareService.this).isSosActive()) {
                    LiveLocation.publish(LocationShareService.this, uid, loc);
                }
            }
        };
        fused.getLastLocation().addOnSuccessListener(loc -> {
            if (loc != null && uid != null) LiveLocation.publish(this, uid, loc);
        });
        fused.requestLocationUpdates(req, callback, Looper.getMainLooper());
    }

    private void notifyNewGuardian(String name) {
        Notification n = new NotificationCompat.Builder(this, Notifications.CH_CIRCLE)
                .setSmallIcon(R.drawable.ic_group)
                .setColor(ContextCompat.getColor(this, R.color.ws_plum))
                .setContentTitle(getString(R.string.notif_new_guardian_title, name))
                .setContentText(getString(R.string.notif_new_guardian_text))
                .setContentIntent(openApp())
                .setAutoCancel(true)
                .build();
        Notifications.notifySafely(this, Notifications.ID_CIRCLE, n);
    }

    private Notification buildNotification() {
        String who = guardianNames.isEmpty()
                ? getString(R.string.sharing_notif_your_circle)
                : android.text.TextUtils.join(", ", guardianNames);
        return new NotificationCompat.Builder(this, Notifications.CH_SHARING)
                .setSmallIcon(R.drawable.ic_location)
                .setColor(ContextCompat.getColor(this, R.color.ws_plum))
                .setContentTitle(getString(R.string.sharing_notif_title))
                .setContentText(getString(R.string.sharing_notif_text, who))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentIntent(openApp())
                .build();
    }

    private PendingIntent openApp() {
        return PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onDestroy() {
        if (fused != null && callback != null) fused.removeLocationUpdates(callback);
        if (guardiansRef != null) {
            if (guardiansListener != null) guardiansRef.removeEventListener(guardiansListener);
            if (newGuardianListener != null) guardiansRef.removeEventListener(newGuardianListener);
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
