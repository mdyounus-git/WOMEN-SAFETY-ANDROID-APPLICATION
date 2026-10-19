package com.example.womensafety.live;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.example.womensafety.BuildConfig;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Shared WebRTC setup and the (de)serialisation used for signalling over Realtime Database. */
final class RtcEnv {

    private static boolean initialized;

    private RtcEnv() {
    }

    static synchronized void ensureInitialized(@NonNull Context context) {
        if (initialized) return;
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions
                .builder(context.getApplicationContext())
                .createInitializationOptions());
        initialized = true;
    }

    /**
     * Free Google STUN servers work on most Wi-Fi and many mobile networks. On networks that
     * block peer-to-peer traffic, add a TURN relay in local.properties (see README).
     */
    @NonNull
    static List<PeerConnection.IceServer> iceServers() {
        List<PeerConnection.IceServer> servers = new ArrayList<>();
        List<String> stun = new ArrayList<>();
        stun.add("stun:stun.l.google.com:19302");
        stun.add("stun:stun1.l.google.com:19302");
        stun.add("stun:stun2.l.google.com:19302");
        servers.add(PeerConnection.IceServer.builder(stun).createIceServer());
        if (!TextUtils.isEmpty(BuildConfig.TURN_URL)) {
            List<String> turn = new ArrayList<>();
            for (String url : BuildConfig.TURN_URL.split(",")) {
                if (!url.trim().isEmpty()) turn.add(url.trim());
            }
            servers.add(PeerConnection.IceServer.builder(turn)
                    .setUsername(BuildConfig.TURN_USERNAME)
                    .setPassword(BuildConfig.TURN_PASSWORD)
                    .createIceServer());
        }
        return servers;
    }

    @NonNull
    static PeerConnection.RTCConfiguration rtcConfig() {
        PeerConnection.RTCConfiguration cfg = new PeerConnection.RTCConfiguration(iceServers());
        cfg.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        cfg.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        cfg.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        cfg.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        return cfg;
    }

    // ---------- Signalling payloads ----------

    @NonNull
    static Map<String, Object> sdpToMap(@NonNull SessionDescription sdp, @NonNull String nonce) {
        Map<String, Object> m = new HashMap<>();
        m.put("nonce", nonce);
        m.put("type", sdp.type.canonicalForm());
        m.put("sdp", sdp.description);
        return m;
    }

    static SessionDescription sdpFrom(String type, String description) {
        if (type == null || description == null) return null;
        try {
            return new SessionDescription(SessionDescription.Type.fromCanonicalForm(type), description);
        } catch (Exception e) {
            return null;
        }
    }

    @NonNull
    static Map<String, Object> iceToMap(@NonNull IceCandidate c, @NonNull String nonce) {
        Map<String, Object> m = new HashMap<>();
        m.put("nonce", nonce);
        m.put("mid", c.sdpMid);
        m.put("idx", c.sdpMLineIndex);
        m.put("sdp", c.sdp);
        return m;
    }

    static IceCandidate iceFrom(String mid, Long idx, String sdp) {
        if (sdp == null || idx == null) return null;
        return new IceCandidate(mid, idx.intValue(), sdp);
    }
}
