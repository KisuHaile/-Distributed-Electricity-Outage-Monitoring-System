package com.electricity.server.web;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import com.electricity.db.DBConnection;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Paths;
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
            String uri = t.getRequestURI().toString();
            if (uri.equals("/")) {
                uri = "/index.html";
            }

            // Basic security: prevent traversing up directories
            if (uri.contains("..")) {
                String response = "403 Forbidden";
                t.sendResponseHeaders(403, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
                return;
            }

            // Load from src/main/resources/public (development mostly) or classpath
            // We'll try loading from file system relative to execution first for easier dev
            File file = new File("src/main/resources/public" + uri);
            if (!file.exists()) {
                // Try just "resources/public" if compiled structure is different
                file = new File("resources/public" + uri);
            }

            if (file.exists() && !file.isDirectory()) {
                t.sendResponseHeaders(200, file.length());
                OutputStream os = t.getResponseBody();
                Files.copy(file.toPath(), os);
                os.close();
            } else {
                String response = "404 Not Found";
                t.sendResponseHeaders(404, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
            }
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
}
