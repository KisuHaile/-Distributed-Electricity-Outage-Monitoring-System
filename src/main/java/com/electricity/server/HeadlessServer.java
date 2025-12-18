package com.electricity.server;

import com.electricity.db.DBConnection;
import com.electricity.service.DiscoveryService;
import com.electricity.server.web.SimpleWebServer;
import com.electricity.model.Peer;

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
    private static final java.util.Map<Integer, Peer> activePeers = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<Integer> manualPeerIds = new java.util.concurrent.ConcurrentSkipListSet<>();

    private static void printLocalIPs() {
        System.out.println("--- Network Diagnostics ---");
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface
                    .getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp())
                    continue;
                java.util.Enumeration<java.net.InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    java.net.InetAddress addr = addresses.nextElement();
                    if (addr instanceof java.net.Inet4Address) {
                        System.out.println("Local IP: " + addr.getHostAddress() + " (" + iface.getDisplayName() + ")");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Could not detect local IPs: " + e.getMessage());
        }
        System.out.println("---------------------------");
    }

    public static void main(String[] args) {
        System.out.println("Starting Headless Distributed Server...");

        // Configuration (Defaults)
        int id = 1;
        int port = 9000; // Changed to 9000 to avoid conflicts
        int webPort = 3000;
        String peerConfig = "none";
        String dbHost = "localhost";

        printLocalIPs();

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

        // 1. Initialize DB & Check Status
        DBConnection.configure(dbHost);
        try (java.sql.Connection conn = DBConnection.getConnection();
                java.sql.Statement stmt = conn.createStatement();
                java.sql.ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM nodes")) {
            if (rs.next()) {
                System.out.println(
                        "[DB-Warmup] Connection Successful. Local database contains " + rs.getInt(1) + " nodes.");
            }
        } catch (Exception e) {
            System.err.println("[DB-Warmup] WARNING: Could not query local database. Ensure 'init_db.bat' was run.");
            System.err.println("[DB-Warmup] Error: " + e.getMessage());
        }

        // 2. Start Web Server (The Browser Interface)
        SimpleWebServer webServer = new SimpleWebServer(webPort);
        webServer.start();

        // 3. Start Core Server Logic
        startServerThreads(id, port, peerConfig);

        // 4. Keep Main Thread Alive (Enter 'q' to quit)
        System.out.println("Server is running. Press 'q' then Enter to stop.");
        try (Scanner scanner = new Scanner(System.in)) {
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
    }

    private static java.util.Map<Integer, Long> peerLastSeen = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile boolean amILeader = true;
    private static int myServerId;
    private static int myServerPort;

    public static int getServerId() {
        return myServerId;
    }

    public static int getServerPort() {
        return myServerPort;
    }

    public static boolean isLeader() {
        return amILeader;
    }

    public static void broadcastSync(String msg) {
        if (activePeers.isEmpty())
            return;
        System.out.println("[Sync] Broadcasting update to " + activePeers.size() + " peers...");
        String syncMsg = "SYNC|" + msg;
        for (Peer p : activePeers.values()) {
            new Thread(() -> {
                int retries = 3;
                while (retries > 0) {
                    try (Socket s = new Socket()) {
                        s.connect(new java.net.InetSocketAddress(p.getHost(), p.getPort()), 5000);
                        java.io.PrintWriter out = new java.io.PrintWriter(s.getOutputStream(), true);
                        out.println("AUTH|SERVER_2025");
                        out.println(syncMsg);
                        return; // Success
                    } catch (Exception e) {
                        retries--;
                        if (retries == 0) {
                            System.err.println("[Sync] Permanent failure for Node " + p.getId() + " (" + p.getHost()
                                    + "): " + e.getMessage());
                        } else {
                            try {
                                Thread.sleep(2000);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
            }).start();
        }
    }

    private static void sendFullStateTo(Peer p) {
        new Thread(() -> {
            System.out.println("[Sync] >>> SYNC START: Pushing database state to Server #" + p.getId() + " ("
                    + p.getHost() + ":" + p.getPort() + ")");
            int retries = 3;
            while (retries > 0) {
                try (Socket s = new Socket()) {
                    s.connect(new java.net.InetSocketAddress(p.getHost(), p.getPort()), 5000);
                    java.io.PrintWriter out = new java.io.PrintWriter(s.getOutputStream(), true);
                    out.println("AUTH|SERVER_2025");

                    try (java.sql.Connection conn = DBConnection.getConnection();
                            java.sql.Statement stmt = conn.createStatement();
                            java.sql.ResultSet rs = stmt.executeQuery("SELECT * FROM nodes")) {

                        int count = 0;
                        while (rs.next()) {
                            String id = rs.getString("node_id");
                            String region = rs.getString("region");
                            String power = rs.getString("last_power_state");
                            String health = rs.getString("transformer_health");

                            String voltage = "220";
                            if (health != null && health.contains("V")) {
                                voltage = health.split("V")[0].trim();
                            }

                            out.println("SYNC|REPORT|" + id + "|" + voltage + "|" + power + "|" + region);
                            count++;
                        }
                        System.out.println(
                                "[Sync] >>> SYNC COMPLETE: Sent " + count + " districts to Server #" + p.getId());
                    }
                    return; // Success
                } catch (Exception e) {
                    retries--;
                    if (retries == 0) {
                        System.err.println("[Sync] State sync failed after retries: " + e.getMessage());
                    } else {
                        System.out.println("[Sync] Sync attempt failed, retrying in 2s... (" + retries + " left)");
                        try {
                            Thread.sleep(2000);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }).start();
    }

    public static void registerManualPeer(int pid, String phost, int pport, boolean triggerSync) {
        if (pid == myServerId)
            return;
        // Assume manual peers are NOT leader initially; discovery will update this
        Peer p = new Peer(pid, phost, pport, false);
        boolean isNew = !activePeers.containsKey(pid);
        activePeers.put(pid, p);
        peerLastSeen.put(pid, System.currentTimeMillis());
        manualPeerIds.add(pid); // Mark as immortal

        if (isNew) {
            System.out.println("[Peer] Server #" + pid + " joined the cluster.");
            if (triggerSync) {
                sendFullStateTo(p);
            }
        }
        checkLeadership();
    }

    private static void checkLeadership() {
        long now = System.currentTimeMillis();
        boolean otherLeaderFound = false;

        for (Peer p : activePeers.values()) {
            Long lastSeen = peerLastSeen.get(p.getId());
            if (lastSeen != null && (now - lastSeen < 7000)) {
                if (p.isLeader()) {
                    // Tie-breaker: If both claim leader, smaller ID wins
                    if (p.getId() < myServerId) {
                        otherLeaderFound = true;
                        break;
                    }
                }
            }
        }

        boolean newLeadership;
        if (amILeader) {
            // I am leader -> only step down if a smaller ID is ALSO claiming leadership
            newLeadership = !otherLeaderFound;
        } else {
            // I am follower -> only become leader if NO other leader is active anywhere
            // (even if they have a larger ID)
            boolean anyoneActiveLeader = false;
            for (Peer p : activePeers.values()) {
                Long lastSeen = peerLastSeen.get(p.getId());
                if (lastSeen != null && (now - lastSeen < 7000) && p.isLeader()) {
                    anyoneActiveLeader = true;
                    break;
                }
            }
            newLeadership = !anyoneActiveLeader;
        }

        if (amILeader != newLeadership) {
            amILeader = newLeadership;
            System.out.println("[Election] Leadership Change: Am I Leader? " + (amILeader ? "YES" : "NO"));
        }
    }

    private static void startServerThreads(int myId, int port, String peersStr) {
        myServerId = myId;
        myServerPort = port;
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
                            peerLastSeen.entrySet().removeIf(e -> {
                                if (manualPeerIds.contains(e.getKey()))
                                    return false; // Never expire manual ones
                                boolean expired = now - e.getValue() > 7000;
                                if (expired)
                                    activePeers.remove(e.getKey());
                                return expired;
                            });
                            checkLeadership();
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }).start();

                // 1. Process Manual Peers (Fallback)
                if (peersStr != null && !peersStr.equals("none") && !peersStr.equals("auto")) {
                    String[] list = peersStr.split(",");
                    for (String pStr : list) {
                        try {
                            String[] parts = pStr.split(":");
                            int pid = Integer.parseInt(parts[0]);
                            String phost = parts[1];
                            int pport = Integer.parseInt(parts[2]);

                            registerManualPeer(pid, phost, pport, true); // Push OUR state to them

                            // Handshake: connect to them and ask for THEIR state
                            new Thread(() -> {
                                try {
                                    Thread.sleep(3000);
                                    System.out.println("[Handshake] Asking Server #" + pid + " (" + phost
                                            + ") for their state...");
                                    try (Socket s = new Socket(phost, pport);
                                            java.io.PrintWriter pout = new java.io.PrintWriter(s.getOutputStream(),
                                                    true)) {
                                        pout.println("AUTH|SERVER_2025");
                                        pout.println("SYNC|JOIN|" + myId + "|0|" + port);
                                    }
                                } catch (Exception e) {
                                    System.err.println(
                                            "[Handshake] Connection failed to Server #" + pid + ": " + e.getMessage());
                                }
                            }).start();
                        } catch (Exception e) {
                            System.err.println("[Manual-Peer] Bad format: " + pStr);
                        }
                    }
                }

                int clientCounter = 0;

                // 2. START DISCOVERY BEACON (Dynamic)
                discoveryService = new DiscoveryService(myId, port, (peer) -> {
                    if (!activePeers.containsKey(peer.getId())) {
                        // This is a NEW peer we haven't synced with yet
                        sendFullStateTo(peer);
                    }
                    peerLastSeen.put(peer.getId(), System.currentTimeMillis());
                    activePeers.put(peer.getId(), peer);
                    checkLeadership();
                }, () -> amILeader);
                new Thread(discoveryService).start();

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
                discoveryService.stop();
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
