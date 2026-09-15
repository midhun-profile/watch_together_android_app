# Playback Synchronization Protocol

## Host Authority
In WatchTogether Mode 1, playback state is **strictly authoritative to the Host**.
The Viewer's player runs as a follower:
- The Host controls `PLAY`, `PAUSE`, `SEEK`, and media selection.
- Any unauthorized playback commands from the Viewer are dropped by the server and ignored by the sync engine.

## Sequence Numbers & Anti-Reordering
Every sync packet carries:
- `sequence`: Monotonically increasing 64-bit integer counter.
- `timestamp`: Epoch millisecond timestamp when the action occurred.
- `sentAt`: Timestamp embedded in the payload for latency estimation.

```kotlin
if (sequence > 0 && sequence <= lastProcessedSequence) {
    // Drop out-of-order or duplicate packet
    return
}
lastProcessedSequence = sequence
```

## Latency Compensation
When the viewer receives a `PLAY` or `SYNC` message:
```kotlin
val now = System.currentTimeMillis()
val latency = (now - sentAt).coerceIn(0L, 2000L) // estimated one-way latency
val targetPositionMs = hostPositionMs + (if (isPlaying) (latency * speed).toLong() else 0L)
```

## Drift Thresholds & Correction Logic
The difference between Viewer position and Host target position determines the corrective action:
- **Drift < 250ms**: Ignored (`isInSync = true`). Eliminates micro-stuttering and jitter.
- **Drift 250ms - 1000ms**: Gentle speed / smooth position adjustment.
- **Drift > 1000ms**: Hard seek (`seekTo(targetPositionMs)`) to authoritative host position.

## Periodic Sync Timer
The Host runs a coroutine ticker emitting `SYNC` messages every 1500ms. This ensures that any network drops or drift accumulation are corrected automatically within two seconds.
