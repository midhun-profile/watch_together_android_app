/**
 * WatchTogether Mode 1 Backend Server
 * Provides:
 * - REST API:
 *     POST   /api/rooms           -> Create room (returns 6-char room code, HOST role)
 *     POST   /api/rooms/:code/join -> Join room (validates existence, expiration, capacity)
 *     GET    /api/rooms/:code     -> Room status lookup
 *     DELETE /api/rooms/:code     -> Close/leave room
 * - WebSocket Server:
 *     Signaling for WebRTC Offer, Answer, ICE Candidates
 *     Playback Synchronization (PLAY, PAUSE, SEEK, SYNC with sequence numbers & timestamps)
 *     Room state events & Heartbeat (PING/PONG)
 * - Rate Limiter (Brute-force protection on join and create attempts)
 * - Room Expiration Engine (Automatic cleanup of expired sessions)
 */

const http = require('http');
const crypto = require('crypto');
const url = require('url');

const PORT = parseInt(process.env.PORT || '9090', 10);
const ROOM_TTL_HOURS = parseInt(process.env.ROOM_TTL_HOURS || '24', 10);
const MAX_PARTICIPANTS = 2; // Mode 1: Host and Viewer only

// Safe alphabet: 32 uppercase characters excluding ambiguous characters (0, O, 1, I)
const CODE_ALPHABET = '23456789ABCDEFGHJKLMNPQRSTUVWXYZ';

function generateRoomCode() {
  const bytes = crypto.randomBytes(6);
  let code = '';
  for (let i = 0; i < 6; i++) {
    code += CODE_ALPHABET[bytes[i] % CODE_ALPHABET.length];
  }
  return code;
}

// In-Memory Room Manager
class RoomManager {
  constructor() {
    this.rooms = new Map(); // roomCode -> Room
  }

  createRoom() {
    let attempts = 0;
    let code = generateRoomCode();
    while (this.rooms.has(code) && attempts < 10) {
      code = generateRoomCode();
      attempts++;
    }

    const now = Date.now();
    const expiresAt = now + ROOM_TTL_HOURS * 3600 * 1000;
    const room = {
      id: crypto.randomUUID(),
      code: code,
      status: 'WAITING', // WAITING -> CONNECTED -> ACTIVE -> DISCONNECTED -> EXPIRED
      createdAt: now,
      lastActivity: now,
      expiresAt: expiresAt,
      hostConnection: null,
      viewerConnection: null,
      mediaInfo: null,
      lastPlaybackState: {
        positionMs: 0,
        isPlaying: false,
        playbackSpeed: 1.0,
        sequence: 0,
        updatedAt: now
      }
    };

    this.rooms.set(code, room);
    console.log(`[ROOM_CREATE] Created room ${code}, expires in ${ROOM_TTL_HOURS}h`);
    return room;
  }

  getRoom(code) {
    if (!code) return null;
    const normalized = code.trim().toUpperCase();
    const room = this.rooms.get(normalized);
    if (!room) return null;

    if (Date.now() > room.expiresAt) {
      room.status = 'EXPIRED';
      this.rooms.delete(normalized);
      console.log(`[ROOM_EXPIRED] Room ${normalized} expired and removed`);
      return null;
    }
    return room;
  }

  joinRoom(code) {
    const room = this.getRoom(code);
    if (!room) {
      return { success: false, error: 'ROOM_NOT_FOUND', status: 404 };
    }
    if (room.status === 'EXPIRED') {
      return { success: false, error: 'ROOM_EXPIRED', status: 410 };
    }
    if (room.viewerConnection !== null) {
      return { success: false, error: 'ROOM_FULL', status: 409 };
    }

    room.lastActivity = Date.now();
    return { success: true, room: room };
  }

  removeRoom(code) {
    const normalized = code.trim().toUpperCase();
    return this.rooms.delete(normalized);
  }

  cleanupExpired() {
    const now = Date.now();
    for (const [code, room] of this.rooms.entries()) {
      if (now > room.expiresAt) {
        this.rooms.delete(code);
        console.log(`[CLEANUP] Purged expired room ${code}`);
      }
    }
  }
}

// In-Memory Rate Limiter (IP-based sliding window)
class RateLimiter {
  constructor() {
    this.attempts = new Map(); // ip -> [timestamps]
  }

  isAllowed(ip, maxRequests = 20, windowMs = 60000) {
    const now = Date.now();
    const timestamps = this.attempts.get(ip) || [];
    const valid = timestamps.filter(t => now - t < windowMs);
    if (valid.length >= maxRequests) {
      this.attempts.set(ip, valid);
      return false;
    }
    valid.push(now);
    this.attempts.set(ip, valid);
    return true;
  }
}

const roomManager = new RoomManager();
const rateLimiter = new RateLimiter();

// Clean up expired rooms every 15 minutes
setInterval(() => {
  roomManager.cleanupExpired();
}, 15 * 60 * 1000);

// Simple Native WebSocket Handshake & Frame Implementation
function handleWebSocketUpgrade(req, socket, head) {
  const secKey = req.headers['sec-websocket-key'];
  if (!secKey) {
    socket.destroy();
    return;
  }

  const hash = crypto.createHash('sha1')
    .update(secKey + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11')
    .digest('base64');

  const responseHeaders = [
    'HTTP/1.1 101 Switching Protocols',
    'Upgrade: websocket',
    'Connection: Upgrade',
    `Sec-WebSocket-Accept: ${hash}`
  ];

  socket.write(responseHeaders.join('\r\n') + '\r\n\r\n');

  const parsedUrl = url.parse(req.url, true);
  const roomCode = (parsedUrl.query.roomCode || '').trim().toUpperCase();
  const role = (parsedUrl.query.role || 'VIEWER').trim().toUpperCase(); // HOST or VIEWER
  const connectionId = crypto.randomBytes(6).toString('hex');

  const client = {
    id: connectionId,
    roomCode: roomCode,
    role: role,
    socket: socket,
    isAlive: true,
    lastSequence: 0
  };

  attachWebSocketClient(client);
}

function attachWebSocketClient(client) {
  const { socket, roomCode, role, id } = client;
  console.log(`[WS_CONNECT] Connection ${id} joined with role ${role} for room ${roomCode}`);

  const room = roomManager.getRoom(roomCode);
  if (!room) {
    sendWsMessage(client, { type: 'ERROR', payload: { message: 'ROOM_NOT_FOUND' } });
    setTimeout(() => socket.end(), 500);
    return;
  }

  // Register in room
  if (role === 'HOST') {
    if (room.hostConnection && room.hostConnection.socket !== socket) {
      try { room.hostConnection.socket.end(); } catch (_) {}
    }
    room.hostConnection = client;
  } else {
    if (room.viewerConnection && room.viewerConnection.socket !== socket) {
      try { room.viewerConnection.socket.end(); } catch (_) {}
    }
    room.viewerConnection = client;
  }

  // Notify client of joined status
  sendWsMessage(client, {
    type: 'ROOM_JOINED',
    roomCode: roomCode,
    payload: {
      role: role,
      connectionId: id,
      participantCount: (room.hostConnection ? 1 : 0) + (room.viewerConnection ? 1 : 0),
      mediaInfo: room.mediaInfo,
      lastPlaybackState: room.lastPlaybackState
    }
  });

  // Check if both are now connected
  if (room.hostConnection && room.viewerConnection) {
    room.status = 'CONNECTED';
    console.log(`[ROOM_CONNECTED] Room ${roomCode} now has both Host and Viewer`);
    const statusMsg = {
      type: 'USER_JOINED',
      roomCode: roomCode,
      payload: {
        role: role,
        participantCount: 2
      }
    };
    sendWsMessage(room.hostConnection, statusMsg);
    sendWsMessage(room.viewerConnection, statusMsg);
  }

  // Frame parser state
  let buffer = Buffer.alloc(0);

  socket.on('data', (chunk) => {
    buffer = Buffer.concat([buffer, chunk]);
    parseFrames();
  });

  socket.on('close', () => {
    handleDisconnect(client);
  });

  socket.on('error', (err) => {
    console.log(`[WS_ERROR] Connection ${id}: ${err.message}`);
    handleDisconnect(client);
  });

  function parseFrames() {
    while (buffer.length >= 2) {
      const firstByte = buffer[0];
      const secondByte = buffer[1];
      const isFinal = (firstByte & 0x80) !== 0;
      const opcode = firstByte & 0x0f;
      const isMasked = (secondByte & 0x80) !== 0;
      let payloadLength = secondByte & 0x7f;
      let offset = 2;

      if (payloadLength === 126) {
        if (buffer.length < 4) return;
        payloadLength = buffer.readUInt16BE(2);
        offset = 4;
      } else if (payloadLength === 127) {
        if (buffer.length < 10) return;
        payloadLength = Number(buffer.readBigUInt64BE(2));
        offset = 10;
      }

      let maskKey = null;
      if (isMasked) {
        if (buffer.length < offset + 4) return;
        maskKey = buffer.slice(offset, offset + 4);
        offset += 4;
      }

      if (buffer.length < offset + payloadLength) return;

      const rawPayload = buffer.slice(offset, offset + payloadLength);
      buffer = buffer.slice(offset + payloadLength);

      if (isMasked && maskKey) {
        for (let i = 0; i < rawPayload.length; i++) {
          rawPayload[i] ^= maskKey[i % 4];
        }
      }

      if (opcode === 0x8) {
        // Close frame
        socket.end();
        return;
      } else if (opcode === 0x9) {
        // Ping -> Send Pong
        sendRawWsFrame(socket, 0xa, rawPayload);
      } else if (opcode === 0x1) {
        // Text message
        try {
          const text = rawPayload.toString('utf8');
          const msg = JSON.parse(text);
          handleIncomingMessage(client, msg);
        } catch (e) {
          console.error('[WS_PARSE_ERROR]', e.message);
        }
      }
    }
  }
}

function handleIncomingMessage(client, msg) {
  const { roomCode, role } = client;
  const room = roomManager.getRoom(roomCode);
  if (!room) return;

  room.lastActivity = Date.now();
  const msgType = msg.type;
  const sequence = msg.sequence || 0;

  // Stale sequence check for playback events
  if (['PLAY', 'PAUSE', 'SEEK', 'SYNC'].includes(msgType)) {
    if (sequence > 0 && sequence <= client.lastSequence) {
      console.log(`[IGNORE_STALE] Ignored stale seq ${sequence} <= ${client.lastSequence}`);
      return;
    }
    client.lastSequence = sequence;
  }

  // Authoritative host rule: Viewer cannot change playback state
  if (['PLAY', 'PAUSE', 'SEEK'].includes(msgType) && role !== 'HOST') {
    console.log(`[SECURITY] Viewer rejected from broadcasting ${msgType}`);
    return;
  }

  // Route messages between Host and Viewer
  const otherClient = (role === 'HOST') ? room.viewerConnection : room.hostConnection;

  switch (msgType) {
    // Signaling
    case 'WEBRTC_OFFER':
      console.log(`[WEBRTC_OFFER] Host -> Viewer for room ${roomCode}`);
      if (otherClient) sendWsMessage(otherClient, msg);
      break;

    case 'WEBRTC_ANSWER':
      console.log(`[WEBRTC_ANSWER] Viewer -> Host for room ${roomCode}`);
      if (otherClient) sendWsMessage(otherClient, msg);
      break;

    case 'ICE_CANDIDATE':
      if (otherClient) sendWsMessage(otherClient, msg);
      break;

    // Playback sync
    case 'PLAY':
    case 'PAUSE':
    case 'SEEK':
    case 'SYNC':
      if (msg.payload) {
        room.lastPlaybackState = {
          positionMs: msg.payload.positionMs || 0,
          isPlaying: msgType === 'PLAY' || (msgType === 'SYNC' && msg.payload.isPlaying),
          playbackSpeed: msg.payload.playbackSpeed || 1.0,
          sequence: sequence,
          updatedAt: Date.now()
        };
      }
      if (otherClient) sendWsMessage(otherClient, msg);
      break;

    case 'MEDIA_STARTED':
      // Host selected movie: broadcast metadata (never movie bytes)
      room.mediaInfo = {
        name: msg.payload?.name || 'Movie',
        durationMs: msg.payload?.durationMs || 0
      };
      room.status = 'ACTIVE';
      console.log(`[MEDIA_STARTED] Host started movie ${room.mediaInfo.name} in room ${roomCode}`);
      if (otherClient) sendWsMessage(otherClient, msg);
      break;

    case 'PING':
      sendWsMessage(client, { type: 'PONG', timestamp: Date.now() });
      break;

    case 'PONG':
      client.isAlive = true;
      break;

    default:
      console.log(`[WS_UNKNOWN_TYPE] ${msgType}`);
      break;
  }
}

function handleDisconnect(client) {
  const { roomCode, role, id } = client;
  console.log(`[WS_DISCONNECT] Connection ${id} (${role}) left room ${roomCode}`);
  const room = roomManager.getRoom(roomCode);
  if (!room) return;

  if (role === 'HOST') {
    room.hostConnection = null;
    room.status = 'DISCONNECTED';
    // When host disconnects in Mode 1, inform viewer session ended
    if (room.viewerConnection) {
      sendWsMessage(room.viewerConnection, {
        type: 'HOST_DISCONNECTED',
        roomCode: roomCode,
        payload: { message: 'Host ended the session.' }
      });
    }
  } else {
    room.viewerConnection = null;
    // When viewer disconnects, notify host but keep room alive for reconnect
    if (room.hostConnection) {
      sendWsMessage(room.hostConnection, {
        type: 'USER_LEFT',
        roomCode: roomCode,
        payload: { role: 'VIEWER', message: 'Friend disconnected' }
      });
    }
  }
}

function sendWsMessage(client, data) {
  if (!client || !client.socket || client.socket.destroyed) return;
  const json = JSON.stringify(data);
  const payload = Buffer.from(json, 'utf8');
  sendRawWsFrame(client.socket, 0x1, payload);
}

function sendRawWsFrame(socket, opcode, payload) {
  const length = payload.length;
  let header;

  if (length <= 125) {
    header = Buffer.alloc(2);
    header[0] = 0x80 | (opcode & 0x0f);
    header[1] = length; // Unmasked from server
  } else if (length <= 65535) {
    header = Buffer.alloc(4);
    header[0] = 0x80 | (opcode & 0x0f);
    header[1] = 126;
    header.writeUInt16BE(length, 2);
  } else {
    header = Buffer.alloc(10);
    header[0] = 0x80 | (opcode & 0x0f);
    header[1] = 127;
    header.writeBigUInt64BE(BigInt(length), 2);
  }

  try {
    socket.write(Buffer.concat([header, payload]));
  } catch (_) {}
}

// HTTP REST Server
const server = http.createServer((req, res) => {
  const clientIp = req.socket.remoteAddress || '127.0.0.1';

  // CORS headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  const parsedUrl = url.parse(req.url, true);
  const pathname = parsedUrl.pathname;

  // Health check endpoint
  if (req.method === 'GET' && pathname === '/health') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ status: 'OK', rooms: roomManager.rooms.size }));
    return;
  }

  // Rate limit check for API
  if (!rateLimiter.isAllowed(clientIp, 60, 60000)) {
    res.writeHead(429, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: 'RATE_LIMIT_EXCEEDED', message: 'Too many requests. Please try again later.' }));
    return;
  }

  // POST /api/rooms -> Create Room
  if (req.method === 'POST' && pathname === '/api/rooms') {
    const room = roomManager.createRoom();
    res.writeHead(201, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      roomId: room.id,
      roomCode: room.code,
      role: 'HOST',
      expiresAt: new Date(room.expiresAt).toISOString()
    }));
    return;
  }

  // POST /api/rooms/:code/join -> Join Room
  const joinMatch = pathname.match(/^\/api\/rooms\/([A-Za-z0-9]+)\/join$/);
  if (req.method === 'POST' && joinMatch) {
    const code = joinMatch[1];
    const result = roomManager.joinRoom(code);
    if (!result.success) {
      res.writeHead(result.status, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: result.error }));
      return;
    }
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      roomId: result.room.id,
      roomCode: result.room.code,
      role: 'VIEWER',
      expiresAt: new Date(result.room.expiresAt).toISOString()
    }));
    return;
  }

  // GET /api/rooms/:code -> Get Room Status
  const getMatch = pathname.match(/^\/api\/rooms\/([A-Za-z0-9]+)$/);
  if (req.method === 'GET' && getMatch) {
    const code = getMatch[1];
    const room = roomManager.getRoom(code);
    if (!room) {
      res.writeHead(404, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ error: 'ROOM_NOT_FOUND' }));
      return;
    }
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      roomCode: room.code,
      status: room.status,
      participantCount: (room.hostConnection ? 1 : 0) + (room.viewerConnection ? 1 : 0),
      expiresAt: new Date(room.expiresAt).toISOString()
    }));
    return;
  }

  // DELETE /api/rooms/:code -> Close Room
  const deleteMatch = pathname.match(/^\/api\/rooms\/([A-Za-z0-9]+)$/);
  if (req.method === 'DELETE' && deleteMatch) {
    const code = deleteMatch[1];
    roomManager.removeRoom(code);
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ message: 'ROOM_CLOSED' }));
    return;
  }

  // Default 404
  res.writeHead(404, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: 'NOT_FOUND' }));
});

// Upgrade HTTP to WebSocket
server.on('upgrade', (req, socket, head) => {
  const parsedUrl = url.parse(req.url, true);
  if (parsedUrl.pathname === '/ws') {
    handleWebSocketUpgrade(req, socket, head);
  } else {
    socket.destroy();
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`WatchTogether backend running on port ${PORT}`);
});
