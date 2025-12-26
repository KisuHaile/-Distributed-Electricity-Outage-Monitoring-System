CREATE DATABASE IF NOT EXISTS electricity;

USE electricity;

CREATE TABLE IF NOT EXISTS nodes (
    node_id VARCHAR(255) PRIMARY KEY,
    region VARCHAR(255),
    last_seen TIMESTAMP NULL,
    last_power_state VARCHAR(20),
    last_load_percent INT,
    transformer_health VARCHAR(255),
    status VARCHAR(50),
    verification_status VARCHAR(20) DEFAULT 'NONE',
    verification_ts TIMESTAMP NULL,
    logical_timestamp BIGINT DEFAULT 0
);

INSERT IGNORE INTO
    nodes (node_id, region, status)
VALUES (
        'DISTRIBUTOR_003',
        'West Addis Ababa',
        'OFFLINE'
    );

INSERT IGNORE INTO
    nodes (node_id, region, status)
VALUES (
        'DISTRIBUTOR_001',
        'East Addis Ababa',
        'OFFLINE'
    );

INSERT IGNORE INTO
    nodes (node_id, region, status)
VALUES (
        'DISTRIBUTOR_002',
        'North Addis Ababa',
        'OFFLINE'
    );

INSERT IGNORE INTO
    nodes (node_id, region, status)
VALUES (
        'DISTRIBUTOR_00',
        'South Addis Ababa',
        'OFFLINE'
    );

CREATE TABLE IF NOT EXISTS events (
    event_id VARCHAR(100) PRIMARY KEY,
    node_id VARCHAR(100),
    event_type VARCHAR(50),
    timestamp DATETIME,
    logical_timestamp BIGINT DEFAULT 0,
    metadata TEXT,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Optional: simple index for queries
CREATE INDEX idx_events_node ON events (node_id);