# WatchTogether Backend Setup & Deployment Guide

## Prerequisites
- Node.js 18+ or Docker
- Optional: PostgreSQL 14+ (if persistent DB is desired; server includes built-in fast in-memory room store)

## Quick Start (Local Development)

1. Navigate to the backend directory:
```bash
cd backend
npm install
```

2. Run the server:
```bash
npm start
```

By default, the server listens on `http://0.0.0.0:8080` with WebSocket endpoint at `ws://0.0.0.0:8080/ws`.

3. Run smoke tests:
```bash
npm test
```

## Environment Variables

| Variable | Description | Default |
|---|---|---|
| `PORT` | Server listening port | `8080` |
| `ROOM_TTL_HOURS` | Room lifespan in hours before auto-purge | `24` |
| `DATABASE_URL` | PostgreSQL connection string (optional) | `""` |
| `STUN_SERVER` | Primary STUN server URL | `stun:stun.l.google.com:19302` |
| `TURN_SERVER` | TURN server URL for NAT traversal | `""` |
| `TURN_USERNAME` | TURN authentication username | `""` |
| `TURN_CREDENTIAL` | TURN authentication credential | `""` |

## Production Deployment (Docker)

```dockerfile
FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production
COPY . .
EXPOSE 8080
CMD ["node", "src/server.js"]
```

## Database Initialization
If running with PostgreSQL, initialize the schema:
```bash
psql -d watchtogether -f src/database/schema.sql
```
