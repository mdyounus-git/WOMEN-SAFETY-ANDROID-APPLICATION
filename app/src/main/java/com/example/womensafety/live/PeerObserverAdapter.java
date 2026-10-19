package com.example.womensafety.live;

import org.webrtc.CandidatePairChangeEvent;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.IceCandidateErrorEvent;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpTransceiver;

/** No-op {@link PeerConnection.Observer} so subclasses only override what they need. */
class PeerObserverAdapter implements PeerConnection.Observer {

    @Override
    public void onSignalingChange(PeerConnection.SignalingState newState) {
    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState newState) {
    }

    @Override
    public void onStandardizedIceConnectionChange(PeerConnection.IceConnectionState newState) {
    }

    @Override
    public void onConnectionChange(PeerConnection.PeerConnectionState newState) {
    }

    @Override
    public void onIceConnectionReceivingChange(boolean receiving) {
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState newState) {
    }

    @Override
    public void onIceCandidate(IceCandidate candidate) {
    }

    @Override
    public void onIceCandidateError(IceCandidateErrorEvent event) {
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] candidates) {
    }

    @Override
    public void onSelectedCandidatePairChanged(CandidatePairChangeEvent event) {
    }

    @Override
    public void onAddStream(MediaStream stream) {
    }

    @Override
    public void onRemoveStream(MediaStream stream) {
    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {
    }

    @Override
    public void onRenegotiationNeeded() {
    }

    @Override
    public void onAddTrack(RtpReceiver receiver, MediaStream[] mediaStreams) {
    }

    @Override
    public void onRemoveTrack(RtpReceiver receiver) {
    }

    @Override
    public void onTrack(RtpTransceiver transceiver) {
    }
}
