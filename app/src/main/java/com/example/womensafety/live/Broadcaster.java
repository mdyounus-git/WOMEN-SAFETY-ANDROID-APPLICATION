package com.example.womensafety.live;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.womensafety.core.Backend;
import com.example.womensafety.evidence.EvidenceStore;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpParameters;
import org.webrtc.RtpSender;
import org.webrtc.RtpTransceiver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Runs on the SOS phone. Opens the camera and microphone once, then:
 * <ul>
 *   <li>saves a photo every few seconds and AAC audio clips as evidence, and</li>
 *   <li>streams live video + audio to each guardian who opens the live view
 *       (one WebRTC connection per guardian, signalled through Realtime Database).</li>
 * </ul>
 * All WebRTC state is touched only on a single executor thread.
 */
public final class Broadcaster {

    private static final String TAG = "Broadcaster";
    private static final long SNAPSHOT_EVERY_MS = 4_000L;
    private static final int VIDEO_W = 640;
    private static final int VIDEO_H = 480;
    private static final int VIDEO_FPS = 15;
    private static final int MAX_VIDEO_BITRATE = 650_000;

    public interface Listener {
        /** Called on the main thread when the number of connected guardians changes. */
        void onViewerCountChanged(int viewers);
    }

    private final Context app;
    private final EvidenceStore evidence;
    private final boolean useCamera;
    private final boolean useMic;
    @Nullable
    private final Listener listener;
    private final ExecutorService rtc = Executors.newSingleThreadExecutor();
    /** Drops work quietly once stopped (late WebRTC/Firebase callbacks are expected). */
    private final Executor rtcSafe = r -> {
        try {
            rtc.execute(r);
        } catch (RejectedExecutionException ignored) {
            // shutting down
        }
    };
    private final Handler main = new Handler(Looper.getMainLooper());

    // ---- WebRTC (rtc thread only) ----
    private EglBase egl;
    private JavaAudioDeviceModule adm;
    private PeerConnectionFactory factory;
    private volatile CameraVideoCapturer capturer;
    private SurfaceTextureHelper textureHelper;
    private VideoSource videoSource;
    private VideoTrack videoTrack;
    private AudioSource audioSource;
    private AudioTrack audioTrack;
    private FrameSnapshotter snapshotter;
    private AacClipEncoder aacEncoder;
    private Mp4Recorder recorder;
    private volatile boolean frontCamera;
    private AudioLoopback loopback;
    private FallbackRecorder fallback;
    private final Map<String, ViewerSession> viewers = new HashMap<>();
    private final Set<String> linkedGuardians = new HashSet<>();
    /** Requests that arrived before the guardian list loaded; replayed once it does. */
    private final Map<String, DataSnapshot> waitingNodes = new HashMap<>();
    private boolean started;
    private boolean stopped;

    // ---- Backend (main thread) ----
    private volatile String uid;
    private DatabaseReference callsRef;
    private DatabaseReference guardiansRef;
    private ChildEventListener callsListener;
    private ValueEventListener guardiansListener;

    private final Runnable micWatchdog = new Runnable() {
        @Override
        public void run() {
            rtcSafe.execute(Broadcaster.this::checkMicrophone);
            main.postDelayed(this, 3_000L);
        }
    };

    public Broadcaster(@NonNull Context context, @NonNull EvidenceStore evidence, boolean useCamera,
                       boolean useMic, @Nullable Listener listener) {
        this.app = context.getApplicationContext();
        this.evidence = evidence;
        this.useCamera = useCamera;
        this.useMic = useMic;
        this.listener = listener;
    }

    /** Opens camera/mic and starts saving evidence. Safe to call from any thread. */
    public void start() {
        rtcSafe.execute(this::startMedia);
        if (useMic) main.postDelayed(micWatchdog, 6_000L);
    }

    /** Starts accepting guardians' live-view requests. Call on the main thread. */
    public void attachBackend(@NonNull String wardUid) {
        if (uid != null) return;
        uid = wardUid;
        publishCamera();
        guardiansRef = Backend.guardians(wardUid);
        guardiansListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Set<String> ids = new HashSet<>();
                for (DataSnapshot g : snapshot.getChildren()) ids.add(g.getKey());
                rtcSafe.execute(() -> {
                    linkedGuardians.clear();
                    linkedGuardians.addAll(ids);
                    // Someone removed from the circle loses the stream immediately.
                    for (String g : new ArrayList<>(viewers.keySet())) {
                        if (!linkedGuardians.contains(g)) closeViewer(g);
                    }
                    for (String g : new ArrayList<>(waitingNodes.keySet())) {
                        if (linkedGuardians.contains(g)) onViewerNode(waitingNodes.remove(g));
                    }
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Guardians listener cancelled: " + error.getMessage());
            }
        };
        guardiansRef.addValueEventListener(guardiansListener);

        callsRef = Backend.calls(wardUid);
        callsRef.removeValue(); // clear anything left from a previous session
        callsListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                rtcSafe.execute(() -> onViewerNode(snapshot));
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                rtcSafe.execute(() -> onViewerNode(snapshot));
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                String g = snapshot.getKey();
                if (g != null) rtcSafe.execute(() -> {
                    waitingNodes.remove(g);
                    closeViewer(g);
                });
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.w(TAG, "Calls listener cancelled: " + error.getMessage());
            }
        };
        callsRef.addChildEventListener(callsListener);
    }

    public void switchCamera() {
        rtcSafe.execute(this::doSwitchCamera);
    }

    /** Releases camera, microphone and all connections. Call on the main thread. */
    public void stop() {
        main.removeCallbacks(micWatchdog);
        if (callsRef != null && callsListener != null) callsRef.removeEventListener(callsListener);
        if (guardiansRef != null && guardiansListener != null) {
            guardiansRef.removeEventListener(guardiansListener);
        }
        if (callsRef != null) callsRef.removeValue();
        rtcSafe.execute(this::stopMedia);
        rtc.shutdown();
    }

    // ===================== Media =====================

    private void startMedia() {
        if (started || stopped) return;
        started = true;
        try {
            RtcEnv.ensureInitialized(app);
            egl = EglBase.create();

            aacEncoder = useMic ? new AacClipEncoder(evidence) : null;
            // Full video recording (MP4 with sound) of the live camera, saved on this phone.
            recorder = useCamera ? new Mp4Recorder(new Mp4Recorder.Output() {
                @NonNull
                @Override
                public File newSegmentFile() {
                    return evidence.newVideoFile();
                }

                @Override
                public void onSegmentSaved(@NonNull File file, long durationMs) {
                    evidence.onVideoSaved();
                }
            }, "sos") : null;
            JavaAudioDeviceModule.Builder admBuilder = JavaAudioDeviceModule.builder(app)
                    .setUseHardwareAcousticEchoCanceler(true)
                    .setUseHardwareNoiseSuppressor(true);
            if (aacEncoder != null) {
                final AacClipEncoder clips = aacEncoder;
                final Mp4Recorder video = recorder;
                admBuilder.setSamplesReadyCallback(samples -> {
                    clips.onWebRtcAudioRecordSamplesReady(samples);
                    if (video != null) video.onAudio(samples);
                });
            }
            adm = admBuilder.createAudioDeviceModule();

            PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
            options.networkIgnoreMask = 0; // allow the on-device loopback used for evidence audio
            factory = PeerConnectionFactory.builder()
                    .setOptions(options)
                    .setAudioDeviceModule(adm)
                    .setVideoEncoderFactory(new DefaultVideoEncoderFactory(
                            egl.getEglBaseContext(), true, true))
                    .setVideoDecoderFactory(new DefaultVideoDecoderFactory(egl.getEglBaseContext()))
                    .createPeerConnectionFactory();

            if (useCamera) startCamera(false);

            if (useMic) {
                audioSource = factory.createAudioSource(new MediaConstraints());
                audioTrack = factory.createAudioTrack("ws-audio", audioSource);
                audioTrack.setEnabled(true);
                fallback = new FallbackRecorder(app, evidence);
                loopback = new AudioLoopback(factory, audioTrack, rtcSafe);
                loopback.start();
            }
            Log.i(TAG, "Media started (camera=" + useCamera + ", mic=" + useMic + ")");
        } catch (Throwable t) {
            Log.e(TAG, "Could not start camera/microphone", t);
        }
    }

    private void startCamera(boolean front) {
        CameraEnumerator enumerator = Camera2Enumerator.isSupported(app)
                ? new Camera2Enumerator(app) : new Camera1Enumerator(true);
        String chosen = null;
        for (String name : enumerator.getDeviceNames()) {
            if (front ? enumerator.isFrontFacing(name) : enumerator.isBackFacing(name)) {
                chosen = name;
                break;
            }
        }
        if (chosen == null && enumerator.getDeviceNames().length > 0) {
            chosen = enumerator.getDeviceNames()[0];
        }
        if (chosen == null) {
            Log.w(TAG, "No camera found");
            return;
        }
        capturer = enumerator.createCapturer(chosen, new CameraLog());
        textureHelper = SurfaceTextureHelper.create("ws-capture", egl.getEglBaseContext());
        videoSource = factory.createVideoSource(false);
        capturer.initialize(textureHelper, app, videoSource.getCapturerObserver());
        capturer.startCapture(VIDEO_W, VIDEO_H, VIDEO_FPS);
        videoTrack = factory.createVideoTrack("ws-video", videoSource);
        videoTrack.setEnabled(true);
        snapshotter = new FrameSnapshotter(evidence, SNAPSHOT_EVERY_MS);
        videoTrack.addSink(snapshotter);
        if (recorder != null) {
            videoTrack.addSink(recorder);
            recorder.start();
        }
        frontCamera = enumerator.isFrontFacing(chosen);
        publishCamera();
    }

    private void doSwitchCamera() {
        if (capturer == null) return;
        capturer.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
            @Override
            public void onCameraSwitchDone(boolean isFrontCamera) {
                frontCamera = isFrontCamera;
                publishCamera();
                Log.i(TAG, "Camera switched, front=" + isFrontCamera);
            }

            @Override
            public void onCameraSwitchError(String errorDescription) {
                Log.w(TAG, "Camera switch failed: " + errorDescription);
            }
        });
    }

    /** If WebRTC isn't delivering mic audio, record with a plain recorder instead (and back). */
    private void checkMicrophone() {
        if (stopped || aacEncoder == null || fallback == null) return;
        long last = aacEncoder.lastSamplesAt();
        boolean flowing = last > 0 && SystemClock.elapsedRealtime() - last < 4_000L;
        if (!flowing && !fallback.isRunning()) {
            Log.i(TAG, "No WebRTC mic audio; using fallback recorder");
            fallback.start();
        } else if (flowing && fallback.isRunning()) {
            fallback.stop();
        }
    }

    private void stopMedia() {
        if (stopped) return;
        stopped = true;
        for (String g : new ArrayList<>(viewers.keySet())) closeViewer(g);
        try {
            if (loopback != null) loopback.stop();
            if (fallback != null) fallback.stop();
            if (videoTrack != null && snapshotter != null) videoTrack.removeSink(snapshotter);
            if (videoTrack != null && recorder != null) videoTrack.removeSink(recorder);
            if (recorder != null) recorder.stop();
            if (snapshotter != null) snapshotter.close();
            if (capturer != null) {
                try {
                    capturer.stopCapture();
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                capturer.dispose();
            }
            if (videoTrack != null) videoTrack.dispose();
            if (videoSource != null) videoSource.dispose();
            if (textureHelper != null) textureHelper.dispose();
            if (audioTrack != null) audioTrack.dispose();
            if (audioSource != null) audioSource.dispose();
            if (factory != null) factory.dispose();
            if (adm != null) adm.release();
            if (aacEncoder != null) aacEncoder.close();
            if (egl != null) egl.release();
        } catch (Throwable t) {
            Log.w(TAG, "Error while releasing media", t);
        }
        Log.i(TAG, "Media stopped");
    }

    /** Tells guardians which camera is live, so their Front / Back switch shows it. */
    private void publishCamera() {
        String id = uid;
        if (id == null || !useCamera || capturer == null) return;
        Backend.sos(id).child("camera").setValue(frontCamera ? "front" : "back");
    }

    /** Number of video segments saved so far. */
    public int getVideoCount() {
        return evidence.getVideoCount();
    }

    // ===================== Viewers (guardians) =====================

    /** One guardian's live connection. */
    private static final class ViewerSession {
        final String guardianUid;
        final String nonce;
        PeerConnection pc;
        boolean answerApplied;
        boolean connected;
        Long lastSwitch;
        final Set<String> appliedIce = new HashSet<>();
        final List<IceCandidate> pendingIce = new ArrayList<>();

        ViewerSession(String guardianUid, String nonce) {
            this.guardianUid = guardianUid;
            this.nonce = nonce;
        }
    }

    /** The guardian's node changed: new request, answer, ICE candidates or a command. */
    private void onViewerNode(@NonNull DataSnapshot node) {
        if (stopped || factory == null) return;
        String g = node.getKey();
        String req = node.child("req").getValue(String.class);
        if (g == null) return;
        if (req == null) {
            waitingNodes.remove(g);
            closeViewer(g);
            return;
        }
        if (!linkedGuardians.contains(g)) {
            // Either the guardian list hasn't loaded yet, or this isn't a guardian at all.
            waitingNodes.put(g, node);
            return;
        }
        ViewerSession s = viewers.get(g);
        if (s == null || !req.equals(s.nonce)) {
            closeViewer(g);
            s = openViewer(g, req);
            if (s == null) return;
            s.lastSwitch = node.child("cmd").child("switchCam").getValue(Long.class);
        }

        // Answer from the guardian
        DataSnapshot ans = node.child("answer");
        if (!s.answerApplied && req.equals(ans.child("nonce").getValue(String.class))) {
            SessionDescription answer = RtcEnv.sdpFrom(ans.child("type").getValue(String.class),
                    ans.child("sdp").getValue(String.class));
            if (answer != null) {
                s.answerApplied = true;
                final ViewerSession session = s;
                s.pc.setRemoteDescription(new SdpAdapter("remote-answer") {
                    @Override
                    public void onSetSuccess() {
                        rtcSafe.execute(() -> {
                            for (IceCandidate c : session.pendingIce) session.pc.addIceCandidate(c);
                            session.pendingIce.clear();
                            capBitrate(session.pc);
                        });
                    }
                }, answer);
            }
        }

        // Guardian's ICE candidates
        for (DataSnapshot c : node.child("gIce").getChildren()) {
            String key = c.getKey();
            if (key == null || s.appliedIce.contains(key)) continue;
            if (!req.equals(c.child("nonce").getValue(String.class))) continue;
            IceCandidate cand = RtcEnv.iceFrom(c.child("mid").getValue(String.class),
                    c.child("idx").getValue(Long.class), c.child("sdp").getValue(String.class));
            s.appliedIce.add(key);
            if (cand == null) continue;
            if (s.pc.getRemoteDescription() == null) s.pendingIce.add(cand);
            else s.pc.addIceCandidate(cand);
        }

        // Commands
        Long sw = node.child("cmd").child("switchCam").getValue(Long.class);
        if (sw != null && !sw.equals(s.lastSwitch)) {
            s.lastSwitch = sw;
            doSwitchCamera();
        }
    }

    @Nullable
    private ViewerSession openViewer(@NonNull String g, @NonNull String nonce) {
        final ViewerSession s = new ViewerSession(g, nonce);
        final DatabaseReference node = callsRef.child(g);
        s.pc = factory.createPeerConnection(RtcEnv.rtcConfig(), new PeerObserverAdapter() {
            @Override
            public void onIceCandidate(IceCandidate candidate) {
                node.child("wIce").push().setValue(RtcEnv.iceToMap(candidate, nonce));
            }

            @Override
            public void onConnectionChange(PeerConnection.PeerConnectionState state) {
                rtcSafe.execute(() -> {
                    s.connected = state == PeerConnection.PeerConnectionState.CONNECTED;
                    if (state == PeerConnection.PeerConnectionState.FAILED) {
                        Log.w(TAG, "Viewer connection failed (network may need a TURN relay)");
                    }
                    publishViewerCount();
                });
            }
        });
        if (s.pc == null) return null;
        s.pc.setAudioPlayout(false);
        List<String> streams = Collections.singletonList("ws");
        if (videoTrack != null) {
            s.pc.addTransceiver(videoTrack, new RtpTransceiver.RtpTransceiverInit(
                    RtpTransceiver.RtpTransceiverDirection.SEND_ONLY, streams));
        }
        if (audioTrack != null) {
            s.pc.addTransceiver(audioTrack, new RtpTransceiver.RtpTransceiverInit(
                    RtpTransceiver.RtpTransceiverDirection.SEND_ONLY, streams));
        }
        viewers.put(g, s);

        s.pc.createOffer(new SdpAdapter("offer") {
            @Override
            public void onCreateSuccess(SessionDescription sdp) {
                rtcSafe.execute(() -> {
                    if (viewers.get(g) != s) return;
                    s.pc.setLocalDescription(new SdpAdapter("local-offer") {
                        @Override
                        public void onSetSuccess() {
                            node.child("offer").setValue(RtcEnv.sdpToMap(sdp, nonce));
                        }
                    }, sdp);
                });
            }
        }, new MediaConstraints());
        Log.i(TAG, "Live view requested by a guardian");
        return s;
    }

    private void closeViewer(@NonNull String g) {
        ViewerSession s = viewers.remove(g);
        if (s == null) return;
        try {
            s.pc.dispose();
        } catch (Throwable t) {
            Log.w(TAG, "Error closing viewer", t);
        }
        publishViewerCount();
    }

    private void capBitrate(PeerConnection pc) {
        try {
            for (RtpSender sender : pc.getSenders()) {
                if (sender.track() == null || !"video".equals(sender.track().kind())) continue;
                RtpParameters params = sender.getParameters();
                for (RtpParameters.Encoding e : params.encodings) e.maxBitrateBps = MAX_VIDEO_BITRATE;
                sender.setParameters(params);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Could not cap bitrate", t);
        }
    }

    private void publishViewerCount() {
        int count = 0;
        for (ViewerSession s : viewers.values()) if (s.connected) count++;
        final int c = count;
        if (listener != null) main.post(() -> listener.onViewerCountChanged(c));
    }

    /** Logs camera events; the capturer handles recovery itself. */
    private static final class CameraLog implements CameraVideoCapturer.CameraEventsHandler {
        @Override
        public void onCameraError(String errorDescription) {
            Log.w(TAG, "Camera error: " + errorDescription);
        }

        @Override
        public void onCameraDisconnected() {
            Log.w(TAG, "Camera disconnected");
        }

        @Override
        public void onCameraFreezed(String errorDescription) {
            Log.w(TAG, "Camera frozen: " + errorDescription);
        }

        @Override
        public void onCameraOpening(String cameraName) {
        }

        @Override
        public void onFirstFrameAvailable() {
            Log.i(TAG, "Camera delivering frames");
        }

        @Override
        public void onCameraClosed() {
        }
    }
}
