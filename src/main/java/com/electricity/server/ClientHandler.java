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
            String update = "UPDATE nodes SET status='ONLINE', last_power_state='NORMAL', last_seen=NOW() WHERE node_id=?";
            try (PreparedStatement ps = conn.prepareStatement(update)) {
                ps.setString(1, nodeId);
                int count = ps.executeUpdate();
                if (count > 0) {
                    System.out.println("[Sync-DB] District " + nodeId + " status RESTORED to ONLINE via confirmation.");
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
        // REPORT|NodeId(DistrictID)|Voltage|PowerState|Region
        if (p.length < 4)
            return "ERR|RPT|BadFormat";

        String nodeId = p[1];
        if (!isServerPeer) {
            if (this.nodeId == null || !this.nodeId.equals(nodeId)) {
                if (this.nodeId != null)
                    activeHandlers.remove(this.nodeId);
                this.nodeId = nodeId;
                activeHandlers.put(nodeId, this);
            }
        }
        double voltage = Double.parseDouble(p[2]);
        String powerState = p[3];
        String region = (p.length > 4) ? p[4] : "Unknown";

        // Storing Voltage AND Region in 'transformer_health' column for display
        // Format: "220.5V | Addis Ababa"
        String displayInfo = String.format("%.1fV | %s", voltage, region);

        long logicalTime = System.currentTimeMillis();

        System.out.println("[HQ] Report from " + nodeId + " (" + region + "): " + voltage + "V, " + powerState);

        // Determine status:
        // Logic: If power is OFFLINE, status is OFFLINE.
        // If power is NORMAL, only set status to ONLINE if it's NOT currently in an
        // OUTAGE.
        String statusToUpdate = "ONLINE";
        if ("OFFLINE".equalsIgnoreCase(powerState)) {
            statusToUpdate = "OFFLINE";
        }

        try (Connection conn = DBConnection.getConnection()) {
            // Check current status to respect "Manual Confirmation" requirement
            String currentStatus = "ONLINE";
            try (PreparedStatement psCheck = conn.prepareStatement("SELECT status FROM nodes WHERE node_id = ?")) {
                psCheck.setString(1, nodeId);
                try (ResultSet rs = psCheck.executeQuery()) {
                    if (rs.next()) {
                        currentStatus = rs.getString("status");
                    }
                }
            }

            // If power is back but node was in OUTAGE/ISSUE, keep it until confirmed
            // NOTE: We REMOVED 'OFFLINE' from here so reconnecting clients show up
            // immediately
            if ("ONLINE".equals(statusToUpdate) && ("OUTAGE".equals(currentStatus) || "ISSUE".equals(currentStatus))) {
                // If it was OFFLINE/OUTAGE, we don't automatically jump to ONLINE
                // We keep the last known status to force the user to "Check Again & Confirm"
                statusToUpdate = currentStatus;
            }

            String upsert = "INSERT INTO nodes (node_id, region, last_seen, last_power_state, last_load_percent, transformer_health, status) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE region=?, last_seen=?, last_power_state=?, transformer_health=?, status=?";

            try (PreparedStatement ps = conn.prepareStatement(upsert)) {
                Timestamp now = new Timestamp(logicalTime);
                // Insert part
                ps.setString(1, nodeId);
                ps.setString(2, region);
                ps.setTimestamp(3, now);
                ps.setString(4, powerState);
                ps.setInt(5, 0);
                ps.setString(6, displayInfo);
                ps.setString(7, statusToUpdate);
                // Update part
                ps.setString(8, region);
                ps.setTimestamp(9, now);
                ps.setString(10, powerState);
                ps.setString(11, displayInfo);
                ps.setString(12, statusToUpdate);

                int rows = ps.executeUpdate();
                if (isServerPeer) {
                    System.out.println("[Sync-DB] Successfully updated " + nodeId + " (Rows affected: " + rows + ")");
                }
                return "OK|ACK_REPORT";
            }
        } catch (java.sql.SQLException e) {
            System.err.println("[DB-ERROR] Failed to save sync data for " + nodeId + ": " + e.getMessage());
            if (isServerPeer) {
                e.printStackTrace(); // Show full trace for inter-server errors
            }
            return "ERR|DB_ERROR";
        }
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
