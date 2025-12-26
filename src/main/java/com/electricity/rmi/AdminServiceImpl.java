package com.electricity.rmi;

import com.electricity.db.DBConnection;
import com.electricity.db.EventLogger;
import com.electricity.server.HeadlessServer;
import com.electricity.server.ClientHandler;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.*;
import java.util.*;

/**
 * Lab 2: RMI Admin Service Implementation
 * 
 * This class implements the AdminService interface and provides
 * remote access to system monitoring and control functions.
 */
public class AdminServiceImpl extends UnicastRemoteObject implements AdminService {

    private static final long serialVersionUID = 1L;
    private long startTime;
    private long messageCount = 0;

    public AdminServiceImpl() throws RemoteException {
        super();
        this.startTime = System.currentTimeMillis();
    }

    @Override
    public List<Map<String, Object>> getAllNodes() throws RemoteException {
        List<Map<String, Object>> nodes = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT node_id, region, last_seen, last_power_state, " +
                                "transformer_health, status, verification_status, logical_timestamp " +
                                "FROM nodes ORDER BY node_id")) {

            while (rs.next()) {
                Map<String, Object> node = new HashMap<>();
                node.put("nodeId", rs.getString("node_id"));
                node.put("region", rs.getString("region"));
                node.put("lastSeen", rs.getTimestamp("last_seen"));
                node.put("powerState", rs.getString("last_power_state"));
                node.put("health", rs.getString("transformer_health"));
                node.put("status", rs.getString("status"));
                node.put("verificationStatus", rs.getString("verification_status"));
                node.put("logicalTimestamp", rs.getLong("logical_timestamp"));
                nodes.add(node);
            }

        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }

        return nodes;
    }

    @Override
    public Map<String, Object> getNodeDetails(String nodeId) throws RemoteException {
        Map<String, Object> details = new HashMap<>();

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT * FROM nodes WHERE node_id = ?")) {

            ps.setString(1, nodeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    details.put("nodeId", rs.getString("node_id"));
                    details.put("region", rs.getString("region"));
                    details.put("lastSeen", rs.getTimestamp("last_seen"));
                    details.put("powerState", rs.getString("last_power_state"));
                    details.put("loadPercent", rs.getInt("last_load_percent"));
                    details.put("health", rs.getString("transformer_health"));
                    details.put("status", rs.getString("status"));
                    details.put("verificationStatus", rs.getString("verification_status"));
                    details.put("verificationTime", rs.getTimestamp("verification_ts"));
                    details.put("logicalTimestamp", rs.getLong("logical_timestamp"));
                } else {
                    throw new RemoteException("Node not found: " + nodeId);
                }
            }

        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }

        return details;
    }

    @Override
    public List<Map<String, Object>> getRecentEvents(int limit) throws RemoteException {
        List<Map<String, Object>> events = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT event_id, node_id, event_type, timestamp, logical_timestamp, metadata " +
                                "FROM events ORDER BY logical_timestamp DESC LIMIT ?")) {

            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> event = new HashMap<>();
                    event.put("eventId", rs.getString("event_id"));
                    event.put("nodeId", rs.getString("node_id"));
                    event.put("eventType", rs.getString("event_type"));
                    event.put("timestamp", rs.getTimestamp("timestamp"));
                    event.put("logicalTimestamp", rs.getLong("logical_timestamp"));
                    event.put("metadata", rs.getString("metadata"));
                    events.add(event);
                }
            }

        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }

        return events;
    }

    @Override
    public List<Map<String, Object>> getNodeEvents(String nodeId, int limit) throws RemoteException {
        List<Map<String, Object>> events = new ArrayList<>();

        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT event_id, node_id, event_type, timestamp, logical_timestamp, metadata " +
                                "FROM events WHERE node_id = ? ORDER BY logical_timestamp DESC LIMIT ?")) {

            ps.setString(1, nodeId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> event = new HashMap<>();
                    event.put("eventId", rs.getString("event_id"));
                    event.put("nodeId", rs.getString("node_id"));
                    event.put("eventType", rs.getString("event_type"));
                    event.put("timestamp", rs.getTimestamp("timestamp"));
                    event.put("logicalTimestamp", rs.getLong("logical_timestamp"));
                    event.put("metadata", rs.getString("metadata"));
                    events.add(event);
                }
            }

        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }

        return events;
    }

    @Override
    public String triggerVerification(String nodeId) throws RemoteException {
        try {
            // Update database
            try (Connection conn = DBConnection.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "UPDATE nodes SET verification_status='PENDING', verification_ts=NOW() WHERE node_id=?")) {

                ps.setString(1, nodeId);
                int updated = ps.executeUpdate();

                if (updated == 0) {
                    throw new RemoteException("Node not found: " + nodeId);
                }
            }

            // Try to send verification command to client
            ClientHandler handler = ClientHandler.getHandler(nodeId);
            if (handler != null) {
                handler.sendMessage("SOLVED_CHECK");
            } else {
                // Queue for web simulator
                com.electricity.server.web.SimpleWebServer.queueCommand(nodeId, "SOLVED_CHECK");
            }

            // Log event
            long lamportTime = HeadlessServer.getClock().tick();
            EventLogger.logEvent(nodeId, "MANUAL_VERIFY", "RMI Admin triggered verification", lamportTime);

            // Broadcast to peers
            HeadlessServer.broadcastSync("VERIFY_RELAY|" + nodeId);

            messageCount++;
            return "Verification triggered for " + nodeId;

        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> getClusterStatus() throws RemoteException {
        Map<String, Object> status = new HashMap<>();

        status.put("serverId", HeadlessServer.getServerId());
        status.put("serverPort", HeadlessServer.getServerPort());
        status.put("isLeader", HeadlessServer.isLeader());
        status.put("logicalTime", HeadlessServer.getClock().getTime());

        // Count active nodes
        try (Connection conn = DBConnection.getConnection();
                Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM nodes WHERE status='ONLINE'")) {
                if (rs.next()) {
                    status.put("onlineNodes", rs.getInt(1));
                }
            }

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM nodes WHERE status='OFFLINE'")) {
                if (rs.next()) {
                    status.put("offlineNodes", rs.getInt(1));
                }
            }

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM nodes WHERE status='OUTAGE'")) {
                if (rs.next()) {
                    status.put("outageNodes", rs.getInt(1));
                }
            }

        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }

        return status;
    }

    @Override
    public Map<String, Object> getServerStats() throws RemoteException {
        Map<String, Object> stats = new HashMap<>();

        long uptime = System.currentTimeMillis() - startTime;
        stats.put("uptimeMs", uptime);
        stats.put("uptimeMinutes", uptime / 60000);
        stats.put("messageCount", messageCount);
        stats.put("logicalTime", HeadlessServer.getClock().getTime());
        stats.put("serverId", HeadlessServer.getServerId());
        stats.put("isLeader", HeadlessServer.isLeader());

        return stats;
    }

    @Override
    public String markNodeResolved(String nodeId, String operatorName) throws RemoteException {
        try (Connection conn = DBConnection.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "UPDATE nodes SET status='ONLINE', last_power_state='NORMAL', " +
                                "verification_status='CONFIRMED' WHERE node_id=?")) {

            ps.setString(1, nodeId);
            int updated = ps.executeUpdate();

            if (updated == 0) {
                throw new RemoteException("Node not found: " + nodeId);
            }

            // Log event
            long lamportTime = HeadlessServer.getClock().tick();
            EventLogger.logEvent(nodeId, "MANUAL_RESTORE_CONFIRMED",
                    "Resolved by operator: " + operatorName + " via RMI", lamportTime);

            // Broadcast to peers
            HeadlessServer.broadcastSync("CONFIRM_RESOLVED|" + nodeId);

            messageCount++;
            return "Node " + nodeId + " marked as resolved by " + operatorName;

        } catch (SQLException e) {
            throw new RemoteException("Database error: " + e.getMessage(), e);
        }
    }

    @Override
    public long getCurrentLogicalTime() throws RemoteException {
        return HeadlessServer.getClock().getTime();
    }

    @Override
    public boolean isLeader() throws RemoteException {
        return HeadlessServer.isLeader();
    }

    @Override
    public int getServerId() throws RemoteException {
        return HeadlessServer.getServerId();
    }
}
