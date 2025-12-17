package com.electricity.model;

/**
 * CORE CONCEPT: Node State Machine
 * 
 * Demonstrates failure detection with uncertainty:
 * ONLINE → SUSPECTED → OFFLINE → RECOVERED
 * 
 * This models real-world distributed systems where:
 * - Failures are detected over time (not instantaneous)
 * - Network partitions create uncertainty
 * - Nodes can recover
 */
public enum NodeState {
    /**
     * Node is sending regular heartbeats
     * Last heartbeat < SUSPECT_THRESHOLD
     */
    ONLINE,

    /**
     * Node missed one heartbeat interval
     * Last heartbeat > SUSPECT_THRESHOLD but < OFFLINE_THRESHOLD
     * 
     * Key concept: We don't declare failure immediately!
     */
    SUSPECTED,

    /**
     * Node has not sent heartbeat for extended period
     * Last heartbeat > OFFLINE_THRESHOLD
     * 
     * Considered failed
     */
    OFFLINE,

    /**
     * Node was OFFLINE but came back online
     * Useful for tracking recovery patterns
     */
    RECOVERED;

    // Thresholds (in seconds)
    public static final int SUSPECT_THRESHOLD = 15; // 1 missed heartbeat
    public static final int OFFLINE_THRESHOLD = 30; // 2 missed heartbeats

    /**
     * Determine state based on time since last heartbeat
     */
    public static NodeState fromSecondsSinceHeartbeat(long seconds, NodeState currentState) {
        if (seconds <= SUSPECT_THRESHOLD) {
            // Receiving heartbeats - either ONLINE or RECOVERED
            if (currentState == NodeState.OFFLINE) {
                return NodeState.RECOVERED; // Was dead, now alive!
            }
            return NodeState.ONLINE;
        } else if (seconds <= OFFLINE_THRESHOLD) {
            // In gray area - SUSPECTED
            return NodeState.SUSPECTED;
        } else {
            // Too long - OFFLINE
            return NodeState.OFFLINE;
        }
    }

    /**
     * Get display color for GUI
     */
    public String getColor() {
        switch (this) {
            case ONLINE:
                return "#2ecc71"; // Green
            case SUSPECTED:
                return "#f39c12"; // Orange
            case OFFLINE:
                return "#e74c3c"; // Red
            case RECOVERED:
                return "#3498db"; // Blue
            default:
                return "#95a5a6"; // Gray
        }
    }

    /**
     * Human-readable explanation
     */
    public String getDescription() {
        switch (this) {
            case ONLINE:
                return "Receiving heartbeats";
            case SUSPECTED:
                return "Missed heartbeat - uncertain";
            case OFFLINE:
                return "Confirmed failure";
            case RECOVERED:
                return "Recovered from failure";
            default:
                return "Unknown";
        }
    }
}
