package com.electricity.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.UUID;

public class EventLogger {

    public static void logEvent(String nodeId, String eventType, String metadata, long logicalTimestamp) {
        String eventId = "EVT_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        try (Connection conn = DBConnection.getConnection()) {
            String sql = "INSERT INTO events (event_id, node_id, event_type, timestamp, logical_timestamp, metadata) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, eventId);
                ps.setString(2, nodeId);
                ps.setString(3, eventType);
                ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
                ps.setLong(5, logicalTimestamp);
                ps.setString(6, metadata);
                ps.executeUpdate();
                System.out.println("[Event-Logger] SUCCESS: Logged " + eventType + " for " + nodeId + " [LT="
                        + logicalTimestamp + "]");
            }
        } catch (Exception e) {
            System.err.println("[Event-Logger] ERROR: Failed to log event for " + nodeId + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Overload for backward compatibility (uses timestamp 0)
    public static void logEvent(String nodeId, String eventType, String metadata) {
        logEvent(nodeId, eventType, metadata, 0);
    }

    public static String determineEventType(String newPower, String oldPower) {
        // Handle nulls
        if (newPower == null)
            return null;
        if (oldPower == null)
            oldPower = "UNKNOWN";

        if (newPower.equalsIgnoreCase(oldPower))
            return null;

        String np = newPower.toUpperCase();
        String op = oldPower.toUpperCase();

        if (np.equals("OUTAGE") || np.equals("OFF"))
            return "OUTAGE_START";
        if (np.equals("NORMAL") && (op.equals("OUTAGE") || op.equals("OFF")))
            return "OUTAGE_END";
        if (np.contains("LOW"))
            return "POWER_QUALITY_ISSUE";
        if (np.equals("NORMAL"))
            return "POWER_RESTORED";

        return "STATE_CHANGE";
    }
}
