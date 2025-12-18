package com.electricity.server.web;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import com.electricity.db.DBConnection;
import com.electricity.server.ClientHandler;
import com.electricity.server.HeadlessServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.nio.file.Files;
import java.io.File;

public class SimpleWebServer {

    private int port;

    public SimpleWebServer(int port) {
        this.port = port;
    }

    public void start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new StaticHandler());
            server.createContext("/api/nodes", new ApiHandler());
            server.createContext("/api/stats", new StatsHandler());
            server.createContext("/api/invite", new InviteHandler());
            server.createContext("/api/verify", new VerifyHandler());
            server.createContext("/api/report_web", new WebReportHandler());
            server.createContext("/api/poll", new PollHandler());
            server.setExecutor(null); // creates a default executor
            server.start();
            System.out.println("Web Dashboard running at http://localhost:" + port + "/");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String uri = t.getRequestURI().getPath();
            if (uri.equals("/")) {
                uri = "/index.html";
            }

            // Basic security: prevent traversing up directories
            if (uri.contains("..")) {
                send(t, 403, "403 Forbidden");
                return;
            }

            // Load from src/main/resources/public
            File file = new File("src/main/resources/public" + uri);
            if (!file.exists()) {
                file = new File("resources/public" + uri);
            }

            if (file.exists() && !file.isDirectory()) {
                t.sendResponseHeaders(200, file.length());
                OutputStream os = t.getResponseBody();
                Files.copy(file.toPath(), os);
                os.close();
            } else {
                send(t, 404, "404 Not Found");
            }
        }

        private void send(HttpExchange t, int code, String body) throws IOException {
            t.sendResponseHeaders(code, body.length());
            OutputStream os = t.getResponseBody();
            os.write(body.getBytes());
            os.close();
        }
    }

    static class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String json = "[]";
            try (Connection conn = DBConnection.getConnection()) {
                String query = "SELECT node_id, status, last_load_percent, last_power_state, transformer_health, " +
                        "DATE_FORMAT(last_seen, '%H:%i:%s') as last_seen_time " +
                        "FROM nodes ORDER BY node_id";

                try (Statement stmt = conn.createStatement();
                        ResultSet rs = stmt.executeQuery(query)) {

                    StringBuilder sb = new StringBuilder();
                    sb.append("[");
                    boolean first = true;
                    while (rs.next()) {
                        if (!first)
                            sb.append(",");
                        sb.append("{");
                        sb.append("\"id\":\"").append(escape(rs.getString("node_id"))).append("\",");
                        sb.append("\"status\":\"").append(escape(rs.getString("status"))).append("\",");
                        sb.append("\"load\":").append(rs.getInt("last_load_percent")).append(",");
                        sb.append("\"power\":\"").append(escape(rs.getString("last_power_state"))).append("\",");
                        sb.append("\"transformer\":\"").append(escape(rs.getString("transformer_health")))
                                .append("\",");
                        sb.append("\"lastSeen\":\"").append(escape(rs.getString("last_seen_time"))).append("\"");
                        sb.append("}");
                        first = false;
                    }
                    sb.append("]");
                    json = sb.toString();
                    if (!first) {
                        System.out.println("[API] Dashboard polled. Serving "
                                + (first ? 0 : 1 + sb.toString().split("},").length - 1) + " nodes.");
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                json = "{\"error\":\"" + e.getMessage() + "\"}";
            }

            t.getResponseHeaders().set("Content-Type", "application/json");
            t.sendResponseHeaders(200, json.length());
            OutputStream os = t.getResponseBody();
            os.write(json.getBytes());
            os.close();
        }

        private String escape(String s) {
            if (s == null)
                return "";
            return s.replace("\"", "\\\"");
        }
    }

    static class InviteHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String query = t.getRequestURI().getQuery();
            String ip = null;
            if (query != null && query.contains("ip=")) {
                ip = query.split("ip=")[1].split("&")[0];
            }

            if (ip == null || ip.isEmpty()) {
                send(t, 400, "{\"error\":\"Missing IP\"}");
                return;
            }

            // Send UDP Invite
            try {
                // protocol: HELLO|ServerId|TcpPort|IsLeader
                String msg = "HELLO|" + HeadlessServer.getServerId() + "|" + HeadlessServer.getServerPort() + "|"
                        + HeadlessServer.isLeader();
                byte[] buf = msg.getBytes();
                java.net.InetAddress address = java.net.InetAddress.getByName(ip);
                java.net.DatagramPacket packet = new java.net.DatagramPacket(buf, buf.length, address, 4446);
                try (java.net.DatagramSocket s = new java.net.DatagramSocket()) {
                    s.send(packet);
                }
                send(t, 200, "{\"status\":\"sent\", \"target\":\"" + ip + "\"}");
            } catch (Exception e) {
                send(t, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }

        private void send(HttpExchange t, int code, String body) throws IOException {
            t.getResponseHeaders().set("Content-Type", "application/json");
            t.sendResponseHeaders(code, body.length());
            t.getResponseBody().write(body.getBytes());
            t.getResponseBody().close();
        }
    }

    private static final java.util.Map<String, String> pendingCommands = new java.util.concurrent.ConcurrentHashMap<>();

    public static void queueCommand(String nodeId, String cmd) {
        pendingCommands.put(nodeId, cmd);
    }

    static class WebReportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String query = t.getRequestURI().getRawQuery();
            if (query == null) {
                send(t, 400, "{\"error\":\"Missing params\"}");
                return;
            }

            java.util.Map<String, String> params = new java.util.HashMap<>();
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=");
                try {
                    String key = java.net.URLDecoder.decode(kv[0], "UTF-8");
                    String val = kv.length > 1 ? java.net.URLDecoder.decode(kv[1], "UTF-8") : "";
                    params.put(key, val);
                } catch (Exception e) {
                }
            }

            String id = params.get("id");
            String v = params.get("v");
            String p = params.get("p");
            String r = params.get("r");

            System.out.println("[WebAPI] Received report: id=" + id + ", p=" + p + ", v=" + v);

            if (id == null || v == null) {
                send(t, 400, "{\"error\":\"Missing required fields\"}");
                return;
            }

            try (Connection conn = DBConnection.getConnection()) {
                // Manual Confirmation Rule: Don't auto-resolve from OUTAGE to ONLINE
                String currentStatus = "ONLINE";
                try (java.sql.PreparedStatement psCheck = conn
                        .prepareStatement("SELECT status FROM nodes WHERE node_id = ?")) {
                    psCheck.setString(1, id);
                    try (java.sql.ResultSet rs = psCheck.executeQuery()) {
                        if (rs.next())
                            currentStatus = rs.getString("status");
                    }
                }

                String status = "ONLINE";
                if ("OFFLINE".equalsIgnoreCase(p)) {
                    status = "OFFLINE";
                } else if ("OUTAGE".equals(currentStatus) || "ISSUE".equals(currentStatus)) {
                    status = currentStatus; // Only keep grid-level outages sticky
                }

                String display = v + "V | " + (r != null ? r : "Unknown");
                String sql = "INSERT INTO nodes (node_id, region, last_seen, last_power_state, last_load_percent, transformer_health, status) "
                        + "VALUES (?, ?, NOW(), ?, 0, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE region=?, last_seen=NOW(), last_power_state=?, transformer_health=?, status=?";

                try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, id);
                    ps.setString(2, r != null ? r : "Unknown");
                    ps.setString(3, p != null ? p : "NORMAL");
                    ps.setString(4, display);
                    ps.setString(5, status);

                    ps.setString(6, r != null ? r : "Unknown");
                    ps.setString(7, p != null ? p : "NORMAL");
                    ps.setString(8, display);
                    ps.setString(9, status);

                    ps.executeUpdate();
                }

                // Construct sync message for peers
                String syncMsg = "REPORT|" + id + "|" + v + "|" + (p != null ? p : "NORMAL") + "|"
                        + (r != null ? r : "Unknown");
                HeadlessServer.broadcastSync(syncMsg);

                // Check for confirmed restoration
                String cmd = params.get("cmd");
                if ("CONFIRM_RESOLVED".equalsIgnoreCase(cmd)) {
                    String update = "UPDATE nodes SET status='ONLINE', last_power_state='NORMAL' WHERE node_id=?";
                    try (java.sql.PreparedStatement ps = conn.prepareStatement(update)) {
                        ps.setString(1, id);
                        ps.executeUpdate();
                    }
                    // IMPORTANT: Use the space-separated or pipe format expected by handleMessage
                    HeadlessServer.broadcastSync("CONFIRM_RESOLVED|" + id);
                    System.out.println("[WebAPI] Restoration CONFIRMED for district " + id);
                }

                send(t, 200, "{\"status\":\"ok\"}");
            } catch (Exception e) {
                send(t, 500, "{\"error\":\"" + e.getMessage() + "\"}");
            }
        }

        private void send(HttpExchange t, int code, String body) throws IOException {
            t.getResponseHeaders().set("Content-Type", "application/json");
            t.sendResponseHeaders(code, body.length());
            t.getResponseBody().write(body.getBytes());
            t.getResponseBody().close();
        }
    }

    static class VerifyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String query = t.getRequestURI().getQuery();
            String id = null;
            if (query != null && query.contains("id=")) {
                id = query.split("id=")[1].split("&")[0];
            }

            if (id == null || id.isEmpty()) {
                send(t, 400, "{\"error\":\"Missing ID\"}");
                return;
            }

            // Try TCP Client first
            ClientHandler handler = ClientHandler.getHandler(id);
            if (handler != null) {
                handler.sendMessage("SOLVED_CHECK");
                send(t, 200, "{\"status\":\"sent\"}");
            } else {
                // Not local? Queue for Web Simulator AND broadcast to peers
                queueCommand(id, "SOLVED_CHECK");
                HeadlessServer.broadcastSync("VERIFY_RELAY|" + id);
                send(t, 200, "{\"status\":\"queued_web\"}");
            }
        }

        private void send(HttpExchange t, int code, String body) throws IOException {
            t.getResponseHeaders().set("Content-Type", "application/json");
            t.sendResponseHeaders(code, body.length());
            t.getResponseBody().write(body.getBytes());
            t.getResponseBody().close();
        }
    }

    static class PollHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String id = t.getRequestURI().getQuery();
            if (id != null && id.contains("id=")) {
                id = id.split("id=")[1].split("&")[0];
            }

            String cmd = "NONE";
            if (id != null && pendingCommands.containsKey(id)) {
                cmd = pendingCommands.remove(id);
                System.out.println("[WebAPI] Command " + cmd + " polled by simulator " + id);
            }

            String json = "{\"command\":\"" + cmd + "\"}";
            t.getResponseHeaders().set("Content-Type", "application/json");
            t.sendResponseHeaders(200, json.length());
            t.getResponseBody().write(json.getBytes());
            t.getResponseBody().close();
        }
    }

    static class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String json = String.format("{\"serverId\": %d, \"isLeader\": %b, \"port\": %d}",
                    HeadlessServer.getServerId(), HeadlessServer.isLeader(), HeadlessServer.getServerPort());
            t.getResponseHeaders().set("Content-Type", "application/json");
            t.sendResponseHeaders(200, json.length());
            t.getResponseBody().write(json.getBytes());
            t.getResponseBody().close();
        }
    }
}
