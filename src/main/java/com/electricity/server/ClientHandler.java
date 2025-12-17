package com.electricity.server;

import java.io.*;
import java.net.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.electricity.db.DBConnection;
import com.electricity.service.ElectionManager;

public class ClientHandler implements Runnable {

    private Socket clientSocket;
    private int clientNumber;
    private ElectionManager electionManager;
    private static final DateTimeFormatter DATETIME_PARSER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public ClientHandler(Socket socket, int clientNumber) {
        this.clientSocket = socket;
        this.clientNumber = clientNumber;
        this.electionManager = null;
    }

    public ClientHandler(Socket socket, int clientNumber, ElectionManager electionManager) {
        this.clientSocket = socket;
        this.clientNumber = clientNumber;
        this.electionManager = electionManager;
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            // 1. SECURITY: Authentication handshake
            String authLine = in.readLine();
            boolean isServerPeer = false;

            if (authLine != null && authLine.equals("AUTH|SERVER_PEER")) {
                if (electionManager == null) {
                    out.println("ERR|NO_ELECTION_SUPPORT");
                    return;
                }
                isServerPeer = true;
                out.println("AUTH_OK");
                System.out.println("[Client #" + clientNumber + "] Server Peer Authenticated.");
            } else if (authLine == null || !authLine.equals("AUTH|GRID_SEC_2025")) {
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

                if (isServerPeer && electionManager != null) {
                    String response = electionManager.processMessage(line);
                    out.println(response);
                } else {
                    String response = handleMessage(line);
                    out.println(response);
                }
            }
        } catch (Throwable e) {
            String msg = e.getMessage();
            if (msg != null && msg.contains("last packet sent successfully")) {
                // Suppress verbose MySQL connection warning
            } else {
                System.err.println("[Client #" + clientNumber + "] Error: " + msg);
                e.printStackTrace();
            }
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignored) {
            }
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
                case "HEARTBEAT":
                    return handleReportLegacy(parts);
                default:
                    return "ERR|UnknownType";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "ERR|Exception|" + e.getMessage();
        }
    }

    private String handleReport(String[] p) {
        // REPORT|nodeId|voltage|powerState
        if (p.length < 4)
            return "ERR|RPT|BadFormat";

        String nodeId = p[1];
        double voltage = Double.parseDouble(p[2]); // new field
        String powerState = p[3];
        // We will store Voltage in 'last_load_percent' column (repurposed) or
        // 'transformer_health' as metadata
        // Let's store voltage in transformer_health as string "220.5V"

        // Simulating Lamport Logical Clock (ordering events)
        long logicalTime = System.currentTimeMillis();

        System.out.println("[Central Authority] Processing Report from District " + nodeId + " (Voltage: " + voltage
                + "V, State: " + powerState + ")");

        // Update Database (Central Truth)
        try (Connection conn = DBConnection.getConnection()) {
            String upsert = "INSERT INTO nodes (node_id, last_seen, last_power_state, last_load_percent, transformer_health, status) "
                    +
                    "VALUES (?, ?, ?, ?, ?, 'ONLINE') " +
                    "ON DUPLICATE KEY UPDATE last_seen=VALUES(last_seen), last_power_state=VALUES(last_power_state), " +
                    "transformer_health=VALUES(transformer_health), status='ONLINE'"; // last_load_percent
                                                                                      // ignored/preserved

            try (PreparedStatement ps = conn.prepareStatement(upsert)) {
                Timestamp now = new Timestamp(logicalTime);
                ps.setString(1, nodeId);
                ps.setTimestamp(2, now);
                ps.setString(3, powerState);
                ps.setInt(4, 0); // Unused load
                ps.setString(5, String.format("%.1fV", voltage));
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
