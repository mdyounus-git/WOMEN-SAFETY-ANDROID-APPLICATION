package com.example.womensafety.evidence;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.womensafety.core.Backend;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ServerValue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Saves SOS photos and audio clips on the phone (private app storage) and, when the backend is
 * set up, also copies them to Realtime Database so the circle keeps them even if the phone is
 * taken or broken.
 */
public final class EvidenceStore {

    private static final String TAG = "Evidence";
    private static final String ROOT = "evidence";
    /** Realtime Database rejects bigger items (see database.rules.json). */
    private static final int MAX_UPLOAD_BYTES = 280_000;

    private final File dir;
    private final String sessionId;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    @Nullable
    private volatile DatabaseReference remote;
    private volatile int photoCount;
    private volatile int audioCount;

    public EvidenceStore(@NonNull Context context, @NonNull String sessionId) {
        this.sessionId = sessionId;
        this.dir = new File(new File(context.getFilesDir(), ROOT), sessionId);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    /** Starts mirroring new evidence to the cloud for this user. */
    public void enableUpload(@NonNull String uid) {
        DatabaseReference ref = Backend.evidence(uid, sessionId);
        Map<String, Object> meta = new HashMap<>();
        meta.put("startedAt", ServerValue.TIMESTAMP);
        ref.child("meta").updateChildren(meta);
        remote = ref;
    }

    public int getPhotoCount() {
        return photoCount;
    }

    public int getAudioCount() {
        return audioCount;
    }

    public void savePhoto(@NonNull byte[] jpeg) {
        final long ts = System.currentTimeMillis();
        run(() -> {
            write(new File(dir, "photo_" + ts + ".jpg"), jpeg);
            photoCount++;
            upload("photos", "jpg", jpeg, ts, 0);
        });
    }

    public void saveAudio(@NonNull byte[] aacAdts, long durationMs) {
        final long ts = System.currentTimeMillis();
        run(() -> {
            write(new File(dir, "audio_" + ts + ".aac"), aacAdts);
            audioCount++;
            upload("audio", "aac", aacAdts, ts, durationMs);
        });
    }

    /** Moves a finished clip from the fallback recorder into this session. */
    public void importAudioFile(@NonNull File file, long durationMs) {
        final long ts = System.currentTimeMillis();
        run(() -> {
            File target = new File(dir, "audio_" + ts + ".aac");
            if (!file.renameTo(target)) target = file;
            audioCount++;
            try {
                byte[] bytes = readAll(target);
                upload("audio", "aac", bytes, ts, durationMs);
            } catch (IOException e) {
                Log.w(TAG, "Could not read clip", e);
            }
        });
    }

    @NonNull
    public File tempFile(@NonNull String name) {
        return new File(dir, name);
    }

    /** Stops the background writer; anything saved afterwards (final clips) is written inline. */
    public void close() {
        io.shutdown();
    }

    private void run(Runnable task) {
        try {
            io.execute(task);
        } catch (RejectedExecutionException e) {
            task.run(); // closing: the last clip/photo must still be saved
        }
    }

    private void upload(String kind, String field, byte[] bytes, long ts, long durationMs) {
        DatabaseReference ref = remote;
        if (ref == null) return;
        if (bytes.length > MAX_UPLOAD_BYTES) {
            Log.w(TAG, "Skipping upload of large " + kind + " (" + bytes.length + " bytes)");
            return;
        }
        Map<String, Object> item = new HashMap<>();
        item.put("ts", ts);
        item.put(field, Base64.encodeToString(bytes, Base64.NO_WRAP));
        if (durationMs > 0) item.put("durMs", durationMs);
        ref.child(kind).push().setValue(item);
    }

    private static void write(File f, byte[] data) {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(data);
        } catch (IOException e) {
            Log.e(TAG, "Failed to save " + f.getName(), e);
        }
    }

    private static byte[] readAll(File f) throws IOException {
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    // ---------- Reading back (Evidence screen) ----------

    /** One SOS session's saved files, newest session first. */
    public static final class Session {
        public final String id;
        public final long startedAt;
        public final List<File> photos;
        public final List<File> audio;

        Session(String id, long startedAt, List<File> photos, List<File> audio) {
            this.id = id;
            this.startedAt = startedAt;
            this.photos = photos;
            this.audio = audio;
        }
    }

    @NonNull
    public static List<Session> listSessions(@NonNull Context context) {
        List<Session> sessions = new ArrayList<>();
        File root = new File(context.getFilesDir(), ROOT);
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs != null) {
            for (File d : dirs) {
                Session s = readSession(d.getName(), d, parseStart(d.getName(), d.lastModified()));
                if (s != null) sessions.add(s);
            }
        }
        // Files saved by older versions of the app (v5) live in external app storage.
        File ext = context.getExternalFilesDir(null);
        if (ext != null) {
            List<File> oldPhotos = listFiles(new File(ext, "SOSPhotos"), ".jpg");
            List<File> oldAudio = listFiles(new File(ext, "SOSAudio"), ".3gp");
            if (!oldPhotos.isEmpty() || !oldAudio.isEmpty()) {
                long newest = 0;
                for (File f : oldPhotos) newest = Math.max(newest, f.lastModified());
                for (File f : oldAudio) newest = Math.max(newest, f.lastModified());
                sessions.add(new Session("legacy", newest, oldPhotos, oldAudio));
            }
        }
        Collections.sort(sessions, (a, b) -> Long.compare(b.startedAt, a.startedAt));
        return sessions;
    }

    @Nullable
    private static Session readSession(String id, File d, long startedAt) {
        List<File> photos = listFiles(d, ".jpg");
        List<File> audio = listFiles(d, ".aac");
        if (photos.isEmpty() && audio.isEmpty()) return null;
        return new Session(id, startedAt, photos, audio);
    }

    private static long parseStart(String sessionId, long fallback) {
        try {
            return Long.parseLong(sessionId.substring(0, Math.min(13, sessionId.length())));
        } catch (Exception e) {
            return fallback;
        }
    }

    @NonNull
    private static List<File> listFiles(File d, String suffix) {
        File[] files = d.listFiles((f, name) -> name.endsWith(suffix));
        if (files == null) return new ArrayList<>();
        List<File> list = new ArrayList<>(Arrays.asList(files));
        Collections.sort(list, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return list;
    }

    public static boolean deleteSession(@NonNull Context context, @NonNull Session session) {
        boolean ok = true;
        for (File f : session.photos) ok &= f.delete();
        for (File f : session.audio) ok &= f.delete();
        if (!"legacy".equals(session.id)) {
            File d = new File(new File(context.getFilesDir(), ROOT), session.id);
            //noinspection ResultOfMethodCallIgnored
            d.delete();
        }
        return ok;
    }
}
