package com.example.womensafety.live;

import android.util.Log;

import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

/** {@link SdpObserver} that logs failures; override the success callback you need. */
class SdpAdapter implements SdpObserver {

    private final String label;

    SdpAdapter(String label) {
        this.label = label;
    }

    @Override
    public void onCreateSuccess(SessionDescription sdp) {
    }

    @Override
    public void onSetSuccess() {
    }

    @Override
    public void onCreateFailure(String error) {
        Log.w("Sdp", label + " create failed: " + error);
    }

    @Override
    public void onSetFailure(String error) {
        Log.w("Sdp", label + " set failed: " + error);
    }
}
