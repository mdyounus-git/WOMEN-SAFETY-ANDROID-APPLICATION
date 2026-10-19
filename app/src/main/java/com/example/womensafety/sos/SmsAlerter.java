package com.example.womensafety.sos;

import android.content.Context;
import android.location.Location;
import android.os.Build;
import android.telephony.SmsManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.womensafety.R;
import com.example.womensafety.core.EmergencyContact;
import com.example.womensafety.core.Permissions;
import com.example.womensafety.core.Prefs;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Text-message fallback: works without internet and reaches people who don't have the app.
 * Uses the phone's normal SMS plan.
 */
final class SmsAlerter {

    private static final String TAG = "SmsAlerter";

    private final Context context;

    SmsAlerter(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    boolean hasRecipients() {
        return !Prefs.get(context).getContacts().isEmpty();
    }

    /** @return number of contacts the message was handed to. */
    int send(@Nullable Location location, boolean followUp) {
        if (!Permissions.hasSms(context)) {
            Log.w(TAG, "SMS permission not granted");
            return 0;
        }
        List<EmergencyContact> contacts = Prefs.get(context).getContacts();
        if (contacts.isEmpty()) return 0;

        String name = Prefs.get(context).getName();
        if (name.isEmpty()) name = context.getString(R.string.sms_someone);
        String where = location == null
                ? context.getString(R.string.sms_location_unknown)
                : "https://maps.google.com/?q=" + String.format(Locale.US, "%.6f,%.6f",
                location.getLatitude(), location.getLongitude());
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        String message = context.getString(followUp ? R.string.sms_update : R.string.sms_first,
                name, where, time);

        SmsManager sms = smsManager();
        if (sms == null) return 0;
        int sent = 0;
        for (EmergencyContact c : contacts) {
            try {
                ArrayList<String> parts = sms.divideMessage(message);
                sms.sendMultipartTextMessage(c.getDialableNumber(), null, parts, null, null);
                sent++;
            } catch (Exception e) {
                Log.e(TAG, "SMS failed for a contact", e);
            }
        }
        return sent;
    }

    @SuppressWarnings("deprecation")
    @Nullable
    private SmsManager smsManager() {
        try {
            if (Build.VERSION.SDK_INT >= 31) return context.getSystemService(SmsManager.class);
            return SmsManager.getDefault();
        } catch (Exception e) {
            return null;
        }
    }
}
