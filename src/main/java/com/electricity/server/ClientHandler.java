package com.electricity.server;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.electricity.db.DBConnection;
// import com.electricity.service.ElectionManager; // Removed

public class ClientHandler implements Runnable {

    private Socket clientSocket;
    private int clientNumber;
    private String nodeId;
    private PrintWriter out;
    private static final java.util.Map<String, ClientHandler> activeHandlers = new java.util.concurrent.ConcurrentHashMap<>();
    private static final DateTimeFormatter DATETIME_PARSER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public ClientHandler(Socket socket, int clientNumber) {
        this.clientSocket = socket;
        this.clientNumber = clientNumber;
    }

    public static ClientHandler getHandler(String nodeId) {
        return activeHandlers.get(nodeId);
    }

    public void sendMessage(String msg) {
        if (out != null) {
            out.println(msg);
        }
    }

    @Override
    public void run() {
        try {
            this.out = new PrintWriter(clientSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            // 1. SECURITY: Authentication handshake
            String authLine = in.readLine();

            if (authLine == null || !authLine.equals("AUTH|GRID_SEC_2025")) {
                // For now, only accept Grid Security Token
                System.out.println("[Client #" + clientNumber + "] Authentication Failed. Closing.");
                out.println("ERR|AUTH_FAILED");
                return; // Close connection
            } else {
                out.println("AUTH_OK");
                System.out.println("[Client #" + clientNumber + "] Authenticated successfully.");
            }

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[Client #" + clientNumber + "] Received: " + line);
                String response = handleMessage(line);
                out.println(response);
            }
        } catch (Throwable e) {
            System.err.println("[Client #" + clientNumber + "] Error: " + e.getMessage());
        } finally {
            if (nodeId != null) {
                activeHandlers.remove(nodeId);
                updateNodeStatus(nodeId, "OFFLINE");
            }
            try {
                clientSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void updateNodeStatus(String id, String status) {
        try (Connection conn = DBConnection.getConnection()) {
            String sql = "UPDATE nodes SET status = ? WHERE node_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, status);
                ps.setString(2, id);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            System.err.println("Failed to update status for " + id + ": " + e.getMessage());
        }
    }

    private String handleMessage(String msg) {
        // Simple pipe-separated protocol
        // REPORT|nodeId|voltage|powerState
        String[] parts = msg.split("\\|", -1);
        if (parts.length == 0)
            return "ERR|Empty";

        String type = parts[0].trim().toUpperCase();
        try {
            switch (type) {
                case "REPORT":
                    return handleReport(parts);
                case "OUTAGE":
                    return handleOutage(parts);
                // Legacy support if needed, but REPORT is new standard
                case "CONFIRM_RESOLVED":
                    return handleConfirmation(parts);
                default:
                    return "ERR|UnknownType";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "ERR|Exception|" + e.getMessage();
        }
    }

    private String handleConfirmation(String[] p) {
        if (p.length < 2)
            return "ERR|CONFIRM|BadFormat";
        String nodeId = p[1];
        System.out.println("[HQ] OUTAGE RESOLVED CONFIRMATION received from " + nodeId);
        try (Connection conn = DBConnection.getConnection()) {
            String update = "UPDATE nodes SET status='ONLINE', last_power_state='NORMAL', last_seen=NOW() WHERE node_id=?";
            try (PreparedStatement ps = conn.prepareStatement(update)) {
                ps.setString(1, nodeId);
                ps.executeUpdate();
            }
            return "OK|STATUS_RESTORED";
        } catch (SQLException e) {
            return "ERR|DB|" + e.getMessage();
        }
    }

    private String handleReport(String[] p) {
        // REPORT|NodeId(DistrictID)|Voltage|PowerState|Region
        if (p.length < 4)
            return "ERR|RPT|BadFormat";

        String nodeId = p[1];
        if (this.nodeId == null || !this.nodeId.equals(nodeId)) {
            if (this.nodeId != null)
                activeHandlers.remove(this.nodeId);
            this.nodeId = nodeId;
            activeHandlers.put(nodeId, this);
        }
        double voltage = Double.parseDouble(p[2]);
        String powerState = p[3];
        String region = (p.length > 4) ? p[4] : "Unknown";

        // Storing Voltage AND Region in 'transformer_health' column for display
        // Format: "220.5V | Addis Ababa"
        String displayInfo = String.format("%.1fV | %s", voltage, region);

        long logicalTime = System.currentTimeMillis();

        System.out.println("[HQ] Report from " + nodeId + " (" + region + "): " + voltage + "V, " + powerState);

        // Determine status based on power state
        String status = "ONLINE";
        if ("OUTAGE".equalsIgnoreCase(powerState) || "OFF".equalsIgnoreCase(powerState)
                || "DOWN".equalsIgnoreCase(powerState)) {
            status = "OFFLINE";
        }

        try (Connection conn = DBConnection.getConnection()) {
            String upsert = "INSERT INTO nodes (node_id, region, last_seen, last_power_state, last_load_percent, transformer_health, status) "
                    +
                    "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE region=VALUES(region), last_seen=VALUES(last_seen), last_power_state=VALUES(last_power_state), "
                    +
                    "transformer_health=VALUES(transformer_health), status=VALUES(status)";

            try (PreparedStatement ps = conn.prepareStatement(upsert)) {
                Timestamp now = new Timestamp(logicalTime);
                ps.setString(1, nodeId);
                ps.setString(2, region);
                ps.setTimestamp(3, now);
                ps.setString(4, powerState);
                ps.setInt(5, 0);
                ps.setString(6, displayInfo); // Stores Voltage | Region
                ps.setString(7, status); // Status (ONLINE/OFFLINE)
                ps.executeUpdate();
            }
            return "OK|ACK_REPORT";
        } catch (SQLException e) {
            System.err.println("Database Write Error: " + e.getMessage());
            return "ERR|DB_ERROR";
        }
    }

    private String handleReportLegacy(String[] p) {
        // Fallback for old clients if any
        if (p.length < 5)
            return "ERR|HB|BadFormat";
        String nodeId = p[1];
        // p[2] = powerState, p[3] = load
        String msg = String.format("REPORT|%s|220.0|%s", nodeId, p[2]);
        return handleReport(msg.split("\\|"));
    }

    private String handleOutage(String[] p) {
        if (p.length < 6)
            return "ERR|OUTAGE|BadFormat";
        // OUTAGE|eventId|nodeId|eventType|timestamp|meta
        // Just log and store. Server is authority.
        String eventId = p[1];
        String nodeId = p[2];
        String type = p[3];

        System.out.println("[Central Authority] ALERT: District " + nodeId + " reported " + type);

        try (Connection conn = DBConnection.getConnection()) {
            // Logic simplified: Just insert event
            String insertEvent = "INSERT INTO events (event_id, node_id, event_type, timestamp, metadata) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertEvent)) {
                ps.setString(1, eventId);
                ps.setString(2, nodeId);
                ps.setString(3, type);
                ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
                ps.setString(5, p[5]);
                ps.executeUpdate();
            }

            // Update node state
            if (type.contains("START"))
                updateNodeState(conn, nodeId, "OFF");
            else if (type.contains("END"))
                updateNodeState(conn, nodeId, "NORMAL");

            return "OK|ACK_OUTAGE";
        } catch (SQLException e) {
            return "ERR|DB|" + e.getMessage();
        }
    }

    private void updateNodeState(Connection conn, String nodeId, String powerState) throws SQLException {
        String update = "UPDATE nodes SET last_power_state = ?, last_seen = NOW() WHERE node_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(update)) {
            ps.setString(1, powerState);
            ps.setString(2, nodeId);
            ps.executeUpdate();
        }
    }
}
