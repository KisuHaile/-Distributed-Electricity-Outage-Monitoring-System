CREATE DATABASE IF NOT EXISTS electricity;

USE electricity;

DROP TABLE IF EXISTS events;

DROP TABLE IF EXISTS nodes;

CREATE TABLE IF NOT EXISTS nodes (
    node_id VARCHAR(100) PRIMARY KEY,
    region VARCHAR(100),
    last_seen TIMESTAMP NULL,
    last_power_state VARCHAR(10),
    last_load_percent INT,
    transformer_health VARCHAR(100),
    status VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS events (
    event_id VARCHAR(100) PRIMARY KEY,
    node_id VARCHAR(100),
    event_type VARCHAR(50),
    timestamp DATETIME,
    metadata TEXT,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (node_id) REFERENCES nodes (node_id) ON DELETE SET NULL
);

-- Optional: simple index for queries
CREATE INDEX idx_events_node ON events (node_id);