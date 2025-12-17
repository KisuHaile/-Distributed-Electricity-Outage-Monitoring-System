package com.electricity.client.ui;

import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import com.electricity.service.DiscoveryService;

public class ClientGUI extends JFrame {
    private JTextField nodeIdField;
    private JButton connectButton;
    private JButton disconnectButton;
    private JButton heartbeatButton;
    private JButton outageStartButton;
    private JButton outageEndButton;
    private JTextArea logArea;
    private JLabel statusLabel;

    private Socket socket;
    private PrintStream out;
    private BufferedReader in;
    private volatile boolean connected = false;
    private Thread listenerThread;
    private Thread heartbeatThread;
    private DiscoveryService discoveryService;

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public ClientGUI() {
        setTitle("Electricity Node Simulator (Client)");
        setSize(500, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Header Panel
        JPanel headerPanel = new JPanel(new GridLayout(3, 1));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel configPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        configPanel.add(new JLabel("Node ID:"));
        nodeIdField = new JTextField("addis_001", 10);
        configPanel.add(nodeIdField);

        connectButton = new JButton("Auto Connect");
        connectButton.addActionListener(e -> startAutoDiscovery());

        // Manual connect button??
        // For simplicity reusing auto connect button behavior logic or adding manual
        // connect dialog
        // But original code: connectButton -> startAutoDiscovery()

        disconnectButton = new JButton("Disconnect");
        disconnectButton.setEnabled(false);
        disconnectButton.addActionListener(e -> disconnect());

        configPanel.add(connectButton);
        configPanel.add(disconnectButton);

        statusLabel = new JLabel("Status: Disconnected", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        statusLabel.setForeground(Color.RED);

        headerPanel.add(new JLabel("Distributed Monitoring Client", SwingConstants.CENTER));
        headerPanel.add(configPanel);
        headerPanel.add(statusLabel);

        add(headerPanel, BorderLayout.NORTH);

        // Main Actions Panel
        JPanel mainPanel = new JPanel(new BorderLayout());
        JPanel actionsPanel = new JPanel(new GridLayout(3, 1, 10, 10));
        actionsPanel.setBorder(BorderFactory.createTitledBorder("Simulate Events"));

        heartbeatButton = new JButton("Send Heartbeat (Normal)");
        heartbeatButton.setBackground(new Color(46, 204, 113));
        heartbeatButton.setForeground(Color.WHITE);
        heartbeatButton.setEnabled(false);
        heartbeatButton.addActionListener(e -> sendHeartbeat());

        outageStartButton = new JButton("Trigger OUTAGE (Fault)");
        outageStartButton.setBackground(new Color(231, 76, 60));
        outageStartButton.setForeground(Color.WHITE);
        outageStartButton.setEnabled(false);
        outageStartButton.addActionListener(e -> sendOutage("START"));

        outageEndButton = new JButton("Resolve OUTAGE (Restored)");
        outageEndButton.setBackground(new Color(52, 152, 219));
        outageEndButton.setForeground(Color.WHITE);
        outageEndButton.setEnabled(false);
        outageEndButton.addActionListener(e -> sendOutage("END"));

        actionsPanel.add(heartbeatButton);
        actionsPanel.add(outageStartButton);
        actionsPanel.add(outageEndButton);

        // Wrap actions panel to keep it fixed height
        JPanel centerWrapper = new JPanel(new BorderLayout());
        centerWrapper.add(actionsPanel, BorderLayout.NORTH);

        // Log Area
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Logs"));

        // Auto-scroll
        DefaultCaret caret = (DefaultCaret) logArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        centerWrapper.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(centerWrapper, BorderLayout.CENTER);

        add(mainPanel, BorderLayout.CENTER);
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            logArea.append("[" + time + "] " + msg + "\n");
        });
    }

    private void connect(String ip, int port) {
        String nodeId = nodeIdField.getText().trim();

        if (nodeId.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter Node ID.");
            return;
        }

        connectButton.setEnabled(false);
        log("Connecting to " + ip + "...");

        new Thread(() -> {
            try {
                socket = new Socket(ip, port);
                out = new PrintStream(socket.getOutputStream());
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                // Auth
                out.println("AUTH|GRID_SEC_2025");
                String resp = in.readLine();

                if ("AUTH_OK".equals(resp)) {
                    log("Connected and Authenticated!");
                    connected = true;
                    updateUIState(true);
                    startListener();
                    startAutoHeartbeat(); // Start automatic heartbeat
                } else {
                    log("Authentication failed: " + resp);
                    closeSocket();
                }
            } catch (Exception e) {
                log("Connection error: " + e.getMessage());
                closeSocket();
                SwingUtilities.invokeLater(() -> connectButton.setEnabled(true));
            }
        }).start();
    }

    private void disconnect() {
        if (discoveryService != null)
            discoveryService.stop();
        if (heartbeatThread != null && heartbeatThread.isAlive())
            heartbeatThread.interrupt();
        closeSocket();
        log("Disconnected.");
    }

    private void startAutoDiscovery() {
        log("Scanning for Leader Server...");
        connectButton.setEnabled(false);

        discoveryService = new DiscoveryService(0, 0, (peer) -> {
        }, null);

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
                            log("Found Leader at " + ip + ":" + port);

                            SwingUtilities.invokeLater(() -> {
                                connect(ip, port);
                            });
                            break;
                        }
                    }
                }
                socket.leaveGroup(group);
            } catch (Exception e) {
                log("Auto-discovery failed: " + e.getMessage());
            } finally {
                SwingUtilities.invokeLater(() -> connectButton.setEnabled(true));
            }
        }).start();
    }

    private void closeSocket() {
        connected = false;
        try {
            if (socket != null)
                socket.close();
        } catch (Exception ignored) {
        }
        updateUIState(false);
    }

    private void updateUIState(boolean isConnected) {
        SwingUtilities.invokeLater(() -> {
            connectButton.setEnabled(!isConnected);
            disconnectButton.setEnabled(isConnected);
            nodeIdField.setEnabled(!isConnected);

            heartbeatButton.setEnabled(isConnected);
            outageStartButton.setEnabled(isConnected);
            outageEndButton.setEnabled(isConnected);

            statusLabel.setText(isConnected ? "Status: Connected" : "Status: Disconnected");
            statusLabel.setForeground(isConnected ? new Color(46, 204, 113) : Color.RED);
        });
    }

    private void startListener() {
        listenerThread = new Thread(() -> {
            try {
                String line;
                while (connected && (line = in.readLine()) != null) {
                    log("Server: " + line);
                }
            } catch (Exception e) {
                if (connected)
                    log("Lost connection: " + e.getMessage());
            } finally {
                if (connected)
                    disconnect();
            }
        });
        listenerThread.start();
    }

    private void startAutoHeartbeat() {
        heartbeatThread = new Thread(() -> {
            log("Auto-Heartbeat started (every 10 seconds)...");
            try {
                while (connected && !Thread.currentThread().isInterrupted()) {
                    // Send heartbeat every 10 seconds
                    Thread.sleep(10000);
                    if (connected) {
                        sendHeartbeat();
                    }
                }
            } catch (InterruptedException e) {
                log("Auto-Heartbeat stopped.");
            }
        });
        heartbeatThread.setDaemon(true); // Daemon thread so it stops when app closes
        heartbeatThread.start();
    }

    private void sendHeartbeat() {
        if (!connected)
            return;
        String nodeId = nodeIdField.getText().trim();
        String powerState = "ON";
        int load = 30 + (int) (Math.random() * 50); // Simulate random load
        String transformer = "ok";
        String msg = String.format("HEARTBEAT|%s|%s|%d|%s", nodeId, powerState, load, transformer);
        send(msg);
    }

    private void sendOutage(String type) {
        if (!connected)
            return;
        String nodeId = nodeIdField.getText().trim();
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

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            new ClientGUI().setVisible(true);
        });
    }
}
