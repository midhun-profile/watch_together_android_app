-- WatchTogether Mode 1 PostgreSQL Database Schema
-- Stores minimal non-sensitive room session lifecycle state.
-- NEVER stores movie files, video bytes, passwords, or personal accounts.

CREATE TABLE IF NOT EXISTS rooms (
    id VARCHAR(36) PRIMARY KEY,
    room_code VARCHAR(10) UNIQUE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING', -- WAITING, CONNECTED, ACTIVE, DISCONNECTED, EXPIRED
    host_connected BOOLEAN DEFAULT FALSE,
    viewer_connected BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    last_activity TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_rooms_code ON rooms(room_code);
CREATE INDEX IF NOT EXISTS idx_rooms_expires_at ON rooms(expires_at);
CREATE INDEX IF NOT EXISTS idx_rooms_status ON rooms(status);
