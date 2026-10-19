package com.example.womensafety.live;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.womensafety.evidence.EvidenceStore;

import org.webrtc.audio.JavaAudioDeviceModule;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * Turns the microphone samples WebRTC is already capturing into small AAC clips (playable
 * .aac files) that are saved as evidence every {@link #CLIP_MS}. Sharing WebRTC's capture means
 * the mic is opened once, for both the live stream and the recording.
 */
final class AacClipEncoder implements JavaAudioDeviceModule.SamplesReadyCallback {

    private static final String TAG = "AacClip";
    static final long CLIP_MS = 15_000L;
    private static final int BITRATE = 32_000;
    private static final int[] SAMPLE_RATES = {96000, 88200, 64000, 48000, 44100, 32000, 24000,
            22050, 16000, 12000, 11025, 8000, 7350};

    private final EvidenceStore evidence;
    private final HandlerThread thread = new HandlerThread("aac-encoder");
    private final Handler handler;
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

    private MediaCodec codec;
    private int sampleRate;
    private int channels;
    private long samplesFed;
    private long clipStartSample;
    private ByteArrayOutputStream clip = new ByteArrayOutputStream();
    private volatile long lastSamplesAt;
    private volatile boolean closed;

    AacClipEncoder(@NonNull EvidenceStore evidence) {
        this.evidence = evidence;
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    /** Elapsed-realtime of the last microphone samples seen (0 = never). */
    long lastSamplesAt() {
        return lastSamplesAt;
    }

    @Override
    public void onWebRtcAudioRecordSamplesReady(JavaAudioDeviceModule.AudioSamples samples) {
        if (closed) return;
        lastSamplesAt = SystemClock.elapsedRealtime();
        if (samples.getAudioFormat() != AudioFormat.ENCODING_PCM_16BIT) return;
        final byte[] pcm = samples.getData();
        final int rate = samples.getSampleRate();
        final int ch = samples.getChannelCount();
        handler.post(() -> encode(pcm, rate, ch));
    }

    private void encode(byte[] pcm, int rate, int ch) {
        if (closed) return;
        try {
            if (codec == null || rate != sampleRate || ch != channels) {
                flushClip();
                releaseCodec();
                startCodec(rate, ch);
            }
            int offset = 0;
            int guard = 0;
            while (offset < pcm.length && guard++ < 64) {
                int inIdx = codec.dequeueInputBuffer(10_000);
                if (inIdx < 0) {
                    drain();
                    continue;
                }
                ByteBuffer in = codec.getInputBuffer(inIdx);
                if (in == null) continue;
                in.clear();
                int n = Math.min(in.remaining(), pcm.length - offset);
                in.put(pcm, offset, n);
                long ptsUs = samplesFed * 1_000_000L / sampleRate;
                codec.queueInputBuffer(inIdx, 0, n, ptsUs, 0);
                samplesFed += n / (2L * channels);
                offset += n;
                drain();
            }
            if ((samplesFed - clipStartSample) * 1000L / sampleRate >= CLIP_MS) flushClip();
        } catch (Exception e) {
            Log.w(TAG, "Encoder error, restarting", e);
            releaseCodec();
        }
    }

    private void startCodec(int rate, int ch) throws Exception {
        sampleRate = rate;
        channels = ch;
        MediaFormat f = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, ch);
        f.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        f.setInteger(MediaFormat.KEY_BIT_RATE, BITRATE);
        f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16 * 1024);
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        codec.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        codec.start();
        samplesFed = 0;
        clipStartSample = 0;
    }

    private void drain() {
        while (true) {
            int outIdx = codec.dequeueOutputBuffer(info, 0);
            if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) return;
            if (outIdx < 0) continue; // format/buffers changed
            ByteBuffer out = codec.getOutputBuffer(outIdx);
            boolean config = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0;
            if (out != null && info.size > 0 && !config) {
                byte[] frame = new byte[info.size];
                out.position(info.offset);
                out.get(frame, 0, info.size);
                clip.write(adtsHeader(info.size + 7), 0, 7);
                clip.write(frame, 0, frame.length);
            }
            codec.releaseOutputBuffer(outIdx, false);
        }
    }

    private void flushClip() {
        if (clip.size() == 0 || sampleRate == 0) return;
        long durMs = (samplesFed - clipStartSample) * 1000L / sampleRate;
        evidence.saveAudio(clip.toByteArray(), durMs);
        clip = new ByteArrayOutputStream();
        clipStartSample = samplesFed;
    }

    private void releaseCodec() {
        if (codec == null) return;
        try {
            codec.stop();
        } catch (Exception ignored) {
        }
        try {
            codec.release();
        } catch (Exception ignored) {
        }
        codec = null;
    }

    /** Saves what's left and stops. */
    void close() {
        handler.post(() -> {
            try {
                if (codec != null) drain();
            } catch (Exception ignored) {
            }
            flushClip();
            releaseCodec();
            closed = true;
            thread.quitSafely();
        });
    }

    private byte[] adtsHeader(int packetLen) {
        int profile = 2; // AAC LC
        int freqIdx = 4; // 44.1 kHz default
        for (int i = 0; i < SAMPLE_RATES.length; i++) {
            if (SAMPLE_RATES[i] == sampleRate) {
                freqIdx = i;
                break;
            }
        }
        int chanCfg = channels;
        byte[] h = new byte[7];
        h[0] = (byte) 0xFF;
        h[1] = (byte) 0xF1;
        h[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        h[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        h[4] = (byte) ((packetLen & 0x7FF) >> 3);
        h[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        h[6] = (byte) 0xFC;
        return h;
    }
}
