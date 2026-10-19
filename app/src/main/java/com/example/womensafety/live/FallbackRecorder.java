package com.example.womensafety.live;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.womensafety.evidence.EvidenceStore;

import java.io.File;

/**
 * Plain microphone recorder used only if WebRTC's microphone capture isn't producing audio
 * (e.g. no network interface at all). Records consecutive 20-second AAC clips into evidence.
 */
final class FallbackRecorder implements MediaRecorder.OnInfoListener {

    private static final String TAG = "FallbackRecorder";
    private static final int CLIP_MS = 20_000;

    private final Context context;
    private final EvidenceStore evidence;
    private MediaRecorder recorder;
    private File current;
    private long clipStartedAt;
    private int index;

    FallbackRecorder(@NonNull Context context, @NonNull EvidenceStore evidence) {
        this.context = context.getApplicationContext();
        this.evidence = evidence;
    }

    boolean isRunning() {
        return recorder != null;
    }

    void start() {
        if (recorder != null) return;
        try {
            current = evidence.tempFile("fallback_" + (index++) + ".aac.part");
            MediaRecorder r = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(context) : new MediaRecorder();
            r.setAudioSource(MediaRecorder.AudioSource.MIC);
            r.setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS);
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            r.setAudioEncodingBitRate(32_000);
            r.setAudioSamplingRate(22_050);
            r.setMaxDuration(CLIP_MS);
            r.setOnInfoListener(this);
            r.setOutputFile(current.getAbsolutePath());
            r.prepare();
            r.start();
            recorder = r;
            clipStartedAt = SystemClock.elapsedRealtime();
            Log.i(TAG, "Fallback recording started");
        } catch (Exception e) {
            Log.w(TAG, "Could not start fallback recording", e);
            release();
        }
    }

    void stop() {
        if (recorder == null) return;
        long dur = SystemClock.elapsedRealtime() - clipStartedAt;
        try {
            recorder.stop();
            evidence.importAudioFile(current, dur);
        } catch (Exception e) {
            // stop() throws if nothing was recorded yet; just drop the empty clip.
            //noinspection ResultOfMethodCallIgnored
            current.delete();
        }
        release();
    }

    private void release() {
        if (recorder != null) {
            try {
                recorder.release();
            } catch (Exception ignored) {
            }
        }
        recorder = null;
    }

    @Override
    public void onInfo(MediaRecorder mr, int what, int extra) {
        if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
            stop();
            start();
        }
    }
}
