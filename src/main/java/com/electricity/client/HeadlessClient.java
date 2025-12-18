package com.electricity.client;

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

        // Auto-connect enabled by default to allow remote "Invitation" from server.
        client.startAutoDiscovery();

        // Keep alive
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                String line = scanner.nextLine();
                if ("q".equals(line))
                    System.exit(0);
            }
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

    // getNodeId moved to bottom

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
                        startAutoReport(); // Start automatic reporting
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

    public void configureAndConnect(String id, String region) {
        this.nodeId = id;
        this.region = region;
        log("Configuration received: ID=" + id + ", Region=" + region);
        if (!connected) {
            startAutoDiscovery();
        }
    }

    private void startAutoReport() {
        new Thread(() -> {
            while (connected && !Thread.currentThread().isInterrupted()) {
                try {
                    sendReport();
                    Thread.sleep(5000); // Send report every 5 seconds
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    public void startAutoDiscovery() {
        if (connected)
            return;
        log("District " + nodeId + " (" + region + ") scanning for Utility HQ...");

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
                log("Fallback: searching local 127.0.0.1...");
                connectToHQ("127.0.0.1", 9000);
            }
        }).start();
    }

    public void disconnect() {
        // No discovery service to stop
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
                    String cmd = line.trim().toUpperCase();
                    if ("SOLVED_CHECK".equals(cmd)) {
                        log("HQ is inquiring if the problem is solved...");
                        if ("NORMAL".equals(currentPowerState)) {
                            log("Sending Confirmation: Problem is RESOLVED.");
                            send("CONFIRM_RESOLVED|" + nodeId);
                        } else {
                            log("Grid is still down. Reporting current status...");
                            sendReport();
                        }
                    }
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

    private String region = "Unknown";
    private double currentVoltage = 220.0;
    private String currentPowerState = "NORMAL";

    public void updateState(String newState) {
        this.currentPowerState = newState;
        updateSimulation(); // Apply immediately
        log("Manual State Update: " + newState);
    }

    private boolean manualVoltageMode = false;

    public void setManualVoltage(double v) {
        this.manualVoltageMode = true;
        this.currentVoltage = v;
        log("Manual Voltage set to: " + v + "V");
        sendReport(); // Send update immediately
    }

    private void updateSimulation() {
        if (manualVoltageMode)
            return; // Don't fluctuate if manual

        // Simulate voltage fluctuation
        if ("NORMAL".equals(currentPowerState)) {
            // 215V - 225V
            currentVoltage = 220.0 + (Math.random() * 10 - 5);
        } else if ("LOW".equals(currentPowerState)) {
            // 180V - 185V
            currentVoltage = 180.0 + (Math.random() * 5);
        } else {
            currentVoltage = 0.0;
        }
    }

    public void sendReport() {
        if (!connected)
            return;
        // REPORT|NodeId|Voltage|PowerState|Region
        // We append region to the report so server knows it (if server supports it)
        // Check if server supports region in ClientHandler first?
        // ClientHandler expects: REPORT|nodeId|voltage|powerState
        // We will stick to protocol: REPORT|nodeId|voltage|powerState
        // Metadata like Region should ideally be sent once or we update Server to
        // accept it.
        // For now, let's stick to valid server protocol.

        String msg = String.format("REPORT|%s|%.1f|%s|%s", nodeId, currentVoltage, currentPowerState, region);
        send(msg);
    }

    // Getters for Web API
    public double getVoltage() {
        return currentVoltage;
    }

    public String getPowerState() {
        return currentPowerState;
    }

    public String getRegion() {
        return region;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void sendOutage(String type) {
        if (!connected)
            return;

        // Update local state so heartbeats reflect the outage!
        if ("START".equals(type)) {
            this.currentPowerState = "OUTAGE";
            log("🚫 OUTAGE STARTED (Heartbeats will report OUTAGE)");
        } else {
            this.currentPowerState = "NORMAL";
            log("✅ OUTAGE RESOLVED (Heartbeats will report NORMAL)");
        }

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
