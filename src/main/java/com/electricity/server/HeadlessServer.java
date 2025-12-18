package com.electricity.server;

import com.electricity.db.DBConnection;
import com.electricity.service.DiscoveryService;
import com.electricity.server.web.SimpleWebServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Scanner;

/**
 * Headless Server that runs without a GUI.
 * Starts background threads for networking and a WebServer for the Dashboard.
 */
public class HeadlessServer {

    private static volatile boolean running = true;
    private static ServerSocket serverSocket;
    private static DiscoveryService discoveryService;

    public static void main(String[] args) {
        System.out.println("Starting Headless Distributed Server...");

        // Configuration (Defaults)
        int id = 1;
        int port = 9000; // Changed to 9000 to avoid conflicts
        int webPort = 3000;
        String peerConfig = "none";
        String dbHost = "localhost";

        // Simple CLI Argument Parsing: [id] [port] [webPort] [peers] [dbHost]
        if (args.length > 0)
            id = Integer.parseInt(args[0]);
        if (args.length > 1)
            port = Integer.parseInt(args[1]);
        if (args.length > 2)
            webPort = Integer.parseInt(args[2]);
        if (args.length > 3)
            peerConfig = args[3];
        if (args.length > 4)
            dbHost = args[4];

        System.out.println("Config: ID=" + id + ", Port=" + port + ", WebPort=" + webPort);
        System.out.println("DB Host: " + dbHost);

        // 1. Initialize DB
        DBConnection.configure(dbHost);

        // 2. Start Web Server (The Browser Interface)
        SimpleWebServer webServer = new SimpleWebServer(webPort);
        webServer.start();

        // 3. Start Core Server Logic
        startServerThreads(id, port, peerConfig);

        // 4. Keep Main Thread Alive (Enter 'q' to quit)
        System.out.println("Server is running. Press 'q' then Enter to stop.");
        Scanner scanner = new Scanner(System.in);
        while (running) {
            if (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if ("q".equalsIgnoreCase(line.trim())) {
                    running = false;
                    shutdown();
                    System.exit(0);
                }
            }
        }
    }

    private static java.util.Map<Integer, Long> peerLastSeen = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile boolean amILeader = true;
    private static int myServerId;

    private static void checkLeadership() {
        boolean highest = true;
        long now = System.currentTimeMillis();
        for (java.util.Map.Entry<Integer, Long> entry : peerLastSeen.entrySet()) {
            if (now - entry.getValue() < 15000) { // Active in last 15s
                if (entry.getKey() > myServerId) {
                    highest = false;
                    break;
                }
            }
        }
        if (amILeader != highest) {
            amILeader = highest;
            System.out.println("[Election] Leadership Change: Am I Leader? " + (amILeader ? "YES" : "NO"));
        }
    }

    private static void startServerThreads(int myId, int port, String peersStr) {
        myServerId = myId;
        new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(port)) {
                serverSocket = ss;
                System.out.println("Utility HQ Server #" + myId + " listening on port " + port);

                // Start Peer Cleanup Thread
                new Thread(() -> {
                    while (running) {
                        try {
                            Thread.sleep(5000);
                            long now = System.currentTimeMillis();
                            peerLastSeen.entrySet().removeIf(e -> now - e.getValue() > 15000);
                            checkLeadership();
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }).start();

                int clientCounter = 0;

                // START DISCOVERY BEACON
                DiscoveryService beacon = new DiscoveryService(myId, port, (peer) -> {
                    peerLastSeen.put(peer.getId(), System.currentTimeMillis());
                    checkLeadership();
                }, () -> amILeader);
                new Thread(beacon).start();

                while (running) {
                    try {
                        Socket socket = serverSocket.accept();
                        clientCounter++;

                        // Reject clients if not leader?
                        // If client found us via discovery, they check isLeader flag.
                        // But if race condition, they might connect just as we lose leadership.
                        // Ideally we check here.
                        if (!amILeader) {
                            // Optional: close connection or redirect.
                            // For now, let's allow it but warn.
                            // Actually, "Share Database Data" implies all servers access DB.
                            // Leader just coordinates.
                            // So it's fine if they connect. But usually clients prefer Leader.
                        }

                        ClientHandler handler = new ClientHandler(socket, clientCounter);
                        new Thread(handler).start();
                    } catch (SocketException se) {
                        if (running)
                            System.err.println("Socket closed.");
                    } catch (Exception e) {
                        System.err.println("Accept error: " + e.getMessage());
                    }
                }
                beacon.stop();
            } catch (java.net.BindException be) {
                System.err.println("CRITICAL ERROR: Port " + port + " is already in use!");
                System.err.println("Please close other server windows or change the port.");
                System.exit(1);
            } catch (IOException e) {
                System.err.println("Could not start socket server: " + e.getMessage());
            }
        }).start();
    }

    private static void shutdown() {
        System.out.println("Shutting down...");
        try {
            if (serverSocket != null)
                serverSocket.close();
            if (discoveryService != null)
                discoveryService.stop();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
