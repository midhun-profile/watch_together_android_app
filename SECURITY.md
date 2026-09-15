# WatchTogether Security & Privacy Policy

## 1. Zero Video Cloud Storage
Movie files, video frames, and raw media bytes are **never stored on or uploaded to the backend server**. All media playback originates from the Host device and streams directly P2P via WebRTC to the Viewer.

## 2. Ephemeral Sessions & No Personal Accounts
WatchTogether Mode 1 requires no user signups, passwords, phone numbers, or account profiles. Rooms are transient sessions represented by random 6-character room codes and are automatically purged after 24 hours.

## 3. Rate Limiting & Abuse Prevention
The backend features an IP-based sliding window rate limiter on both room creation and room joining endpoints. This prevents automated room code brute-forcing and denial-of-service attempts.

## 4. Participant Limits
Rooms strictly enforce a maximum of 2 participants (Host and Viewer). Third-party connection attempts are rejected with `409 Conflict: ROOM_FULL`.

## 5. Authoritative Host Security
Playback controls (`PLAY`, `PAUSE`, `SEEK`) are authenticated on the signaling layer against the client's role. Commands originating from a viewer connection are rejected to prevent unauthorized disruption.
