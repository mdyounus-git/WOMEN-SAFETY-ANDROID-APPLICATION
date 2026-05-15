package com.example.womensafety;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.telephony.SmsManager;
import android.util.Log;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class SOSService extends Service {
    private static final String TAG = "SOSService";
    private static final String CHANNEL_ID = "SOSServiceChannel";
    private static final int NOTIFICATION_ID = 1;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private MediaRecorder mediaRecorder;
    private CameraHelper cameraHelper;
    private Handler cameraHandler;
    private Handler smsHandler;
    private ArrayList<EmergencyContact> contacts;
    private Location lastKnownLocation = null;
    private boolean isSOSTriggered = false;
    private File audioFile;
    private int smsCount = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            cameraHelper = new CameraHelper(this);
            cameraHandler = new Handler(Looper.getMainLooper());
            smsHandler = new Handler(Looper.getMainLooper());
            startLocationUpdates();
            Log.d(TAG, "✅ SOS Service Created");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error creating SOS Service", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            loadContactsFromPreferences();
            createNotificationChannel();
            startForeground(NOTIFICATION_ID, createNotification("SOS Service Active"));
            if (!isSOSTriggered) {
                triggerSOS();
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error in onStartCommand", e);
            Toast.makeText(this, "❌ SOS Service failed to start", Toast.LENGTH_LONG).show();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private void loadContactsFromPreferences() {
        try {
            SharedPreferences prefs = getSharedPreferences("WomenSafetyPrefs", MODE_PRIVATE);
            Set<String> contactsSet = prefs.getStringSet("emergency_contacts", new HashSet<>());
            contacts = new ArrayList<>();
            if (contactsSet != null && !contactsSet.isEmpty()) {
                for (String contactStr : contactsSet) {
                    EmergencyContact contact = EmergencyContact.fromStorageString(contactStr);
                    if (contact != null) {
                        contacts.add(contact);
                    }
                }
                Log.d(TAG, "✅ Loaded " + contacts.size() + " contacts");
            } else {
                Log.e(TAG, "❌ No contacts found!");
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Error loading contacts", e);
            contacts = new ArrayList<>();
        }
    }

    private void triggerSOS() {
        if (isSOSTriggered) return;
        try {
            isSOSTriggered = true;
            updateNotification("🚨 SOS TRIGGERED! Getting location...");
            Log.d(TAG, "🚨 SOS Triggered!");
            Toast.makeText(this, "🚨 SOS ACTIVATED!", Toast.LENGTH_LONG).show();
            waitForLocationThenSendSMS();
            startAudioRecording();
            startCameraCapture();
            startContinuousSMS();
        } catch (Exception e) {
            Log.e(TAG, "❌ Error triggering SOS", e);
        }
    }

    private void startLocationUpdates() {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "❌ Location permission not granted");
                return;
            }
            LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000).setMinUpdateIntervalMillis(5000).build();
            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(LocationResult locationResult) {
                    if (locationResult != null && locationResult.getLastLocation() != null) {
                        lastKnownLocation = locationResult.getLastLocation();
                        Log.d(TAG, "📍 Location updated: " + lastKnownLocation.getLatitude() + "," + lastKnownLocation.getLongitude());
                    }
                }
            };
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    lastKnownLocation = location;
                    Log.d(TAG, "✅ Got last known location");
                }
            });
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        } catch (Exception e) {
            Log.e(TAG, "❌ Error starting location updates", e);
        }
    }

    private void waitForLocationThenSendSMS() {
        if (smsHandler != null) {
            smsHandler.postDelayed(() -> sendSMSAlerts(), 3000);
        }
    }

    private void startContinuousSMS() {
        if (smsHandler != null) {
            smsHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (isSOSTriggered) {
                        sendSMSAlerts();
                        smsHandler.postDelayed(this, 10000);
                    }
                }
            }, 10000);
        }
    }

    private void sendSMSAlerts() {
        try {
            if (contacts == null || contacts.isEmpty()) {
                Log.e(TAG, "❌ No contacts to send SMS");
                updateNotification("⚠️ No emergency contacts!");
                return;
            }
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "❌ SMS permission not granted");
                return;
            }
            SharedPrefManager prefManager = SharedPrefManager.getInstance(this);
            String userName = prefManager.getUserName();
            if (userName == null || userName.isEmpty()) userName = "User";
            String locationText;
            if (lastKnownLocation != null) {
                double lat = lastKnownLocation.getLatitude();
                double lng = lastKnownLocation.getLongitude();
                locationText = "https://maps.google.com/?q=" + lat + "," + lng;
            } else {
                locationText = "Location unavailable";
            }
            smsCount++;
            String message = "🚨 EMERGENCY ALERT #" + smsCount + " 🚨\n" + userName + " needs help!\nLocation: " + locationText + "\nTime: " + new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(new Date());
            Log.d(TAG, "📨 Sending SMS #" + smsCount);
            SmsManager smsManager = SmsManager.getDefault();
            int successCount = 0;
            for (EmergencyContact contact : contacts) {
                try {
                    String phoneNumber = contact.getPhoneNumber().replaceAll("[^0-9+]", "");
                    if (!phoneNumber.startsWith("+")) phoneNumber = "+91" + phoneNumber;
                    ArrayList<String> parts = smsManager.divideMessage(message);
                    smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null);
                    successCount++;
                    Log.d(TAG, "✅ SMS sent to: " + contact.getName());
                    Thread.sleep(100);
                } catch (Exception e) {
                    Log.e(TAG, "❌ Failed SMS to: " + contact.getName(), e);
                }
            }
            String resultMessage = "📨 SMS #" + smsCount + " sent to " + successCount + " contacts";
            updateNotification(resultMessage);
            Toast.makeText(this, resultMessage, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "❌ Error sending SMS", e);
        }
    }

    private void startAudioRecording() {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "❌ Audio permission not granted");
                return;
            }
            File audioDir = new File(getExternalFilesDir(null), "SOSAudio");
            if (!audioDir.exists()) audioDir.mkdirs();
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            audioFile = new File(audioDir, "SOS_Audio_" + timestamp + ".3gp");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mediaRecorder = new MediaRecorder(this);
            } else {
                mediaRecorder = new MediaRecorder();
            }
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mediaRecorder.setOutputFile(audioFile.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();
            Log.d(TAG, "✅ Audio recording started");
        } catch (Exception e) {
            Log.e(TAG, "❌ Audio recording failed", e);
        }
    }

    private void startCameraCapture() {
        if (cameraHandler != null) {
            cameraHandler.postDelayed(new Runnable() {
                int photoCount = 0;
                boolean useFrontCamera = false;
                @Override
                public void run() {
                    if (isSOSTriggered) {
                        try {
                            capturePhoto(useFrontCamera);
                            useFrontCamera = !useFrontCamera;
                            photoCount++;
                            updateNotification("📸 " + photoCount + " photos | 🎙️ Recording | 📨 " + smsCount + " SMS");
                            cameraHandler.postDelayed(this, 5000);
                        } catch (Exception e) {
                            Log.e(TAG, "❌ Camera error", e);
                        }
                    }
                }
            }, 2000);
        }
    }

    private void capturePhoto(boolean useFrontCamera) {
        try {
            if (cameraHelper != null && ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                cameraHelper.capturePhoto(useFrontCamera);
            }
        } catch (Exception e) {
            Log.e(TAG, "❌ Camera capture failed", e);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "SOS Service", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification(String contentText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("Women Safety - SOS Active").setContentText(contentText).setSmallIcon(android.R.drawable.ic_dialog_alert).setContentIntent(pendingIntent).setOngoing(true).setPriority(NotificationCompat.PRIORITY_HIGH).build();
    }

    private void updateNotification(String message) {
        try {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) manager.notify(NOTIFICATION_ID, createNotification(message));
        } catch (Exception e) {
            Log.e(TAG, "❌ Notification error", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (cameraHelper != null) cameraHelper.release();
            if (fusedLocationClient != null && locationCallback != null) fusedLocationClient.removeLocationUpdates(locationCallback);
            if (mediaRecorder != null) {
                try {
                    mediaRecorder.stop();
                    mediaRecorder.release();
                } catch (Exception e) {
                    Log.e(TAG, "Audio stop error", e);
                }
            }
            if (cameraHandler != null) cameraHandler.removeCallbacksAndMessages(null);
            if (smsHandler != null) smsHandler.removeCallbacksAndMessages(null);
            isSOSTriggered = false;
            smsCount = 0;
            Log.d(TAG, "✅ SOS Service Destroyed");
        } catch (Exception e) {
            Log.e(TAG, "❌ Error destroying service", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}