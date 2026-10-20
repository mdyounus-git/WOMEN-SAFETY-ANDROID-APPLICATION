package com.example.womensafety.live;

import android.media.AudioFormat;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import androidx.annotation.NonNull;

import org.webrtc.AudioTrackSink;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSink;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Records a live WebRTC stream (video frames + PCM audio) into playable MP4 files
 * (H.264 video, AAC audio), in 30-second segments. Short segments mean that if the phone is
 * switched off or taken mid-SOS, everything up to the last half-minute is already saved.
 *
 * <p>Used on both phones: the SOS phone records its own camera and microphone, and the
 * guardian's phone records the stream it receives. Recording problems are logged and never
 * interrupt the live stream.
 */
public final class Mp4Recorder implements VideoSink {

    private static final String TAG = "Mp4Recorder";
    private static final long SEGMENT_US = 30_000_000L;
    private static final long FRAME_INTERVAL_NS = 1_000_000_000L / 15; // 15 fps
    private static final int VIDEO_BITRATE = 900_000;
    private static final int AUDIO_BITRATE = 48_000;
    private static final long MIN_FREE_BYTES = 300L * 1024 * 1024;
    private static final long AUDIO_ACTIVE_NS = 2_000_000_000L;

    /** Where finished segments go. */
    public interface Output {
        /** A new, not yet existing file to write the next segment to. */
        @NonNull
        File newSegmentFile();

        /** Called (on the recorder thread) when a segment has been finalised. */
        void onSegmentSaved(@NonNull File file, long durationMs);
    }

    private final Output output;
    private final HandlerThread thread;
    private final Handler handler;
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

    private volatile boolean running;
    private volatile long lastFrameNs;
    private volatile long lastAudioNs;
    private volatile int lastAudioRate;
    private volatile int lastAudioChannels;
    private volatile boolean busy;

    // ---- recorder thread only ----
    private Segment seg;
    private int segments;

    public Mp4Recorder(@NonNull Output output, @NonNull String name) {
        this.output = output;
        thread = new HandlerThread("mp4-" + name);
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    public void start() {
        running = true;
    }

    /** Finalises the current segment and stops. Safe to call more than once. */
    public void stop() {
        if (!running && !thread.isAlive()) return;
        running = false;
        handler.post(() -> {
            finishSegment();
            thread.quitSafely();
        });
    }

    public boolean isRecording() {
        return running;
    }

    // ------------------------------------------------------------------ input

    @Override
    public void onFrame(VideoFrame frame) {
        if (!running || busy) return;
        long now = System.nanoTime();
        if (now - lastFrameNs < FRAME_INTERVAL_NS) return;
        lastFrameNs = now;
        VideoFrame.I420Buffer i420;
        try {
            i420 = frame.getBuffer().toI420();
        } catch (Throwable t) {
            return;
        }
        if (i420 == null) return;
        final RawFrame raw;
        try {
            raw = RawFrame.copyOf(i420, frame.getRotation(), now);
        } finally {
            i420.release();
        }
        busy = true;
        handler.post(() -> {
            try {
                encodeVideo(raw);
            } catch (Throwable t) {
                Log.w(TAG, "Video encode failed; starting a new segment", t);
                abortSegment();
            } finally {
                busy = false;
            }
        });
    }

    /** Feed 16-bit PCM audio from WebRTC's microphone callback (the SOS phone). */
    public void onAudio(@NonNull JavaAudioDeviceModule.AudioSamples samples) {
        if (!running || samples.getAudioFormat() != AudioFormat.ENCODING_PCM_16BIT) return;
        onPcm(samples.getData(), samples.getSampleRate(), samples.getChannelCount());
    }

    /**
     * Audio sink for a received WebRTC audio track (the guardian's phone). Gets the decoded audio
     * before the speaker volume is applied, so muting the speaker doesn't mute the recording.
     */
    @NonNull
    public AudioTrackSink audioSink() {
        return (data, bitsPerSample, sampleRate, channels, frames, captureTimeMs) -> {
            if (!running || bitsPerSample != 16 || data == null) return;
            int bytes = frames * channels * 2;
            ByteBuffer src = data.duplicate();
            if (src.remaining() < bytes) bytes = src.remaining();
            byte[] pcm = new byte[bytes];
            src.get(pcm);
            onPcm(pcm, sampleRate, channels);
        };
    }

    private void onPcm(@NonNull byte[] pcmData, int sampleRate, int channelCount) {
        if (!running || sampleRate <= 0 || channelCount <= 0) return;
        final long now = System.nanoTime();
        final byte[] pcm = pcmData;
        final int rate = sampleRate;
        final int channels = channelCount;
        lastAudioRate = rate;
        lastAudioChannels = channels;
        lastAudioNs = now;
        handler.post(() -> {
            try {
                encodeAudio(pcm, rate, channels, now);
            } catch (Throwable t) {
                Log.w(TAG, "Audio encode failed", t);
            }
        });
    }

    // ------------------------------------------------------------------ segments

    /** One MP4 file being written. */
    private static final class Segment {
        File file;
        File part;
        MediaMuxer muxer;
        MediaCodec video;
        MediaCodec audio;
        int width;
        int height;
        int rotation;
        int sampleRate;
        int channels;
        long startNs;
        long lastVideoUs = -1;
        long lastAudioUs = -1;
        int videoTrack = -1;
        int audioTrack = -1;
        boolean muxerStarted;
        final List<Pending> pending = new ArrayList<>();
    }

    /** Encoded output held until the muxer can start (it needs every track's format first). */
    private static final class Pending {
        final boolean isVideo;
        final byte[] data;
        final long ptsUs;
        final int flags;

        Pending(boolean isVideo, byte[] data, long ptsUs, int flags) {
            this.isVideo = isVideo;
            this.data = data;
            this.ptsUs = ptsUs;
            this.flags = flags;
        }
    }

    private void encodeVideo(RawFrame f) throws Exception {
        if (!running) return;
        int w = f.width & ~15;
        int h = f.height & ~15;
        if (w < 64 || h < 64) return;
        if (seg != null && (seg.width != w || seg.height != h || seg.rotation != f.rotation
                || f.timeNs - seg.startNs >= SEGMENT_US * 1000)) {
            finishSegment();
        }
        if (seg == null && !startSegment(w, h, f.rotation, f.timeNs)) return;

        int idx = seg.video.dequeueInputBuffer(10_000);
        if (idx >= 0) {
            Image img = seg.video.getInputImage(idx);
            if (img != null) {
                writeImage(img, f, w, h);
                long pts = monotonic((f.timeNs - seg.startNs) / 1000, seg.lastVideoUs);
                seg.lastVideoUs = pts;
                seg.video.queueInputBuffer(idx, 0, w * h * 3 / 2, pts, 0);
            } else {
                seg.video.queueInputBuffer(idx, 0, 0, 0, 0);
            }
        }
        drain(seg.video, true);
    }

    private void encodeAudio(byte[] pcm, int rate, int channels, long nowNs) throws Exception {
        if (seg == null || seg.audio == null || !running) return;
        if (rate != seg.sampleRate || channels != seg.channels) return;
        int offset = 0;
        int guard = 0;
        long chunkNs = (long) pcm.length / (2L * channels) * 1_000_000_000L / rate;
        long baseUs = (nowNs - chunkNs - seg.startNs) / 1000;
        if (baseUs < 0) return; // audio from before this segment
        while (offset < pcm.length && guard++ < 32) {
            int idx = seg.audio.dequeueInputBuffer(5_000);
            if (idx < 0) {
                drain(seg.audio, false);
                continue;
            }
            ByteBuffer in = seg.audio.getInputBuffer(idx);
            if (in == null) continue;
            in.clear();
            int n = Math.min(in.remaining(), pcm.length - offset);
            in.put(pcm, offset, n);
            long offUs = (long) offset / (2L * channels) * 1_000_000L / rate;
            long pts = monotonic(baseUs + offUs, seg.lastAudioUs);
            seg.lastAudioUs = pts;
            seg.audio.queueInputBuffer(idx, 0, n, pts, 0);
            offset += n;
        }
        drain(seg.audio, false);
    }

    private boolean startSegment(int w, int h, int rotation, long startNs) {
        File probe = output.newSegmentFile();
        File dir = probe.getParentFile();
        if (dir != null && dir.getUsableSpace() < MIN_FREE_BYTES) {
            Log.w(TAG, "Not enough free space for video; skipping");
            return false;
        }
        Segment s = new Segment();
        s.file = probe;
        s.part = new File(probe.getPath() + ".part");
        s.width = w;
        s.height = h;
        s.rotation = (rotation % 360 + 360) % 360;
        s.startNs = startNs;
        try {
            MediaFormat vf = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h);
            vf.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            vf.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE);
            vf.setInteger(MediaFormat.KEY_FRAME_RATE, 15);
            vf.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2);
            s.video = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            s.video.configure(vf, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            s.video.start();

            // Include audio only if it is actually flowing, in the format it arrives in.
            if (System.nanoTime() - lastAudioNs < AUDIO_ACTIVE_NS && lastAudioRate > 0) {
                s.sampleRate = lastAudioRate;
                s.channels = lastAudioChannels;
                startAudioCodec(s);
            }

            s.muxer = new MediaMuxer(s.part.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            if (s.rotation % 90 == 0) s.muxer.setOrientationHint(s.rotation);
            seg = s;
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Could not start recording segment", e);
            release(s);
            return false;
        }
    }

    private void startAudioCodec(Segment s) {
        try {
            MediaFormat af = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC,
                    s.sampleRate, s.channels);
            af.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            af.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE);
            af.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024);
            s.audio = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            s.audio.configure(af, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            s.audio.start();
        } catch (Exception e) {
            Log.w(TAG, "No audio in this segment", e);
            if (s.audio != null) s.audio.release();
            s.audio = null;
        }
    }

    /** Moves encoded output into the file (or holds it until the muxer has started). */
    private void drain(MediaCodec codec, boolean isVideo) {
        Segment s = seg;
        if (s == null) return;
        while (true) {
            int out = codec.dequeueOutputBuffer(info, 0);
            if (out == MediaCodec.INFO_TRY_AGAIN_LATER) return;
            if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (isVideo && s.videoTrack < 0) s.videoTrack = s.muxer.addTrack(codec.getOutputFormat());
                if (!isVideo && s.audioTrack < 0) s.audioTrack = s.muxer.addTrack(codec.getOutputFormat());
                maybeStartMuxer(s);
                continue;
            }
            if (out < 0) continue;
            ByteBuffer buf = codec.getOutputBuffer(out);
            boolean config = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
            if (buf != null && info.size > 0 && !config) {
                if (s.muxerStarted) {
                    buf.position(info.offset);
                    buf.limit(info.offset + info.size);
                    write(s, isVideo, buf, info.presentationTimeUs, info.flags);
                } else if (s.pending.size() < 600) {
                    byte[] copy = new byte[info.size];
                    buf.position(info.offset);
                    buf.get(copy);
                    s.pending.add(new Pending(isVideo, copy, info.presentationTimeUs, info.flags));
                }
            }
            codec.releaseOutputBuffer(out, false);
        }
    }

    private void maybeStartMuxer(Segment s) {
        if (s.muxerStarted || s.videoTrack < 0) return;
        if (s.audio != null && s.audioTrack < 0) return;
        s.muxer.start();
        s.muxerStarted = true;
        for (Pending p : s.pending) {
            write(s, p.isVideo, ByteBuffer.wrap(p.data), p.ptsUs, p.flags);
        }
        s.pending.clear();
    }

    private final MediaCodec.BufferInfo writeInfo = new MediaCodec.BufferInfo();

    private void write(Segment s, boolean isVideo, ByteBuffer data, long ptsUs, int flags) {
        int track = isVideo ? s.videoTrack : s.audioTrack;
        if (track < 0) return;
        writeInfo.set(data.position(), data.remaining(), ptsUs, flags & ~MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        s.muxer.writeSampleData(track, data, writeInfo);
    }

    private void finishSegment() {
        Segment s = seg;
        seg = null;
        if (s == null) return;
        long durMs = Math.max(s.lastVideoUs, s.lastAudioUs) / 1000;
        try {
            if (s.video != null) drainLast(s, s.video, true);
            if (s.audio != null) drainLast(s, s.audio, false);
        } catch (Throwable t) {
            Log.w(TAG, "Error draining encoders", t);
        }
        boolean wrote = s.muxerStarted;
        release(s);
        if (wrote && s.part.renameTo(s.file)) {
            segments++;
            output.onSegmentSaved(s.file, durMs);
        } else {
            //noinspection ResultOfMethodCallIgnored
            s.part.delete();
        }
    }

    private void drainLast(Segment s, MediaCodec codec, boolean isVideo) {
        Segment saved = seg;
        seg = s;
        try {
            drain(codec, isVideo);
        } finally {
            seg = saved;
        }
    }

    /** Drops a broken segment (keeps whatever it had already finalised). */
    private void abortSegment() {
        Segment s = seg;
        seg = null;
        if (s == null) return;
        release(s);
        //noinspection ResultOfMethodCallIgnored
        s.part.delete();
    }

    private static void release(Segment s) {
        if (s.video != null) {
            try {
                s.video.stop();
            } catch (Exception ignored) {
            }
            s.video.release();
        }
        if (s.audio != null) {
            try {
                s.audio.stop();
            } catch (Exception ignored) {
            }
            s.audio.release();
        }
        if (s.muxer != null) {
            try {
                if (s.muxerStarted) s.muxer.stop();
            } catch (Exception e) {
                Log.w(TAG, "Muxer stop failed", e);
                s.muxerStarted = false;
            }
            s.muxer.release();
        }
    }

    private static long monotonic(long pts, long last) {
        return pts <= last ? last + 1 : pts;
    }

    // ------------------------------------------------------------------ frames

    /** A CPU copy of an I420 frame, so the camera buffer can be released immediately. */
    private static final class RawFrame {
        final int width;
        final int height;
        final int rotation;
        final long timeNs;
        final byte[] y;
        final byte[] u;
        final byte[] v;

        private RawFrame(int width, int height, int rotation, long timeNs, byte[] y, byte[] u, byte[] v) {
            this.width = width;
            this.height = height;
            this.rotation = rotation;
            this.timeNs = timeNs;
            this.y = y;
            this.u = u;
            this.v = v;
        }

        static RawFrame copyOf(VideoFrame.I420Buffer b, int rotation, long timeNs) {
            int w = b.getWidth();
            int h = b.getHeight();
            int cw = (w + 1) / 2;
            int ch = (h + 1) / 2;
            return new RawFrame(w, h, rotation, timeNs,
                    copyPlane(b.getDataY(), b.getStrideY(), w, h),
                    copyPlane(b.getDataU(), b.getStrideU(), cw, ch),
                    copyPlane(b.getDataV(), b.getStrideV(), cw, ch));
        }

        private static byte[] copyPlane(ByteBuffer src, int stride, int w, int h) {
            byte[] out = new byte[w * h];
            ByteBuffer s = src.duplicate();
            for (int row = 0; row < h; row++) {
                s.position(row * stride);
                s.get(out, row * w, w);
            }
            return out;
        }
    }

    /** Copies a frame (cropped to w x h) into the encoder's input image, any YUV layout. */
    private static void writeImage(Image img, RawFrame f, int w, int h) {
        Image.Plane[] planes = img.getPlanes();
        writePlane(planes[0], f.y, f.width, w, h);
        int cw = w / 2;
        int ch = h / 2;
        int srcCw = (f.width + 1) / 2;
        writePlane(planes[1], f.u, srcCw, cw, ch);
        writePlane(planes[2], f.v, srcCw, cw, ch);
    }

    private static void writePlane(Image.Plane plane, byte[] src, int srcStride, int w, int h) {
        ByteBuffer dst = plane.getBuffer();
        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride();
        if (pixelStride == 1) {
            for (int row = 0; row < h; row++) {
                dst.position(row * rowStride);
                dst.put(src, row * srcStride, w);
            }
        } else {
            int limit = dst.limit();
            for (int row = 0; row < h; row++) {
                int base = row * rowStride;
                int srcBase = row * srcStride;
                for (int col = 0; col < w; col++) {
                    int pos = base + col * pixelStride;
                    if (pos < limit) dst.put(pos, src[srcBase + col]);
                }
            }
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "Mp4Recorder(segments=" + segments + ")";
    }
}
