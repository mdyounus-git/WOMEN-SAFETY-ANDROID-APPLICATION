package com.example.womensafety.sos;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
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
import com.example.womensafety.evidence.EvidenceStore;
import com.example.womensafety.live.Broadcaster;
import com.example.womensafety.location.LiveLocation;
import com.example.womensafety.ui.MainActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.firebase.database.ServerValue;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

/**
 * The SOS itself. While running it:
 * <ol>
 *   <li>tracks location every few seconds and shares it with the circle,</li>
 *   <li>flags SOS in the database so guardians' phones ring an alarm,</li>
 *   <li>opens camera + microphone: live stream to guardians and evidence clips,</li>
 *   <li>texts the emergency contacts (works without internet).</li>
 * </ol>
 */
public class SosService extends Service implements Broadcaster.Listener {

    private static final String TAG = "SosService";
    public static final String ACTION_START = "com.example.womensafety.SOS_START";
    public static final String ACTION_STOP = "com.example.womensafety.SOS_STOP";
    public static final String EXTRA_SOURCE = "source";

    private static final long LOCATION_INTERVAL_MS = 5_000L;
    private static final long FIRST_SMS_TIMEOUT_MS = 8_000L;
    private static final long SMS_REPEAT_MS = 3 * 60_000L;
    private static final long MAX_DURATION_MS = 3 * 60 * 60_000L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private FusedLocationProviderClient fused;
    private LocationCallback locationCallback;
    @Nullable
    private Location lastLocation;
    private boolean firstSmsSent;

    private Broadcaster broadcaster;
    private EvidenceStore evidence;
    private SmsAlerter sms;
    private PowerManager.WakeLock wakeLock;
    private String sessionId;
    @Nullable
    private String uid;
    private boolean running;
    private boolean cameraOn;
    private boolean micOn;
    private long startedAt;
    private int smsSent;
    private int viewers;

    private final Runnable firstSmsTimeout = () -> sendSms(false);
    private final Runnable repeatSms = new Runnable() {
        @Override
        public void run() {
            sendSms(true);
            main.postDelayed(this, SMS_REPEAT_MS);
        }
    };
    private final Runnable statusTick = new Runnable() {
        @Override
        public void run() {
            publishStatus();
            main.postDelayed(this, 2_000L);
        }
    };
    private final Runnable autoStop = () -> {
        Log.i(TAG, "SOS reached maximum duration; stopping");
        stopSos();
    };

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSos();
            return START_NOT_STICKY;
        }
        if (running) return START_STICKY;
        // intent == null means Android restarted us after killing the process: resume SOS.
        if (action == null && !Prefs.get(this).isSosActive()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        begin();
        return START_STICKY;
    }

    // ------------------------------------------------------------------

    private void begin() {
        Prefs prefs = Prefs.get(this);
        sessionId = prefs.getActiveSosSession();
        if (sessionId == null) sessionId = newSessionId();
        prefs.setActiveSosSession(sessionId);
        startedAt = System.currentTimeMillis();

        if (!goForeground()) {
            prefs.setActiveSosSession(null);
            stopSelf();
            return;
        }
        running = true;
        Haptics.sosConfirmed(this);
        acquireWakeLock();

        evidence = new EvidenceStore(this, sessionId);
        sms = new SmsAlerter(this);
        startLocation();
        if (sms.hasRecipients()) main.postDelayed(firstSmsTimeout, FIRST_SMS_TIMEOUT_MS);

        broadcaster = new Broadcaster(this, evidence, cameraOn, micOn, this);
        if (cameraOn || micOn) broadcaster.start();

        if (Backend.isConfigured(this)) {
            Backend.ensureSignedIn(this, new Backend.UidCallback() {
                @Override
                public void onReady(@NonNull String id) {
                    if (!running) return;
                    uid = id;
                    setSosFlag(true);
                    evidence.enableUpload(id);
                    if (cameraOn || micOn) broadcaster.attachBackend(id);
                    if (lastLocation != null) LiveLocation.publish(SosService.this, id, lastLocation);
                }

                @Override
                public void onError(@NonNull Exception e) {
                    Log.w(TAG, "Offline: SOS continues with SMS and local evidence", e);
                }
            });
        }
        main.post(statusTick);
        main.postDelayed(autoStop, MAX_DURATION_MS);
        Log.i(TAG, "SOS started (camera=" + cameraOn + ", mic=" + micOn + ")");
    }

    /**
     * Starts the foreground service with whatever the user has allowed. Android only grants
     * camera/microphone here if SOS was started while the app was visible (button, or the
     * volume-key trampoline); otherwise we fall back to location only.
     */
    private boolean goForeground() {
        boolean foreground = Permissions.isAppInForeground();
        boolean loc = Permissions.hasLocation(this) && (foreground || Permissions.hasBackgroundLocation(this));
        // Android only allows camera/microphone in a service started while the app is visible.
        cameraOn = foreground && Permissions.hasCamera(this);
        micOn = foreground && Permissions.hasMicrophone(this);
        Notification n = buildNotification();

        int full = (loc ? ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION : 0)
                | (cameraOn ? ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA : 0)
                | (micOn ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE : 0);
        if (tryForeground(n, full)) return true;
        cameraOn = false;
        micOn = false;
        if (loc && tryForeground(n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)) return true;
        // Last resort: keep whichever single type works so the service never fails to start.
        if (Permissions.hasMicrophone(this) && tryForeground(n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)) {
            micOn = true;
            return true;
        }
        if (Permissions.hasCamera(this) && tryForeground(n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)) {
            cameraOn = true;
            return true;
        }
        Log.e(TAG, "Could not start SOS in the foreground");
        return false;
    }

    private boolean tryForeground(Notification n, int types) {
        if (types == 0) return false;
        try {
            ServiceCompat.startForeground(this, Notifications.ID_SOS, n, types);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "startForeground(" + types + ") refused", e);
            return false;
        }
    }

    @SuppressLint("MissingPermission")
    private void startLocation() {
        if (!Permissions.hasLocation(this)) return;
        fused = LocationServices.getFusedLocationProviderClient(this);
        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, LOCATION_INTERVAL_MS)
                .setMinUpdateIntervalMillis(2_000L)
                .build();
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult result) {
                Location loc = result.getLastLocation();
                if (loc != null) onLocation(loc);
            }
        };
        fused.getLastLocation().addOnSuccessListener(loc -> {
            if (loc != null && lastLocation == null) onLocation(loc);
        });
        fused.requestLocationUpdates(req, locationCallback, Looper.getMainLooper());
    }

    private void onLocation(@NonNull Location loc) {
        if (!running) return;
        lastLocation = loc;
        if (uid != null) LiveLocation.publish(this, uid, loc);
        // First SMS goes out as soon as we have a fix (or after a short timeout without one).
        if (!firstSmsSent && sms.hasRecipients() && loc.hasAccuracy() && loc.getAccuracy() < 100) {
            main.removeCallbacks(firstSmsTimeout);
            sendSms(false);
        }
    }

    private void sendSms(boolean followUp) {
        if (!running) return;
        if (!followUp) {
            if (firstSmsSent) return;
            firstSmsSent = true;
            main.postDelayed(repeatSms, SMS_REPEAT_MS);
        }
        smsSent += sms.send(lastLocation, followUp);
        publishStatus();
    }

    private void setSosFlag(boolean active) {
        if (uid == null) return;
        Map<String, Object> m = new HashMap<>();
        m.put("active", active);
        m.put("sessionId", sessionId);
        m.put(active ? "startedAt" : "endedAt", ServerValue.TIMESTAMP);
        Backend.sos(uid).updateChildren(m);
    }

    @Override
    public void onViewerCountChanged(int count) {
        viewers = count;
        publishStatus();
        Notifications.notifySafely(this, Notifications.ID_SOS, buildNotification());
    }

    private void publishStatus() {
        if (!running) return;
        SosState.publish(new SosState.Status(true, startedAt, smsSent, viewers,
                evidence == null ? 0 : evidence.getPhotoCount(),
                evidence == null ? 0 : evidence.getAudioCount() + evidence.getVideoCount(), cameraOn, micOn));
    }

    private Notification buildNotification() {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String text = viewers > 0
                ? getResources().getQuantityString(R.plurals.sos_notif_watching, viewers, viewers)
                : getString(R.string.sos_notif_text);
        return new NotificationCompat.Builder(this, Notifications.CH_SOS)
                .setSmallIcon(R.drawable.ic_shield_fill)
                .setColor(ContextCompat.getColor(this, R.color.ws_sos))
                .setContentTitle(getString(R.string.sos_notif_title))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(open)
                .build();
    }

    private void acquireWakeLock() {
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm == null) return;
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WomenSafety:SOS");
        wakeLock.acquire(MAX_DURATION_MS);
    }

    private void stopSos() {
        Prefs.get(this).setActiveSosSession(null);
        if (running) {
            running = false;
            setSosFlag(false);
            Haptics.sosStopped(this);
        }
        cleanup();
        SosState.publishInactive();
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        stopSelf();
        Log.i(TAG, "SOS stopped");
    }

    private void cleanup() {
        main.removeCallbacksAndMessages(null);
        if (fused != null && locationCallback != null) fused.removeLocationUpdates(locationCallback);
        locationCallback = null;
        if (broadcaster != null) broadcaster.stop();
        broadcaster = null;
        if (evidence != null) evidence.close();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        wakeLock = null;
    }

    @Override
    public void onDestroy() {
        // If Android destroys us without ACTION_STOP, the saved session lets START_STICKY resume.
        cleanup();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static String newSessionId() {
        // Starts with the timestamp so sessions sort and can be dated.
        return System.currentTimeMillis() + "-" + Integer.toHexString(new SecureRandom().nextInt(0xFFFF));
    }
}
