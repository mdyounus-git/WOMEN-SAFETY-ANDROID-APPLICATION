package com.example.womensafety.location;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.womensafety.core.Prefs;
import com.example.womensafety.guardian.GuardianWatchService;
import com.example.womensafety.sos.SosTrigger;

/**
 * Restarts sharing and guardian alerts after the phone reboots or the app updates. If the phone
 * was switched off during an SOS, the SOS resumes (location + SMS) as soon as it turns back on.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        try {
            if (Prefs.get(context).isSosActive()) {
                SosTrigger.start(context, SosTrigger.SOURCE_RESUME);
            }
            LocationShareService.sync(context);
            GuardianWatchService.sync(context);
        } catch (Exception e) {
            Log.w("BootReceiver", "Could not restore services", e);
        }
    }
}
