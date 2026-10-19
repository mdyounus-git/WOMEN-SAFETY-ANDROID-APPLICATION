package com.example.womensafety.ui;

import android.content.Context;
import android.os.Build;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.example.womensafety.R;
import com.example.womensafety.core.Permissions;
import com.example.womensafety.core.Prefs;

import java.util.ArrayList;
import java.util.List;

/** The safety setup steps, shared by the Home card and the Setup screen. */
public final class SetupChecklist {

    public enum Key { LOCATION, BACKGROUND_LOCATION, NOTIFICATIONS, CAMERA_MIC, VOLUME, SMS, FULL_SCREEN, BATTERY }

    public static final class Item {
        public final Key key;
        @DrawableRes
        public final int icon;
        @StringRes
        public final int title;
        @StringRes
        public final int description;
        public final boolean done;

        Item(Key key, int icon, int title, int description, boolean done) {
            this.key = key;
            this.icon = icon;
            this.title = title;
            this.description = description;
            this.done = done;
        }
    }

    private SetupChecklist() {
    }

    @NonNull
    public static List<Item> items(@NonNull Context c) {
        List<Item> list = new ArrayList<>();
        list.add(new Item(Key.LOCATION, R.drawable.ic_location, R.string.setup_location,
                R.string.setup_location_desc, Permissions.hasPreciseLocation(c)));
        if (Build.VERSION.SDK_INT >= 29) {
            list.add(new Item(Key.BACKGROUND_LOCATION, R.drawable.ic_my_location, R.string.setup_bg_location,
                    R.string.setup_bg_location_desc, Permissions.hasBackgroundLocation(c)));
        }
        if (Build.VERSION.SDK_INT >= 33) {
            list.add(new Item(Key.NOTIFICATIONS, R.drawable.ic_bell, R.string.setup_notifications,
                    R.string.setup_notifications_desc, Permissions.hasNotifications(c)));
        }
        list.add(new Item(Key.CAMERA_MIC, R.drawable.ic_videocam, R.string.setup_camera_mic,
                R.string.setup_camera_mic_desc, Permissions.hasCamera(c) && Permissions.hasMicrophone(c)));
        list.add(new Item(Key.VOLUME, R.drawable.ic_volume_up, R.string.setup_volume,
                R.string.setup_volume_desc, Permissions.isVolumeTriggerEnabled(c)));
        list.add(new Item(Key.SMS, R.drawable.ic_sms, R.string.setup_sms,
                R.string.setup_sms_desc, Permissions.hasSms(c)));
        // Only relevant on a guardian's phone.
        if (Build.VERSION.SDK_INT >= 34 && Prefs.get(c).getWatchingCount() > 0) {
            list.add(new Item(Key.FULL_SCREEN, R.drawable.ic_warning, R.string.setup_full_screen,
                    R.string.setup_full_screen_desc, Permissions.canShowFullScreenAlerts(c)));
        }
        list.add(new Item(Key.BATTERY, R.drawable.ic_battery, R.string.setup_battery,
                R.string.setup_battery_desc, Permissions.isIgnoringBatteryOptimizations(c)));
        return list;
    }

    public static int doneCount(@NonNull List<Item> items) {
        int n = 0;
        for (Item i : items) if (i.done) n++;
        return n;
    }
}
