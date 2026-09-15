# WebRTC P2P Architecture for WatchTogether Mode 1

## Overview
WatchTogether establishes a direct Peer-to-Peer (P2P) WebRTC session between the Host and the Viewer. The signaling server is only involved during connection setup and playback control routing; all high-bandwidth media frames flow directly between the two peer devices.

## Abstraction Interface
In adherence with Section 23 of the design specification, the WebRTC implementation is encapsulated behind the `WebRtcManager` interface:

```kotlin
interface WebRtcManager {
    fun setListener(listener: WebRtcListener?)
    fun createOffer(roomCode: String)
    fun createAnswer(roomCode: String)
    fun setRemoteDescription(type: String, sdp: String)
    fun addIceCandidate(candidate: String, sdpMid: String?, sdpMLineIndex: Int)
    fun close()
    fun setVideoSource(sourceDescription: String)
    fun getIceConnectionState(): String
}
```

## NAT Traversal (STUN & TURN)
Direct P2P connectivity operates through standard ICE candidate gathering:
- **Default STUN Server**: `stun:stun.l.google.com:19302`
- **Configurable TURN Server**: Supported via environment variables (`TURN_SERVER`, `TURN_USERNAME`, `TURN_CREDENTIAL`) for symmetric NAT and restrictive cellular networks.

## Signaling Sequence Flow
```
Host Device                       Signaling Server                     Viewer Device
    │                                    │                                   │
    │─── Connect WS (?role=HOST) ───────>│                                   │
    │<── ROOM_JOINED (count=1) ──────────│                                   │
    │                                    │<── Connect WS (?role=VIEWER) ─────│
    │<── USER_JOINED (count=2) ──────────│─── USER_JOINED (count=2) ────────>│
    │                                    │                                   │
    │─── WEBRTC_OFFER (SDP) ────────────>│─── WEBRTC_OFFER (SDP) ───────────>│
    │                                    │<── WEBRTC_ANSWER (SDP) ───────────│
    │<── WEBRTC_ANSWER (SDP) ────────────│                                   │
    │                                    │                                   │
    │─── ICE_CANDIDATE ─────────────────>│─── ICE_CANDIDATE ────────────────>│
    │<── ICE_CANDIDATE ──────────────────│<── ICE_CANDIDATE ─────────────────│
    │                                    │                                   │
    │====================== Direct P2P Media Stream ========================>│
```

## Error Recovery
- If ICE connection fails or disconnects unexpectedly, the `SignalingClient` initiates an exponential backoff reconnect procedure.
- If a temporary network partition occurs, the Room remains alive on the server, allowing the peer to reconnect without regenerating the room code.
