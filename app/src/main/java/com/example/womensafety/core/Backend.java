package com.example.womensafety.core;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Firebase access (free Spark plan: Anonymous Auth + Realtime Database).
 *
 * <p>Database layout — see {@code database.rules.json} in the project root for who can read what:
 * <pre>
 * users/{uid}                       name, phone
 * invites/{code}                    wardUid, wardName, expiresAt      (QR codes, 10 min)
 * links/{wardUid}/guardians/{gUid}  name, phone, code, since          (who can see the ward)
 * watching/{gUid}/{wardUid}         name, since                       (reverse index for guardians)
 * live/{wardUid}/location           lat, lng, acc, speed, battery, ts (always on)
 * live/{wardUid}/sos                active, sessionId, startedAt       (camera + mic only when active)
 * calls/{wardUid}/{gUid}            WebRTC signalling for the live stream
 * evidence/{wardUid}/{sessionId}    photos/*, audio/*                 (base64, kept as evidence)
 * </pre>
 */
public final class Backend {

    private static final String TAG = "Backend";
    private static volatile boolean persistenceSet;

    private Backend() {
    }

    /** False until app/google-services.json has been added and the app rebuilt. */
    public static boolean isConfigured(@NonNull Context context) {
        try {
            return !FirebaseApp.getApps(context.getApplicationContext()).isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    /** Call once from Application.onCreate. */
    public static void init(@NonNull Context context) {
        if (!isConfigured(context) || persistenceSet) return;
        try {
            // Queues writes (e.g. the SOS flag) while offline and sends them when back online.
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        } catch (Exception e) {
            Log.w(TAG, "Persistence already configured", e);
        }
        persistenceSet = true;
    }

    public interface UidCallback {
        void onReady(@NonNull String uid);

        void onError(@NonNull Exception e);
    }

    /** Signs in anonymously once; the uid then stays stable for this install. */
    public static void ensureSignedIn(@NonNull Context context, @NonNull UidCallback callback) {
        if (!isConfigured(context)) {
            callback.onError(new IllegalStateException("Firebase is not set up yet"));
            return;
        }
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            callback.onReady(user.getUid());
            return;
        }
        auth.signInAnonymously()
                .addOnSuccessListener(result -> {
                    FirebaseUser signedIn = result.getUser();
                    if (signedIn != null) callback.onReady(signedIn.getUid());
                    else callback.onError(new IllegalStateException("No user after sign-in"));
                })
                .addOnFailureListener(callback::onError);
    }

    @Nullable
    public static String uid(@NonNull Context context) {
        if (!isConfigured(context)) return null;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user == null ? null : user.getUid();
    }

    public static void signOut(@NonNull Context context) {
        if (isConfigured(context)) FirebaseAuth.getInstance().signOut();
    }

    // ---------- Paths ----------

    @NonNull
    public static DatabaseReference root() {
        return FirebaseDatabase.getInstance().getReference();
    }

    @NonNull
    public static DatabaseReference user(@NonNull String uid) {
        return root().child("users").child(uid);
    }

    @NonNull
    public static DatabaseReference invite(@NonNull String code) {
        return root().child("invites").child(code);
    }

    @NonNull
    public static DatabaseReference guardians(@NonNull String wardUid) {
        return root().child("links").child(wardUid).child("guardians");
    }

    @NonNull
    public static DatabaseReference watching(@NonNull String guardianUid) {
        return root().child("watching").child(guardianUid);
    }

    @NonNull
    public static DatabaseReference live(@NonNull String wardUid) {
        return root().child("live").child(wardUid);
    }

    @NonNull
    public static DatabaseReference location(@NonNull String wardUid) {
        return live(wardUid).child("location");
    }

    @NonNull
    public static DatabaseReference sos(@NonNull String wardUid) {
        return live(wardUid).child("sos");
    }

    @NonNull
    public static DatabaseReference calls(@NonNull String wardUid) {
        return root().child("calls").child(wardUid);
    }

    @NonNull
    public static DatabaseReference evidence(@NonNull String wardUid, @NonNull String sessionId) {
        return root().child("evidence").child(wardUid).child(sessionId);
    }
}
