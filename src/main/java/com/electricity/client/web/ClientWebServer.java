package com.electricity.client.web;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import com.electricity.client.HeadlessClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.io.File;
import java.util.List;

public class ClientWebServer {

    private int port;
    private HeadlessClient client;

    public ClientWebServer(int port, HeadlessClient client) {
        this.port = port;
        this.client = client;
    }

    public void start() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new StaticHandler());
            server.createContext("/api/status", new StatusHandler());
            server.createContext("/api/action", new ActionHandler());
            server.setExecutor(null);
            server.start();
            System.out.println("Client Web Dashboard running at http://localhost:" + port + "/client/");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String uri = t.getRequestURI().getPath(); // Use getPath instead of toString to ignore query params

            // Handle root redirect
            if (uri.equals("/")) {
                t.getResponseHeaders().set("Location", "/client/index.html");
                t.sendResponseHeaders(302, -1);
                return;
            }

            // Handle directory access - if it ends in /, assumes index.html
            if (uri.endsWith("/")) {
                uri += "index.html";
            }

            // Basic security
            if (uri.contains("..")) {
                send(t, 403, "Forbidden");
                return;
            }

            // Try src/main/resources/public (Source mode)
            File file = new File("src/main/resources/public" + uri);
            if (!file.exists()) {
                // Try resources/public (Bin/Deployment mode)
                file = new File("resources/public" + uri);
            }

            if (file.exists() && !file.isDirectory()) {
                t.sendResponseHeaders(200, file.length());
                OutputStream os = t.getResponseBody();
                Files.copy(file.toPath(), os);
                os.close();
            } else {
                send(t, 404, "Not Found");
            }
        }
    }

    class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String json = String.format("{\"connected\": %b, \"nodeId\": \"%s\", \"logs\": %s}",
                    client.isConnected(),
                    client.getNodeId(),
                    client.getLogsJson());

            t.getResponseHeaders().set("Content-Type", "application/json");
            send(t, 200, json);
        }
    }

    class ActionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if (!"POST".equals(t.getRequestMethod())) {
                send(t, 405, "Method Not Allowed");
                return;
            }

            String query = t.getRequestURI().getQuery();
            // Simple parsing: action=...
            String action = "";
            if (query != null && query.contains("action=")) {
                action = query.split("action=")[1].split("&")[0];
            }

            switch (action) {
                case "connect":
                    client.startAutoDiscovery();
                    break;
                case "disconnect":
                    client.disconnect();
                    break;
                case "heartbeat":
                    // Renamed to sendReport in new model
                    client.sendReport();
                    break;
                case "outage_start":
                    client.sendOutage("START");
                    break;
                case "outage_end":
                    client.sendOutage("END");
                    break;
                default:
                    send(t, 400, "Unknown Action");
                    return;
            }

            send(t, 200, "{\"status\":\"ok\"}");
        }
    }

    private void send(HttpExchange t, int code, String body) throws IOException {
        t.sendResponseHeaders(code, body.length());
        OutputStream os = t.getResponseBody();
        os.write(body.getBytes());
        os.close();
    }
}
