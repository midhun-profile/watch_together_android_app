# WatchTogether Room Protocol Specification

## 1. Room Lifecycle
A room progresses through the following states:
1. `WAITING`: Room created by host; awaiting viewer to join.
2. `CONNECTED`: Both host and viewer connected via signaling and WebRTC handshake initiated.
3. `ACTIVE`: Movie playback is active and streaming peer-to-peer.
4. `DISCONNECTED`: A participant disconnected; room stays alive for transient reconnection.
5. `EXPIRED`: Room TTL reached (default 24 hours); room purged from memory.

## 2. Room Code Structure
- **Length**: Exactly 6 uppercase characters.
- **Alphabet**: 32 unambiguous characters `[2-9, A-Z \ {0, O, 1, I}]`.
- **Collision avoidance**: Secure random generation with unique verification loop.

## 3. REST API Endpoints

### Create Room
`POST /api/rooms`
- Request: Empty JSON `{}`
- Response (`201 Created`):
```json
{
  "roomId": "e93ab871-36f9-4b68-8a8b-1e231189ac35",
  "roomCode": "K7P4XM",
  "role": "HOST",
  "expiresAt": "2026-09-16T15:00:00.000Z"
}
```

### Join Room
`POST /api/rooms/{roomCode}/join`
- Request: Empty JSON `{}`
- Response (`200 OK`):
```json
{
  "roomId": "e93ab871-36f9-4b68-8a8b-1e231189ac35",
  "roomCode": "K7P4XM",
  "role": "VIEWER",
  "expiresAt": "2026-09-16T15:00:00.000Z"
}
```
- Errors:
  - `404 Not Found`: Room code does not exist.
  - `409 Conflict`: Room already has 2 participants.
  - `410 Gone`: Room has expired.

### Get Room Status
`GET /api/rooms/{roomCode}`
- Response (`200 OK`):
```json
{
  "roomCode": "K7P4XM",
  "status": "CONNECTED",
  "participantCount": 2,
  "expiresAt": "2026-09-16T15:00:00.000Z"
}
```

### Leave/Close Room
`DELETE /api/rooms/{roomCode}`
- Response (`200 OK`):
```json
{
  "message": "ROOM_CLOSED"
}
```

## 4. WebSocket Signaling Messages

WebSocket Endpoint: `/ws?roomCode={roomCode}&role={role}`

### Message Envelope
```json
{
  "type": "MESSAGE_TYPE",
  "roomCode": "K7P4XM",
  "sequence": 104,
  "timestamp": 1726435200000,
  "payload": {}
}
```

### Message Types
- `ROOM_JOINED`: Server acknowledges client registration and sends initial participant count.
- `USER_JOINED`: Broadcast when peer joins the room.
- `USER_LEFT`: Broadcast when peer disconnects.
- `HOST_DISCONNECTED`: Informs viewer that host ended the session.
- `WEBRTC_OFFER`: Host sends SDP offer to viewer.
- `WEBRTC_ANSWER`: Viewer sends SDP answer to host.
- `ICE_CANDIDATE`: Exchange of ICE candidate strings.
- `PLAY`: Host initiates play (`positionMs`, `sentAt`).
- `PAUSE`: Host initiates pause (`positionMs`, `sentAt`).
- `SEEK`: Host seeks to target position (`targetPositionMs`, `sentAt`).
- `SYNC`: Periodic sync packet emitted every 1.5s (`positionMs`, `isPlaying`, `playbackSpeed`, `sentAt`).
- `MEDIA_STARTED`: Host selected a video (`name`, `durationMs`).
- `PING` / `PONG`: Heartbeat keepalive every 15-20 seconds.
