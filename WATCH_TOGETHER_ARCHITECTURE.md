# WatchTogether Mode 1 Architecture

## Overview
WatchTogether Mode 1 enables a host device to stream and synchronize a local movie file with a friend in real time using WebRTC P2P streaming and WebSocket signaling.

The architecture strictly adheres to the core system principles:
1. **Existing Player Preservation**: The existing video player engine (Media3/LibVLC) was preserved with 0% logic rewrite. WatchTogether interacts with playback via a clean `PlaybackController` abstraction adapter.
2. **Local Video Privacy**: Movie video files and bytes remain strictly on the host device. Movies are never uploaded to the backend server.
3. **P2P Streaming via WebRTC**: Video and audio are transmitted peer-to-peer between Host and Viewer.
4. **Authoritative Host Playback**: The Host is the single authority on play, pause, seek, and playback position.
5. **No Persistent Account/Login Required**: Transient, ephemeral 6-character room codes govern session access.

```
┌─────────────────────────────────────────────────────────────────┐
│                     WATCHTOGETHER ARCHITECTURE                  │
├────────────────────────────────┬────────────────────────────────┤
│           HOST DEVICE          │          VIEWER DEVICE         │
├────────────────────────────────┼────────────────────────────────┤
│  [Local Video File / SAF]      │                                │
│              │                 │                                │
│   [Existing VideoPlayer]       │                                │
│              │                 │                                │
│    [PlaybackController]        │       [PlaybackController]     │
│              │                 │                 │              │
│    [PlaybackSyncManager]       │       [PlaybackSyncManager]    │
│              │                 │                 │              │
│      [WebRtcManager] <===========P2P Media====> [WebRtcManager] │
│              │                 │                 │              │
│      [SignalingClient]         │         [SignalingClient]      │
└──────────────┬─────────────────┴─────────────────┬──────────────┘
               │                                   │
               ▼                                   ▼
        ┌─────────────────────────────────────────────────┐
        │            SIGNALING & ROOM BACKEND             │
        │    REST API: /api/rooms, /api/rooms/:code/join  │
        │    WebSocket: /ws (WebRTC SDP, ICE, Play/Pause) │
        │    Room Expiration: 24h, Max 2 Participants     │
        └─────────────────────────────────────────────────┘
```

## Layer Descriptions
- **Presentation Layer**:
  - `WatchTogetherStartScreen`: Entry point featuring Create Room, Room Code input (6 characters), Join Room, and Browse Local Videos.
  - `RoomCreatedScreen`: Displays generated room code with copy to clipboard, system share intent, and waiting indicators.
  - `RoomSessionScreen`: Real-time session interface showing video playback, sync drift indicator, participant status, and stream HUD.
- **State & ViewModel Layer**:
  - `WatchTogetherViewModel`: Coordinates session lifecycle, error handling, WebRTC state, and signaling callbacks.
- **Domain & Playback Abstraction**:
  - `PlaybackController`: Minimal interface (`play()`, `pause()`, `seekTo()`, `getCurrentPosition()`, `getDuration()`, `isPlaying()`).
  - `ExistingPlayerAdapter`: Non-invasive adapter hooking into the existing `VideoPlayer`.
- **Sync Engine**:
  - `PlaybackSyncManager`: Emits host actions and periodic sync ticks every 1.5s. Implements latency compensation and threshold-based drift correction on the viewer.
- **Signaling & WebRTC**:
  - `SignalingClient`: OkHttp-powered WebSocket client with automatic heartbeat, reconnect, and sequence ordering.
  - `WebRtcManager`: Manages SDP Offer/Answer exchanges and ICE candidates with STUN/TURN fallbacks.
