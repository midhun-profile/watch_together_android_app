# Changelog

## [1.1.0] - Mode 1: Real-Time P2P Movie Synchronization

### Added
- **WatchTogether Mode 1**: Full implementation of synchronized video playback between Host and Viewer devices.
- **Backend Room & Signaling System**:
  - REST API for room creation (`POST /api/rooms`), joining (`POST /api/rooms/:code/join`), and status lookup.
  - WebSocket signaling server (`/ws`) for WebRTC SDP offers/answers, ICE candidate exchange, and playback synchronization.
  - 6-character room codes using a 32-character unambiguous alphabet.
  - In-memory room manager with 24-hour expiration lifecycle and 2-participant limit.
  - PostgreSQL database schema (`backend/src/database/schema.sql`).
- **Android Architecture**:
  - `PlaybackController` clean abstraction interface and `ExistingPlayerAdapter` to hook into existing player without rewriting internals.
  - `WebRtcManager` interface and implementation for P2P connection lifecycle and STUN/TURN configuration.
  - `PlaybackSyncManager` handling host authority, monotonic sequence numbers, latency compensation, and drift thresholds (<250ms ignore, 250-1000ms gentle, >1000ms hard seek).
  - `SignalingClient` using OkHttp WebSocket with heartbeat keepalive and exponential backoff reconnection.
  - `WatchTogetherStartScreen`: Clean M3 start screen with Create Room, 6-character room code entry, and Join Room.
  - `RoomCreatedScreen`: Displays generated room code with copy to clipboard and native Android sharesheet.
  - `RoomSessionScreen`: Live session UI displaying synchronized video, drift metrics, and participant statuses.
  - Full bidirectional navigation between Local Video Library and WatchTogether.
- **Unit Tests**:
  - `WatchTogetherTests.kt`: Verifies unambiguous 32-character room code alphabet, JSON serialization of signaling messages, sequence ordering, and drift correction logic.
- **Architecture Documentation**:
  - `WATCH_TOGETHER_ARCHITECTURE.md`
  - `ROOM_PROTOCOL.md`
  - `WEBRTC_ARCHITECTURE.md`
  - `PLAYBACK_SYNC.md`
  - `BACKEND_SETUP.md`
  - `SECURITY.md`

### Preserved
- 100% of existing LibVLC and Media3 video player engines, codecs, and local media playback functionality.
- Existing Room database for favorites, playback history, and folder navigation.
