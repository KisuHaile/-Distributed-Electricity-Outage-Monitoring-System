import java.io.*;
import java.net.Socket;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private int clientNumber;
    private static final DateTimeFormatter DATETIME_PARSER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public ClientHandler(Socket socket, int clientNumber) {
        this.clientSocket = socket;
        this.clientNumber = clientNumber;
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {

            // 1. SECURITY: Authentication handshake
            String authLine = in.readLine();
            if (authLine == null || !authLine.equals("AUTH|GRID_SEC_2025")) {
                System.out.println("[Client #" + clientNumber + "] Authentication Failed. Closing.");
                out.println("ERR|AUTH_FAILED");
                return; // Close connection
            }
            out.println("AUTH_OK");
            System.out.println("[Client #" + clientNumber + "] Authenticated successfully.");

            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[Client #" + clientNumber + "] Received: " + line);
                String response = handleMessage(line);
                out.println(response);
            }
        } catch (Throwable e) {
            System.err.println("[Client #" + clientNumber + "] Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private String handleMessage(String msg) {
        // Simple pipe-separated protocol
        // HEARTBEAT|nodeId|powerState|load|transformer
        String[] parts = msg.split("\\|", -1);
        if (parts.length == 0)
            return "ERR|Empty";

        String type = parts[0].trim().toUpperCase();
        try {
            switch (type) {
                case "HEARTBEAT":
                    return handleHeartbeat(parts);
                case "OUTAGE":
                    return handleOutage(parts);
                default:
                    return "ERR|UnknownType";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "ERR|Exception|" + e.getMessage();
        }
    }

    private String handleHeartbeat(String[] p) {
        if (p.length < 5)
            return "ERR|HB|BadFormat";
        String nodeId = p[1];
        String powerState = p[2];
        int load = Integer.parseInt(p[3]);
        String transformer = p[4];

        try (Connection conn = DBConnection.getConnection()) {
            // Upsert node row
            String upsert = "INSERT INTO nodes (node_id, last_seen, last_power_state, last_load_percent, transformer_health, status) "
                    +
                    "VALUES (?, NOW(), ?, ?, ?, 'alive') " +
                    "ON DUPLICATE KEY UPDATE last_seen = NOW(), last_power_state = VALUES(last_power_state), last_load_percent = VALUES(last_load_percent), transformer_health = VALUES(transformer_health), status='alive'";
            try (PreparedStatement ps = conn.prepareStatement(upsert)) {
                ps.setString(1, nodeId);
                ps.setString(2, powerState);
                ps.setInt(3, load);
                ps.setString(4, transformer);
                ps.executeUpdate();
            }
            return "OK|HEARTBEAT";
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            return "OK|HEARTBEAT|DB_WARN";
        }
    }

    private String handleOutage(String[] p) {
        if (p.length < 6)
            return "ERR|OUTAGE|BadFormat";
        String eventId = p[1];
        String nodeId = p[2];
        String eventType = p[3];
        String timestampStr = p[4];
        String metadata = p[5];

        Timestamp ts;
        try {
            // Expecting ISO like 2025-12-09T10:35:00
            LocalDateTime ldt = LocalDateTime.parse(timestampStr, DATETIME_PARSER);
            ts = Timestamp.valueOf(ldt);
        } catch (Exception e) {
            // fallback: use now
            ts = new Timestamp(System.currentTimeMillis());
        }

        try (Connection conn = DBConnection.getConnection()) {
            // Insert event - dedupe by PK (event_id)
            String insertEvent = "INSERT INTO events (event_id, node_id, event_type, timestamp, metadata) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(insertEvent)) {
                ps.setString(1, eventId);
                ps.setString(2, nodeId);
                ps.setString(3, eventType);
                ps.setTimestamp(4, ts);
                ps.setString(5, metadata);
                ps.executeUpdate();
            }

            // Optionally update node last_power_state if event is outage start/end
            if ("OUTAGE_START".equalsIgnoreCase(eventType)) {
                updateNodeState(conn, nodeId, "off");
            } else if ("OUTAGE_END".equalsIgnoreCase(eventType)) {
                updateNodeState(conn, nodeId, "on");
            }

            return "OK|OUTAGE|" + eventId;
        } catch (SQLIntegrityConstraintViolationException dup) {
            // Duplicate event id - already processed
            return "OK|OUTAGE|DUPLICATE|" + eventId;
        } catch (SQLException e) {
            e.printStackTrace();
            return "ERR|DB";
        }
    }

    private void updateNodeState(Connection conn, String nodeId, String powerState) throws SQLException {
        String update = "INSERT INTO nodes (node_id, last_seen, last_power_state, status) VALUES (?, NOW(), ?, 'alive') "
                +
                "ON DUPLICATE KEY UPDATE last_seen=NOW(), last_power_state=VALUES(last_power_state), status='alive'";
        try (PreparedStatement ps = conn.prepareStatement(update)) {
            ps.setString(1, nodeId);
            ps.setString(2, powerState);
            ps.executeUpdate();
        }
    }
}
