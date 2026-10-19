package com.example.womensafety.live;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.womensafety.evidence.EvidenceStore;

import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Takes a still photo from the live camera feed every few seconds and saves it as evidence.
 * Uses the same camera stream as the live video, so there is no camera conflict.
 */
final class FrameSnapshotter implements VideoSink {

    private static final String TAG = "Snapshotter";
    private static final int MAX_EDGE = 720;
    private static final int JPEG_QUALITY = 62;

    private final EvidenceStore evidence;
    private final long intervalMs;
    private final ExecutorService encoder = Executors.newSingleThreadExecutor();
    private long lastShotAt;
    private volatile boolean busy;
    private volatile boolean closed;

    FrameSnapshotter(@NonNull EvidenceStore evidence, long intervalMs) {
        this.evidence = evidence;
        this.intervalMs = intervalMs;
    }

    @Override
    public void onFrame(VideoFrame frame) {
        if (closed || busy) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastShotAt < intervalMs) return;
        lastShotAt = now;

        // Copy the frame to CPU memory now (cheap); compress on a worker thread.
        VideoFrame.I420Buffer i420 = frame.getBuffer().toI420();
        if (i420 == null) return;
        final int w = i420.getWidth();
        final int h = i420.getHeight();
        final int rotation = frame.getRotation();
        final byte[] nv21;
        try {
            nv21 = toNv21(i420, w, h);
        } finally {
            i420.release();
        }
        busy = true;
        encoder.execute(() -> {
            try {
                byte[] jpeg = compress(nv21, w, h, rotation);
                if (jpeg != null) evidence.savePhoto(jpeg);
            } catch (Exception e) {
                Log.w(TAG, "Snapshot failed", e);
            } finally {
                busy = false;
            }
        });
    }

    void close() {
        closed = true;
        encoder.shutdown();
    }

    /** I420 (three planes) to NV21 (Y plane + interleaved V/U), honouring row strides. */
    private static byte[] toNv21(VideoFrame.I420Buffer b, int w, int h) {
        byte[] out = new byte[w * h + 2 * ((w + 1) / 2) * ((h + 1) / 2)];
        ByteBuffer y = b.getDataY();
        ByteBuffer u = b.getDataU();
        ByteBuffer v = b.getDataV();
        int sy = b.getStrideY();
        int su = b.getStrideU();
        int sv = b.getStrideV();
        int pos = 0;
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) out[pos++] = y.get(row * sy + col);
        }
        int cw = (w + 1) / 2;
        int ch = (h + 1) / 2;
        for (int row = 0; row < ch; row++) {
            for (int col = 0; col < cw; col++) {
                out[pos++] = v.get(row * sv + col);
                out[pos++] = u.get(row * su + col);
            }
        }
        return out;
    }

    private static byte[] compress(byte[] nv21, int w, int h, int rotation) {
        YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, w, h, null);
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        if (!yuv.compressToJpeg(new Rect(0, 0, w, h), 90, raw)) return null;
        byte[] rawBytes = raw.toByteArray();
        Bitmap bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.length);
        if (bmp == null) return null;

        float scale = Math.min(1f, (float) MAX_EDGE / Math.max(w, h));
        Matrix m = new Matrix();
        m.postScale(scale, scale);
        if (rotation != 0) m.postRotate(rotation);
        Bitmap out = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        out.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, jpeg);
        if (out != bmp) out.recycle();
        bmp.recycle();
        return jpeg.toByteArray();
    }
}
