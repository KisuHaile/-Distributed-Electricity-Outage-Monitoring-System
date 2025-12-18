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
            server.createContext("/api/configure", new ConfigureHandler());
            server.createContext("/api/set_voltage", new SetVoltageHandler());
            server.setExecutor(null);
            server.start();
            System.out.println("Client Web Dashboard running at http://localhost:" + port + "/client/");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void send(HttpExchange t, int code, String body) throws IOException {
        t.sendResponseHeaders(code, body.length());
        OutputStream os = t.getResponseBody();
        os.write(body.getBytes());
        os.close();
    }

    class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String uri = t.getRequestURI().getPath();
            if (uri.equals("/")) {
                t.getResponseHeaders().set("Location", "/client/index.html");
                t.sendResponseHeaders(302, -1);
                return;
            }
            if (uri.endsWith("/"))
                uri += "index.html";
            if (uri.contains("..")) {
                send(t, 403, "Forbidden");
                return;
            }
            File file = new File("src/main/resources/public" + uri);
            if (!file.exists())
                file = new File("resources/public" + uri);

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
            String json = String.format(
                    "{\"connected\": %b, \"nodeId\": \"%s\", \"region\": \"%s\", \"voltage\": %.1f, \"powerState\": \"%s\", \"logs\": %s}",
                    client.isConnected(),
                    client.getNodeId(),
                    client.getRegion(),
                    client.getVoltage(),
                    client.getPowerState(),
                    client.getLogsJson());
            t.getResponseHeaders().set("Content-Type", "application/json");
            send(t, 200, json);
        }
    }

    class ConfigureHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String query = t.getRequestURI().getQuery();
            String id = "District-X";
            String region = "Unknown";
            if (query != null) {
                for (String param : query.split("&")) {
                    String[] pair = param.split("=");
                    if (pair.length == 2) {
                        if (pair[0].equals("id"))
                            id = pair[1];
                        if (pair[0].equals("region"))
                            region = pair[1];
                    }
                }
            }
            client.configureAndConnect(id, region);
            send(t, 200, "{\"status\":\"configured\"}");
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
                case "outage_start":
                    client.sendOutage("START");
                    break;
                case "outage_end":
                    client.sendOutage("END");
                    break;
                case "low_voltage":
                    client.updateState("LOW");
                    break;
                default:
                    send(t, 400, "Unknown Action");
                    return;
            }
            send(t, 200, "{\"status\":\"ok\"}");
        }
    }

    class SetVoltageHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String query = t.getRequestURI().getQuery();
            if (query != null && query.contains("v=")) {
                try {
                    double v = Double.parseDouble(query.split("v=")[1].split("&")[0]);
                    client.setManualVoltage(v);
                    send(t, 200, "{\"status\":\"ok\"}");
                } catch (Exception e) {
                    send(t, 400, "{\"error\":\"Invalid Voltage\"}");
                }
            } else {
                send(t, 400, "{\"error\":\"Missing Voltage\"}");
            }
        }
    }
}
