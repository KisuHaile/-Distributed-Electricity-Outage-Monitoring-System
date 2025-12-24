package com.electricity.server;

import java.io.*;
import java.net.*;
import java.sql.*;
import com.electricity.db.DBConnection;

public class ClientHandler implements Runnable {

    private Socket clientSocket;
    private int clientNumber;
    private String nodeId;
    private PrintWriter out;
    private boolean isServerPeer = false;
    private static final java.util.Map<String, ClientHandler> activeHandlers = new java.util.concurrent.ConcurrentHashMap<>();

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

            if (authLine != null && authLine.equals("AUTH|SERVER_2025")) {
                this.isServerPeer = true;
                handleServerSession(in);
                return;
            }

            if (authLine == null || !authLine.equals("AUTH|GRID_SEC_2025")) {
                // For now, only accept Grid Security Token
                System.out.println("[Client #" + clientNumber + "] Authentication Failed. Closing.");
                out.println("ERR|AUTH_FAILED");
                return; // Close connection
            } else {
                out.println("AUTH_OK");
                System.out.println("[Client #" + clientNumber + "] Authenticated successfully. Monitoring active.");
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

    private void handleServerSession(BufferedReader in) throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            if (line.startsWith("SYNC|")) {
                String sub = line.substring(5);
                System.out.println("[Sync-In] Processing sync message: " + sub);
                handleMessage(sub);
            }
        }
    }

    private void updateNodeStatus(String id, String status) {
        System.out.println("[HQ] Local status update for " + id + " to " + status);
        try (Connection conn = DBConnection.getConnection()) {
            String sql = "UPDATE nodes SET status = ? WHERE node_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, status);
                ps.setString(2, id);
                ps.executeUpdate();
            }
            // Sync this connection status change to other servers
            if (!isServerPeer) {
                HeadlessServer.broadcastSync("STATUS|" + id + "|" + status);
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
                case "JOIN":
                    // JOIN|ServerId|IP|Port
                    int sid = Integer.parseInt(parts[1]);
                    String sip = parts[2];
                    int sport = Integer.parseInt(parts[3]);

                    if ("0".equals(sip) || "localhost".equals(sip)) {
                        sip = clientSocket.getInetAddress().getHostAddress();
                    }

                    System.out
                            .println("[Handshake] Received JOIN from Server #" + sid + " (" + sip + ":" + sport + ")");
                    // Register and trigger full sync back
                    HeadlessServer.registerManualPeer(sid, sip, sport, true);
                    return "OK|JOINED";
                case "REPORT":
                    String res = handleReport(parts);
                    if (!isServerPeer && res.startsWith("OK"))
                        HeadlessServer.broadcastSync(msg);
                    return res;
                case "OUTAGE":
                    String res2 = handleOutage(parts);
                    if (!isServerPeer && res2.startsWith("OK"))
                        HeadlessServer.broadcastSync(msg);
                    return res2;
                case "CONFIRM_RESOLVED":
                    String res3 = handleConfirmation(parts);
                    if (!isServerPeer && res3.startsWith("OK"))
                        HeadlessServer.broadcastSync(msg);
                    return res3;
                case "STATUS":
                    if (parts.length >= 3) {
                        updateNodeStatus(parts[1], parts[2]);
                    }
                    return "OK";
                case "VERIFY_RELAY":
                    if (parts.length >= 2) {
                        String vid = parts[1];
                        ClientHandler vh = getHandler(vid);
                        if (vh != null) {
                            vh.sendMessage("SOLVED_CHECK");
                            System.out.println("[Sync-In] Relay: Sent SOLVED_CHECK to local TCP client " + vid);
                        } else {
                            // Also queue for web simulator in case it's polling THIS server
                            com.electricity.server.web.SimpleWebServer.queueCommand(vid, "SOLVED_CHECK");
                        }
                    }
                    return "OK";
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
        System.out.println("[Sync-In] >>> CONFIRMATION RECEIVED for District " + nodeId);
        try (Connection conn = DBConnection.getConnection()) {
            String update = "UPDATE nodes SET status='ONLINE', last_power_state='NORMAL', verification_status='CONFIRMED', last_seen=NOW() WHERE node_id=?";
            try (PreparedStatement ps = conn.prepareStatement(update)) {
                ps.setString(1, nodeId);
                int count = ps.executeUpdate();
                if (count > 0) {
                    System.out.println("[Sync-DB] District " + nodeId + " status RESTORED to ONLINE via confirmation.");
                    com.electricity.db.EventLogger.logEvent(nodeId, "MANUAL_RESTORE_CONFIRMED",
                            "Admin/Client confirmed grid restoration.");
                } else {
                    System.out.println("[Sync-DB] WARNING: No node found with ID " + nodeId + " to confirm.");
                }
            }
            return "OK|STATUS_RESTORED";
        } catch (SQLException e) {
            return "ERR|DB|" + e.getMessage();
        }
    }

    private String handleReport(String[] p) {
        // REPORT|NodeId|Voltage|PowerState|Region
        if (p.length < 4)
            return "ERR|RPT|BadFormat";

        String incomingNodeId = p[1];
        double voltage = Double.parseDouble(p[2]);
        String powerState = p[3];
        String region = (p.length > 4) ? p[4] : "Unknown";

        if (!isServerPeer) {
            // Check if node is authorized (ID and Region must match)
            try (Connection conn = DBConnection.getConnection();
                    PreparedStatement ps = conn
                            .prepareStatement("SELECT 1 FROM nodes WHERE node_id = ? AND region = ?")) {
                ps.setString(1, incomingNodeId);
                ps.setString(2, region);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        System.out.println("[Security] Rejected node: ID=" + incomingNodeId + ", Region=" + region);
                        return "ERR|UNAUTHORIZED_CREDENTIALS";
                    }
                }
            } catch (SQLException e) {
                return "ERR|DB_VERIFY_FAILED";
            }

            if (this.nodeId == null || !this.nodeId.equals(incomingNodeId)) {
                if (this.nodeId != null)
                    activeHandlers.remove(this.nodeId);
                this.nodeId = incomingNodeId;
                activeHandlers.put(incomingNodeId, this);
            }
        }

        String displayInfo = String.format("%.1fV | %s", voltage, region);
        long logicalTime = System.currentTimeMillis();

        System.out.println("[HQ] Report from " + incomingNodeId + " (" + region + "): " + voltage + "V, " + powerState);

        String statusToUpdate = "ONLINE";
        if ("OFFLINE".equalsIgnoreCase(powerState)) {
            statusToUpdate = "OFFLINE";
        }

        try (Connection conn = DBConnection.getConnection()) {
            String currentStatus = "ONLINE";
            String oldPowerState = "UNKNOWN";
            try (PreparedStatement psCheck = conn
                    .prepareStatement("SELECT status, last_power_state FROM nodes WHERE node_id = ?")) {
                psCheck.setString(1, incomingNodeId);
                try (ResultSet rs = psCheck.executeQuery()) {
                    if (rs.next()) {
                        currentStatus = rs.getString("status");
                        oldPowerState = rs.getString("last_power_state");
                    }
                }
            }

            // Detect Power State Change for Event Logging
            String eventType = com.electricity.db.EventLogger.determineEventType(powerState, oldPowerState);
            if (eventType != null) {
                String metadata = String.format("Voltage: %.1fV, Previous: %s, Region: %s", voltage, oldPowerState,
                        region);
                com.electricity.db.EventLogger.logEvent(incomingNodeId, eventType, metadata);
            }

            if ("ONLINE".equals(statusToUpdate) && ("OUTAGE".equals(currentStatus) || "ISSUE".equals(currentStatus))) {
                statusToUpdate = currentStatus;
            }

            String update = "UPDATE nodes SET region=?, last_seen=?, last_power_state=?, transformer_health=?, status=? "
                    + "WHERE node_id=?";

            try (PreparedStatement ps = conn.prepareStatement(update)) {
                Timestamp now = new Timestamp(logicalTime);
                ps.setString(1, region);
                ps.setTimestamp(2, now);
                ps.setString(3, powerState);
                ps.setString(4, displayInfo);
                ps.setString(5, statusToUpdate);
                ps.setString(6, incomingNodeId);

                int rows = ps.executeUpdate();
                if (isServerPeer) {
                    System.out.println(
                            "[Sync-DB] Successfully updated " + incomingNodeId + " (Rows affected: " + rows + ")");
                }
                return "OK|ACK_REPORT";
            }
        } catch (java.sql.SQLException e) {
            System.err.println("[DB-ERROR] Failed to save sync data for " + incomingNodeId + ": " + e.getMessage());
            return "ERR|DB_ERROR";
        }
    }

    private String handleOutage(String[] p) {
        if (p.length < 6)
            return "ERR|OUTAGE|BadFormat";
        // String eventId = p[1]; // Unused
        String incomingNodeId = p[2];
        String type = p[3];

        if (!isServerPeer) {
            try (Connection conn = DBConnection.getConnection();
                    PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM nodes WHERE node_id = ?")) {
                ps.setString(1, incomingNodeId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        System.out.println("[Security] Rejected OUTAGE from unauthorized node: " + incomingNodeId);
                        return "ERR|UNAUTHORIZED_NODE";
                    }
                }
            } catch (SQLException e) {
                return "ERR|DB_VERIFY_FAILED";
            }
        }

        System.out.println("[Central Authority] ALERT: District " + incomingNodeId + " reported " + type);

        try (Connection conn = DBConnection.getConnection()) {
            // Log via Central Logger
            com.electricity.db.EventLogger.logEvent(incomingNodeId, type, "Explicit Outage Report: " + p[5]);

            if (type.contains("START"))
                updateNodeState(conn, incomingNodeId, "OFFLINE");
            else if (type.contains("END"))
                updateNodeState(conn, incomingNodeId, "NORMAL");

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
