package com.example.womensafety.live;

import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.womensafety.core.Backend;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.ValueEventListener;

import org.webrtc.AudioTrack;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpTransceiver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Runs on the guardian's phone: asks the ward's phone for its live camera + microphone and
 * plays them. Only works while the ward's SOS is active (the ward's phone only answers then).
 */
public final class Viewer {

    private static final String TAG = "Viewer";
    private static final long CONNECT_TIMEOUT_MS = 20_000L;

    public enum State { CONNECTING, LIVE, RECONNECTING, FAILED, CLOSED }

    public interface Listener {
        /** Main thread. */
        void onStateChanged(@NonNull State state);
    }

    private final Context app;
    private final String wardUid;
    private final String myUid;
    private final Listener listener;
    private final ExecutorService rtc = Executors.newSingleThreadExecutor();
    private final Executor rtcSafe = r -> {
        try {
            rtc.execute(r);
        } catch (RejectedExecutionException ignored) {
        }
    };
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable connectTimeout = () -> setState(State.FAILED);

    private EglBase egl;
    private PeerConnectionFactory factory;
    private JavaAudioDeviceModule adm;
    private PeerConnection pc;
    @Nullable
    private SurfaceViewRenderer renderer;
    private VideoTrack remoteVideo;
    private AudioTrack remoteAudio;
    private String nonce;
    private boolean offerApplied;
    private boolean closed;
    private boolean muted;
    private final Set<String> appliedIce = new HashSet<>();
    private final List<IceCandidate> pendingIce = new ArrayList<>();

    private DatabaseReference node;
    private ValueEventListener nodeListener;
    private AudioManager audioManager;
    private int previousAudioMode;
    private boolean previousSpeaker;

    public Viewer(@NonNull Context context, @NonNull String wardUid, @NonNull String myUid,
                  @NonNull Listener listener) {
        this.app = context.getApplicationContext();
        this.wardUid = wardUid;
        this.myUid = myUid;
        this.listener = listener;
    }

    /** EGL context needed to initialise the {@link SurfaceViewRenderer}. Main thread. */
    @NonNull
    public EglBase.Context eglContext() {
        if (egl == null) egl = EglBase.create();
        return egl.getEglBaseContext();
    }

    /** Main thread. The renderer must already be initialised with {@link #eglContext()}. */
    public void start(@NonNull SurfaceViewRenderer surface) {
        this.renderer = surface;
        eglContext();
        routeAudioToSpeaker(true);
        node = Backend.calls(wardUid).child(myUid);
        rtcSafe.execute(this::createFactory);
        requestStream();
    }

    /** Asks the ward's phone for a fresh connection (also used to reconnect). Main thread. */
    public void requestStream() {
        if (closed) return;
        final String newNonce = UUID.randomUUID().toString();
        setState(State.CONNECTING);
        main.removeCallbacks(connectTimeout);
        main.postDelayed(connectTimeout, CONNECT_TIMEOUT_MS);
        if (node != null && nodeListener != null) node.removeEventListener(nodeListener);
        rtcSafe.execute(() -> resetPeer(newNonce));

        Map<String, Object> req = new HashMap<>();
        req.put("req", newNonce);
        req.put("ts", ServerValue.TIMESTAMP);
        node.setValue(req).addOnSuccessListener(v -> {
            nodeListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    rtcSafe.execute(() -> onNode(snapshot));
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.w(TAG, "Signalling cancelled: " + error.getMessage());
                    setState(State.FAILED);
                }
            };
            node.addValueEventListener(nodeListener);
        }).addOnFailureListener(e -> {
            Log.w(TAG, "Could not request stream", e);
            setState(State.FAILED);
        });
    }

    public void switchCamera() {
        if (node != null) node.child("cmd").child("switchCam").setValue(ServerValue.increment(1));
    }

    public void setMuted(boolean mute) {
        muted = mute;
        rtcSafe.execute(() -> {
            if (remoteAudio != null) remoteAudio.setVolume(mute ? 0 : 1);
        });
    }

    public boolean isMuted() {
        return muted;
    }

    /** Main thread. */
    public void stop() {
        if (closed) return;
        closed = true;
        main.removeCallbacks(connectTimeout);
        if (node != null && nodeListener != null) node.removeEventListener(nodeListener);
        if (node != null) node.removeValue();
        routeAudioToSpeaker(false);
        rtcSafe.execute(() -> {
            disposePeer();
            if (factory != null) factory.dispose();
            if (adm != null) adm.release();
            factory = null;
            main.post(() -> {
                if (egl != null) egl.release();
                egl = null;
            });
        });
        rtc.shutdown();
        setState(State.CLOSED);
    }

    // ------------------------------------------------------------------

    private void createFactory() {
        if (factory != null || closed) return;
        RtcEnv.ensureInitialized(app);
        adm = JavaAudioDeviceModule.builder(app).createAudioDeviceModule();
        factory = PeerConnectionFactory.builder()
                .setAudioDeviceModule(adm)
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(egl.getEglBaseContext(), true, true))
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(egl.getEglBaseContext()))
                .createPeerConnectionFactory();
    }

    private void resetPeer(String newNonce) {
        disposePeer();
        nonce = newNonce;
        offerApplied = false;
        appliedIce.clear();
        pendingIce.clear();
        if (factory == null) return;
        final String myNonce = newNonce;
        pc = factory.createPeerConnection(RtcEnv.rtcConfig(), new PeerObserverAdapter() {
            @Override
            public void onIceCandidate(IceCandidate candidate) {
                if (node != null) node.child("gIce").push().setValue(RtcEnv.iceToMap(candidate, myNonce));
            }

            @Override
            public void onTrack(RtpTransceiver transceiver) {
                MediaStreamTrack track = transceiver.getReceiver().track();
                rtcSafe.execute(() -> attachTrack(track));
            }

            @Override
            public void onConnectionChange(PeerConnection.PeerConnectionState s) {
                if (!myNonce.equals(nonce)) return;
                switch (s) {
                    case CONNECTED:
                        main.removeCallbacks(connectTimeout);
                        setState(State.LIVE);
                        break;
                    case DISCONNECTED:
                        setState(State.RECONNECTING);
                        break;
                    case FAILED:
                        setState(State.FAILED);
                        break;
                    default:
                        break;
                }
            }
        });
    }

    private void attachTrack(MediaStreamTrack track) {
        if (track instanceof VideoTrack) {
            remoteVideo = (VideoTrack) track;
            if (renderer != null) remoteVideo.addSink(renderer);
        } else if (track instanceof AudioTrack) {
            remoteAudio = (AudioTrack) track;
            remoteAudio.setVolume(muted ? 0 : 1);
        }
    }

    private void onNode(DataSnapshot snap) {
        if (closed || pc == null || nonce == null) return;
        DataSnapshot offer = snap.child("offer");
        if (!offerApplied && nonce.equals(offer.child("nonce").getValue(String.class))) {
            SessionDescription sdp = RtcEnv.sdpFrom(offer.child("type").getValue(String.class),
                    offer.child("sdp").getValue(String.class));
            if (sdp != null) {
                offerApplied = true;
                applyOffer(sdp, nonce);
            }
        }
        for (DataSnapshot c : snap.child("wIce").getChildren()) {
            String key = c.getKey();
            if (key == null || appliedIce.contains(key)) continue;
            if (!nonce.equals(c.child("nonce").getValue(String.class))) continue;
            appliedIce.add(key);
            IceCandidate cand = RtcEnv.iceFrom(c.child("mid").getValue(String.class),
                    c.child("idx").getValue(Long.class), c.child("sdp").getValue(String.class));
            if (cand == null) continue;
            if (pc.getRemoteDescription() == null) pendingIce.add(cand);
            else pc.addIceCandidate(cand);
        }
    }

    private void applyOffer(SessionDescription offer, String forNonce) {
        final PeerConnection peer = pc;
        peer.setRemoteDescription(new SdpAdapter("remote-offer") {
            @Override
            public void onSetSuccess() {
                rtcSafe.execute(() -> {
                    if (peer != pc) return;
                    for (IceCandidate c : pendingIce) peer.addIceCandidate(c);
                    pendingIce.clear();
                    peer.createAnswer(new SdpAdapter("answer") {
                        @Override
                        public void onCreateSuccess(SessionDescription answer) {
                            rtcSafe.execute(() -> {
                                if (peer != pc) return;
                                peer.setLocalDescription(new SdpAdapter("local-answer") {
                                    @Override
                                    public void onSetSuccess() {
                                        if (node != null) {
                                            node.child("answer").setValue(RtcEnv.sdpToMap(answer, forNonce));
                                        }
                                    }
                                }, answer);
                            });
                        }
                    }, new MediaConstraints());
                });
            }
        }, offer);
    }

    private void disposePeer() {
        if (remoteVideo != null && renderer != null) {
            try {
                remoteVideo.removeSink(renderer);
            } catch (Throwable ignored) {
            }
        }
        remoteVideo = null;
        remoteAudio = null;
        if (pc != null) {
            try {
                pc.dispose();
            } catch (Throwable t) {
                Log.w(TAG, "dispose failed", t);
            }
        }
        pc = null;
    }

    private void setState(State s) {
        main.post(() -> listener.onStateChanged(s));
    }

    @SuppressWarnings("deprecation")
    private void routeAudioToSpeaker(boolean on) {
        if (audioManager == null) audioManager = app.getSystemService(AudioManager.class);
        if (audioManager == null) return;
        if (on) {
            previousAudioMode = audioManager.getMode();
            previousSpeaker = audioManager.isSpeakerphoneOn();
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(true);
        } else {
            audioManager.setSpeakerphoneOn(previousSpeaker);
            audioManager.setMode(previousAudioMode);
        }
    }
}
