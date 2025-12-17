package com.electricity.client;

import com.electricity.service.DiscoveryService;
import com.electricity.client.web.ClientWebServer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;

public class HeadlessClient {

    private String nodeId;
    private Socket socket;
    private PrintStream out;
    private BufferedReader in;
    private volatile boolean connected = false;
    private Thread listenerThread;
    private Thread heartbeatThread;
    private DiscoveryService discoveryService;
    private List<String> logs = Collections.synchronizedList(new ArrayList<>());

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public HeadlessClient(String nodeId) {
        this.nodeId = nodeId;
    }

    public static void main(String[] args) {
        System.out.println("Starting Client Simulator (Web Enabled)...");

        String myNodeId = "addis_001";
        int webPort = 3002; // Different from server's 3000/3001

        if (args.length > 0)
            myNodeId = args[0];
        if (args.length > 1)
            webPort = Integer.parseInt(args[1]);

        HeadlessClient client = new HeadlessClient(myNodeId);

        // Start Web Server
        ClientWebServer webServer = new ClientWebServer(webPort, client);
        webServer.start();

        System.out.println("Open Client Dashboard: http://localhost:" + webPort + "/client/");

        // Auto-connect on start
        client.startAutoDiscovery();

        // Keep alive
        Scanner scanner = new Scanner(System.in);
        while (true) {
            String line = scanner.nextLine();
            if ("q".equals(line))
                System.exit(0);
        }
    }

    public void log(String msg) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String entry = "[" + time + "] " + msg;
        System.out.println(entry);
        logs.add(entry);
        // Keep logs size manageable
        if (logs.size() > 50)
            logs.remove(0);
    }

    public String getLogsJson() {
        StringBuilder sb = new StringBuilder("[");
        synchronized (logs) {
            boolean first = true;
            for (String l : logs) {
                if (!first)
                    sb.append(",");
                sb.append("\"").append(l.replace("\"", "\\\"")).append("\"");
                first = false;
            }
        }
        sb.append("]");
        return sb.toString();
    }

    public boolean isConnected() {
        return connected;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void connectToHQ(String ip, int port) {
        new Thread(() -> {
            while (!connected && !Thread.currentThread().isInterrupted()) {
                try {
                    log("Connecting to Utility HQ (" + ip + ":" + port + ")...");
                    if (socket != null && !socket.isClosed())
                        socket.close();

                    socket = new Socket(ip, port);
                    out = new PrintStream(socket.getOutputStream());
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                    // Simple Handshake
                    out.println("AUTH|GRID_SEC_2025");
                    String resp = in.readLine();

                    if ("AUTH_OK".equals(resp)) {
                        log("Connected to Central Authority.");
                        connected = true;
                        startListener();
                        startAutoReport();
                        break; // Exit retry loop
                    } else {
                        log("HQ rejected connection: " + resp);
                        closeSocket();
                        Thread.sleep(5000); // Wait before retry
                    }
                } catch (Exception e) {
                    log("HQ Unreachable (" + e.getMessage() + "). Retrying in 5s...");
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                    }
                }
            }
        }).start();
    }

    public void startAutoDiscovery() {
        if (connected)
            return;
        log("Scanning for Utility HQ Signal (Multicast)...");

        new Thread(() -> {
            try (java.net.MulticastSocket socket = new java.net.MulticastSocket(4446)) {
                java.net.InetAddress group = java.net.InetAddress.getByName("230.0.0.1");
                socket.joinGroup(group);
                byte[] buf = new byte[256];

                while (!connected && !Thread.currentThread().isInterrupted()) {
                    java.net.DatagramPacket packet = new java.net.DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String msg = new String(packet.getData(), 0, packet.getLength());
                    // Msg: HELLO|ServerId|TcpPort|IsLeader
                    String[] parts = msg.split("\\|");
                    if (parts.length >= 4 && "HELLO".equals(parts[0])) {
                        boolean isLeader = Boolean.parseBoolean(parts[3]);
                        if (isLeader) {
                            String ip = packet.getAddress().getHostAddress();
                            int port = Integer.parseInt(parts[2]);
                            log("Found Utility HQ at " + ip + ":" + port);
                            connectToHQ(ip, port);
                            break;
                        }
                    }
                }
                socket.leaveGroup(group);
            } catch (Exception e) {
                log("Auto-discovery failed: " + e.getMessage());
                // Fallback to local
                log("Fallback: searching local...");
                connectToHQ("127.0.0.1", 9000);
            }
        }).start();
    }

    public void disconnect() {
        // No discovery service to stop
        if (heartbeatThread != null)
            heartbeatThread.interrupt();
        closeSocket();
        log("Disconnected.");
    }

    private void closeSocket() {
        connected = false;
        try {
            if (socket != null)
                socket.close();
        } catch (Exception ignored) {
        }
    }

    private void startListener() {
        listenerThread = new Thread(() -> {
            try {
                String line;
                while (connected && (line = in.readLine()) != null) {
                    log("Central: " + line);
                }
            } catch (Exception e) {
                if (connected)
                    log("Lost connection to HQ: " + e.getMessage());
            } finally {
                if (connected)
                    disconnect();
            }
        });
        listenerThread.start();
    }

    private void startAutoReport() {
        heartbeatThread = new Thread(() -> {
            log("Auto-reporting started (every 10s)...");
            try {
                // Initial delay
                Thread.sleep(1000);
                if (connected)
                    sendReport();

                while (connected && !Thread.currentThread().isInterrupted()) {
                    Thread.sleep(10000);
                    if (connected)
                        sendReport();
                }
            } catch (InterruptedException e) {
                log("Auto-reporting stopped.");
            }
        });
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    public void sendReport() {
        if (!connected)
            return;
        // Logic: REPORT|NodeId|Voltage|PowerState
        // PowerState: NORMAL, LOW, OFF
        double voltage = 220.0 + (Math.random() * 10 - 5); // 215-225V
        String state = "NORMAL";
        if (Math.random() < 0.1) {
            voltage = 180;
            state = "LOW";
        }

        String msg = String.format("REPORT|%s|%.1f|%s", nodeId, voltage, state);
        send(msg);
    }

    public void sendOutage(String type) {
        if (!connected)
            return;
        String eventId = UUID.randomUUID().toString();
        String ts = LocalDateTime.now().format(DF);

        String msg;
        if ("START".equals(type)) {
            msg = String.format("OUTAGE|%s|%s|OUTAGE_START|%s|line_fault", eventId, nodeId, ts);
        } else {
            msg = String.format("OUTAGE|%s|%s|OUTAGE_END|%s|restored", eventId, nodeId, ts);
        }
        send(msg);
    }

    private void send(String msg) {
        if (out != null) {
            out.println(msg);
            log("Sent: " + msg);
        }
    }
}
