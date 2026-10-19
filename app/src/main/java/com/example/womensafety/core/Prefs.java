package com.example.womensafety.core;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Single place for everything the app keeps on the device.
 *
 * <p>Key names for the profile and emergency contacts are kept identical to the previous
 * version of the app so existing users keep their data after updating.
 */
public final class Prefs {

    private static final String FILE = "WomenSafetyPrefs";

    // Kept from v5 for compatibility.
    private static final String KEY_NAME = "userName";
    private static final String KEY_PHONE = "userPhone";
    private static final String KEY_CONTACTS = "emergency_contacts";
    // Removed in v6: passwords were stored in plain text. Deleted on first launch.
    private static final String LEGACY_PASSWORD = "userPassword";
    private static final String LEGACY_EMAIL = "userEmail";
    private static final String LEGACY_LOGGED_IN = "isLoggedIn";

    private static final String KEY_ONBOARDED = "onboarded_v6";
    private static final String KEY_SHARING = "location_sharing_enabled";
    private static final String KEY_HAS_GUARDIANS = "has_guardians";
    private static final String KEY_WATCHING = "watching_count";
    private static final String KEY_SOS_SESSION = "sos_session";
    private static final String KEY_LAST_CITY = "last_city";
    private static final String KEY_LAST_LAT = "last_lat";
    private static final String KEY_LAST_LNG = "last_lng";

    private static volatile Prefs instance;
    private final SharedPreferences sp;

    private Prefs(Context context) {
        sp = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
        migrate();
    }

    public static Prefs get(@NonNull Context context) {
        Prefs local = instance;
        if (local == null) {
            synchronized (Prefs.class) {
                local = instance;
                if (local == null) {
                    local = new Prefs(context);
                    instance = local;
                }
            }
        }
        return local;
    }

    private void migrate() {
        if (!sp.contains(LEGACY_PASSWORD) && !sp.contains(LEGACY_LOGGED_IN)) return;
        SharedPreferences.Editor e = sp.edit()
                .remove(LEGACY_PASSWORD)
                .remove(LEGACY_EMAIL)
                .remove(LEGACY_LOGGED_IN);
        // Anyone who registered in the old app already has a name; skip onboarding for them.
        if (!getName().isEmpty()) e.putBoolean(KEY_ONBOARDED, true);
        e.apply();
    }

    // ---------- Profile ----------

    public boolean isOnboarded() {
        return sp.getBoolean(KEY_ONBOARDED, false);
    }

    public void setOnboarded(boolean value) {
        sp.edit().putBoolean(KEY_ONBOARDED, value).apply();
    }

    @NonNull
    public String getName() {
        String name = sp.getString(KEY_NAME, "");
        return name == null ? "" : name.trim();
    }

    @NonNull
    public String getFirstName() {
        String name = getName();
        int space = name.indexOf(' ');
        return space > 0 ? name.substring(0, space) : name;
    }

    @NonNull
    public String getPhone() {
        String phone = sp.getString(KEY_PHONE, "");
        return phone == null ? "" : phone.trim();
    }

    public void saveProfile(@NonNull String name, @NonNull String phone) {
        sp.edit().putString(KEY_NAME, name.trim()).putString(KEY_PHONE, phone.trim()).apply();
    }

    // ---------- Emergency contacts (SMS fallback) ----------

    @NonNull
    public List<EmergencyContact> getContacts() {
        Set<String> raw = sp.getStringSet(KEY_CONTACTS, Collections.emptySet());
        List<EmergencyContact> list = new ArrayList<>();
        if (raw != null) {
            for (String entry : raw) {
                EmergencyContact c = EmergencyContact.fromStorageString(entry);
                if (c != null) list.add(c);
            }
        }
        Collections.sort(list, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        return list;
    }

    public void addContact(@NonNull EmergencyContact contact) {
        Set<String> updated = new HashSet<>(sp.getStringSet(KEY_CONTACTS, Collections.emptySet()));
        updated.add(contact.toStorageString());
        sp.edit().putStringSet(KEY_CONTACTS, updated).apply();
    }

    public void removeContact(@NonNull EmergencyContact contact) {
        Set<String> updated = new HashSet<>(sp.getStringSet(KEY_CONTACTS, Collections.emptySet()));
        updated.remove(contact.toStorageString());
        sp.edit().putStringSet(KEY_CONTACTS, updated).apply();
    }

    // ---------- Live sharing ----------

    /** The user's own switch for always-on location sharing with her circle. */
    public boolean isSharingEnabled() {
        return sp.getBoolean(KEY_SHARING, true);
    }

    public void setSharingEnabled(boolean enabled) {
        sp.edit().putBoolean(KEY_SHARING, enabled).apply();
    }

    /** Cached so the boot receiver knows whether to restart sharing without a network call. */
    public boolean hasGuardians() {
        return sp.getBoolean(KEY_HAS_GUARDIANS, false);
    }

    public void setHasGuardians(boolean value) {
        sp.edit().putBoolean(KEY_HAS_GUARDIANS, value).apply();
    }

    /** Number of people this user looks after (as a guardian). */
    public int getWatchingCount() {
        return sp.getInt(KEY_WATCHING, 0);
    }

    public void setWatchingCount(int count) {
        sp.edit().putInt(KEY_WATCHING, count).apply();
    }

    // ---------- SOS ----------

    @Nullable
    public String getActiveSosSession() {
        return sp.getString(KEY_SOS_SESSION, null);
    }

    public boolean isSosActive() {
        return getActiveSosSession() != null;
    }

    /** Uses commit() so the state is on disk before a lock-screen trampoline finishes. */
    public void setActiveSosSession(@Nullable String sessionId) {
        if (sessionId == null) {
            sp.edit().remove(KEY_SOS_SESSION).commit();
        } else {
            sp.edit().putString(KEY_SOS_SESSION, sessionId).commit();
        }
    }

    // ---------- Last known place (used by News / Map before a fresh fix arrives) ----------

    @NonNull
    public String getLastCity() {
        String city = sp.getString(KEY_LAST_CITY, "");
        return city == null ? "" : city;
    }

    public void setLastPlace(@NonNull String city, double lat, double lng) {
        sp.edit()
                .putString(KEY_LAST_CITY, city)
                .putLong(KEY_LAST_LAT, Double.doubleToRawLongBits(lat))
                .putLong(KEY_LAST_LNG, Double.doubleToRawLongBits(lng))
                .apply();
    }

    public boolean hasLastPlace() {
        return sp.contains(KEY_LAST_LAT);
    }

    public double getLastLat() {
        return Double.longBitsToDouble(sp.getLong(KEY_LAST_LAT, 0));
    }

    public double getLastLng() {
        return Double.longBitsToDouble(sp.getLong(KEY_LAST_LNG, 0));
    }

    /** Clears everything (used by "Reset app" in Profile). */
    public void clearAll() {
        sp.edit().clear().commit();
    }
}
