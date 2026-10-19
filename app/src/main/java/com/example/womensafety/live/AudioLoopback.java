package com.example.womensafety.live;

import android.util.Log;

import androidx.annotation.NonNull;

import org.webrtc.AudioTrack;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpTransceiver;
import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.Executor;

/**
 * WebRTC only opens the microphone while an audio track is being sent somewhere. During SOS we
 * want audio evidence even before (or without) any guardian watching, so this connects the
 * audio track to a second, local, muted peer on the same phone. No data leaves the device.
 */
final class AudioLoopback {

    private static final String TAG = "AudioLoopback";

    private final PeerConnectionFactory factory;
    private final AudioTrack track;
    private final Executor executor;
    private PeerConnection sender;
    private PeerConnection receiver;
    private boolean closed;

    AudioLoopback(@NonNull PeerConnectionFactory factory, @NonNull AudioTrack track,
                  @NonNull Executor executor) {
        this.factory = factory;
        this.track = track;
        this.executor = executor;
    }

    /** Must be called on {@code executor}. */
    void start() {
        // No ICE servers needed: host candidates (including loopback) connect on-device.
        PeerConnection.RTCConfiguration cfg = new PeerConnection.RTCConfiguration(new ArrayList<>());
        cfg.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        sender = factory.createPeerConnection(cfg, new PeerObserverAdapter() {
            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState state) {
                if (state == PeerConnection.IceGatheringState.COMPLETE) {
                    executor.execute(AudioLoopback.this::offerGathered);
                }
            }
        });
        receiver = factory.createPeerConnection(cfg, new PeerObserverAdapter() {
            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState state) {
                if (state == PeerConnection.IceGatheringState.COMPLETE) {
                    executor.execute(AudioLoopback.this::answerGathered);
                }
            }
        });
        if (sender == null || receiver == null) {
            Log.w(TAG, "Could not create loopback peers");
            return;
        }
        sender.setAudioPlayout(false);
        receiver.setAudioPlayout(false);
        sender.addTransceiver(track, new RtpTransceiver.RtpTransceiverInit(
                RtpTransceiver.RtpTransceiverDirection.SEND_ONLY, Collections.singletonList("loop")));

        sender.createOffer(new SdpAdapter("loop-offer") {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                executor.execute(() -> {
                    if (!closed) sender.setLocalDescription(new SdpAdapter("loop-offer-set"), sdp);
                });
            }
        }, new MediaConstraints());
    }

    private void offerGathered() {
        if (closed) return;
        SessionDescription offer = sender.getLocalDescription();
        if (offer == null) return;
        receiver.setRemoteDescription(new SdpAdapter("loop-remote-offer") {
            @Override
            public void onSetSuccess() {
                executor.execute(AudioLoopback.this::createAnswer);
            }
        }, offer);
    }

    private void createAnswer() {
        if (closed) return;
        receiver.createAnswer(new SdpAdapter("loop-answer") {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                executor.execute(() -> {
                    if (!closed) receiver.setLocalDescription(new SdpAdapter("loop-answer-set"), sdp);
                });
            }
        }, new MediaConstraints());
    }

    private void answerGathered() {
        if (closed) return;
        SessionDescription answer = receiver.getLocalDescription();
        if (answer != null) sender.setRemoteDescription(new SdpAdapter("loop-remote-answer"), answer);
    }

    /** Must be called on {@code executor}. */
    void stop() {
        closed = true;
        if (sender != null) sender.dispose();
        if (receiver != null) receiver.dispose();
        sender = null;
        receiver = null;
    }
}
