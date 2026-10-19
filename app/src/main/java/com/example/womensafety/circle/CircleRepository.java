package com.example.womensafety.circle;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.womensafety.core.Backend;
import com.example.womensafety.core.Prefs;
import com.example.womensafety.guardian.GuardianWatchService;
import com.example.womensafety.location.LocationShareService;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Linking phones with a QR code.
 *
 * <p>Flow: the woman's phone creates a one-time code (valid 10 minutes) and shows it as a QR.
 * Her mother's / brother's phone scans it and joins her circle. Database rules only accept the
 * join if the code matches and hasn't expired, and she is notified every time someone joins.
 */
public final class CircleRepository {

    public static final long INVITE_TTL_MS = 10 * 60_000L;
    private static final String SCHEME = "wsafety";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private CircleRepository() {
    }

    public interface Result<T> {
        void onSuccess(T value);

        void onError(@NonNull Exception e);
    }

    /** Removes a database listener. */
    public interface Subscription {
        void remove();
    }

    /** A QR invite the woman shows to her family. */
    public static final class Invite {
        public final String code;
        public final long expiresAt;

        Invite(String code, long expiresAt) {
            this.code = code;
            this.expiresAt = expiresAt;
        }

        @NonNull
        public String qrPayload() {
            return SCHEME + "://join?c=" + code;
        }
    }

    /** What a guardian sees after scanning, before confirming. */
    public static final class InviteInfo {
        public final String code;
        public final String wardUid;
        public final String wardName;
        public final long expiresAt;

        InviteInfo(String code, String wardUid, String wardName, long expiresAt) {
            this.code = code;
            this.wardUid = wardUid;
            this.wardName = wardName;
            this.expiresAt = expiresAt;
        }
    }

    // ---------------------------------------------------------------- ward side

    public static void createInvite(@NonNull Context context, @NonNull Result<Invite> result) {
        Backend.ensureSignedIn(context, new Backend.UidCallback() {
            @Override
            public void onReady(@NonNull String uid) {
                publishProfile(context, uid);
                String code = newCode();
                long expiresAt = System.currentTimeMillis() + INVITE_TTL_MS;
                Map<String, Object> m = new HashMap<>();
                m.put("wardUid", uid);
                m.put("wardName", Prefs.get(context).getName());
                m.put("expiresAt", expiresAt);
                Backend.invite(code).setValue(m)
                        .addOnSuccessListener(v -> result.onSuccess(new Invite(code, expiresAt)))
                        .addOnFailureListener(result::onError);
            }

            @Override
            public void onError(@NonNull Exception e) {
                result.onError(e);
            }
        });
    }

    public static void cancelInvite(@NonNull String code) {
        Backend.invite(code).removeValue();
    }

    public static void removeGuardian(@NonNull String wardUid, @NonNull String guardianUid,
                                      @NonNull Result<Void> result) {
        Backend.guardians(wardUid).child(guardianUid).removeValue()
                .addOnSuccessListener(v -> result.onSuccess(null))
                .addOnFailureListener(result::onError);
    }

    @NonNull
    public static Subscription listenGuardians(@NonNull Context context, @NonNull String wardUid,
                                               @NonNull Consumer<List<Person>> onChange) {
        Context app = context.getApplicationContext();
        DatabaseReference ref = Backend.guardians(wardUid);
        ValueEventListener l = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Person> people = readPeople(snapshot);
                boolean had = Prefs.get(app).hasGuardians();
                Prefs.get(app).setHasGuardians(!people.isEmpty());
                if (had != !people.isEmpty()) LocationShareService.sync(app);
                onChange.accept(people);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                onChange.accept(Collections.emptyList());
            }
        };
        ref.addValueEventListener(l);
        return () -> ref.removeEventListener(l);
    }

    // ---------------------------------------------------------------- guardian side

    /** Extracts the code from a scanned QR (accepts the full link or just the code). */
    @Nullable
    public static String parseCode(@Nullable String scanned) {
        if (scanned == null) return null;
        String s = scanned.trim();
        if (s.startsWith(SCHEME + "://")) {
            String c = Uri.parse(s).getQueryParameter("c");
            s = c == null ? "" : c;
        }
        s = s.toUpperCase(Locale.ROOT);
        if (s.length() != 10) return null;
        for (int i = 0; i < s.length(); i++) if (ALPHABET.indexOf(s.charAt(i)) < 0) return null;
        return s;
    }

    public static void lookupInvite(@NonNull Context context, @NonNull String code,
                                    @NonNull Result<InviteInfo> result) {
        Backend.ensureSignedIn(context, new Backend.UidCallback() {
            @Override
            public void onReady(@NonNull String uid) {
                Backend.invite(code).get().addOnSuccessListener(snap -> {
                    String wardUid = snap.child("wardUid").getValue(String.class);
                    String wardName = snap.child("wardName").getValue(String.class);
                    Long expiresAt = snap.child("expiresAt").getValue(Long.class);
                    if (wardUid == null || expiresAt == null) {
                        result.onError(new IllegalArgumentException("not_found"));
                    } else if (expiresAt < System.currentTimeMillis()) {
                        result.onError(new IllegalStateException("expired"));
                    } else if (wardUid.equals(uid)) {
                        result.onError(new IllegalStateException("self"));
                    } else {
                        result.onSuccess(new InviteInfo(code, wardUid,
                                wardName == null ? "" : wardName, expiresAt));
                    }
                }).addOnFailureListener(result::onError);
            }

            @Override
            public void onError(@NonNull Exception e) {
                result.onError(e);
            }
        });
    }

    public static void acceptInvite(@NonNull Context context, @NonNull InviteInfo invite,
                                    @NonNull Result<Void> result) {
        Context app = context.getApplicationContext();
        String me = Backend.uid(app);
        if (me == null) {
            result.onError(new IllegalStateException("not_signed_in"));
            return;
        }
        publishProfile(app, me);
        Prefs prefs = Prefs.get(app);
        Map<String, Object> link = new HashMap<>();
        link.put("name", prefs.getName());
        link.put("phone", prefs.getPhone());
        link.put("code", invite.code);
        link.put("since", ServerValue.TIMESTAMP);
        Backend.guardians(invite.wardUid).child(me).setValue(link)
                .addOnSuccessListener(v -> {
                    Map<String, Object> w = new HashMap<>();
                    w.put("name", invite.wardName);
                    w.put("since", ServerValue.TIMESTAMP);
                    Backend.watching(me).child(invite.wardUid).setValue(w)
                            .addOnSuccessListener(v2 -> {
                                prefs.setWatchingCount(Math.max(1, prefs.getWatchingCount() + 1));
                                GuardianWatchService.sync(app);
                                result.onSuccess(null);
                            })
                            .addOnFailureListener(result::onError);
                })
                .addOnFailureListener(result::onError);
    }

    /** Stop looking after someone. */
    public static void leave(@NonNull String myUid, @NonNull String wardUid, @NonNull Result<Void> result) {
        Backend.watching(myUid).child(wardUid).removeValue();
        Backend.guardians(wardUid).child(myUid).removeValue()
                .addOnSuccessListener(v -> result.onSuccess(null))
                .addOnFailureListener(e -> result.onSuccess(null)); // already removed by her
    }

    @NonNull
    public static Subscription listenWatching(@NonNull Context context, @NonNull String myUid,
                                              @NonNull Consumer<List<Person>> onChange) {
        Context app = context.getApplicationContext();
        DatabaseReference ref = Backend.watching(myUid);
        ValueEventListener l = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<Person> people = readPeople(snapshot);
                int before = Prefs.get(app).getWatchingCount();
                Prefs.get(app).setWatchingCount(people.size());
                if (before != people.size()) GuardianWatchService.sync(app);
                onChange.accept(people);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                onChange.accept(Collections.emptyList());
            }
        };
        ref.addValueEventListener(l);
        return () -> ref.removeEventListener(l);
    }

    // ---------------------------------------------------------------- shared

    /** Name and phone, visible only to linked people (used for the "Call" button). */
    public static void publishProfile(@NonNull Context context, @NonNull String uid) {
        Prefs prefs = Prefs.get(context);
        Map<String, Object> m = new HashMap<>();
        m.put("name", prefs.getName());
        m.put("phone", prefs.getPhone());
        Backend.user(uid).setValue(m);
    }

    @NonNull
    private static List<Person> readPeople(@NonNull DataSnapshot snapshot) {
        List<Person> people = new ArrayList<>();
        for (DataSnapshot p : snapshot.getChildren()) {
            String id = p.getKey();
            if (id == null) continue;
            Long since = p.child("since").getValue(Long.class);
            people.add(new Person(id, p.child("name").getValue(String.class),
                    p.child("phone").getValue(String.class), since == null ? 0 : since));
        }
        Collections.sort(people, (a, b) -> Long.compare(a.since, b.since));
        return people;
    }

    @NonNull
    private static String newCode() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        return sb.toString();
    }
}
