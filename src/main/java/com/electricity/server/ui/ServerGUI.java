package com.electricity.server.ui;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.Vector;

import com.electricity.service.ElectionManager;
import com.electricity.service.DiscoveryService;
import com.electricity.db.DBConnection;
import com.electricity.monitor.NodeMonitor;
import com.electricity.model.Peer;
import com.electricity.server.ClientHandler;

public class ServerGUI extends JFrame {
    private JTextArea logArea;
    private JTable dashboardTable;
    private DefaultTableModel tableModel;
    private JButton startButton;
    private JButton exitButton;
    private JTextField idField;
    private JTextField portField;
    private JTextField peersField;
    private JTextField dbHostField;
    private Thread serverThread;
    private ServerSocket serverSocket;
    private volatile boolean running = false;
    private ElectionManager electionManager;
    private DiscoveryService discoveryService;

    public ServerGUI() {
        setTitle("Electricity Monitoring Server");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Header
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));

        JPanel headerPanel = new JPanel();
        headerPanel.setBackground(new Color(44, 62, 80));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // Padding
        headerPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, 60)); // Limit height
        JLabel titleLabel = new JLabel("Distributed Server Monitor");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        headerPanel.add(titleLabel);
        topPanel.add(headerPanel);

        // Config Panel
        JPanel configPanel = new JPanel(new GridLayout(4, 2, 5, 5));
        configPanel.setBorder(BorderFactory.createTitledBorder("Cluster Configuration"));

        idField = new JTextField("1");
        portField = new JTextField("8080");
        peersField = new JTextField("2:localhost:8081"); // Default example
        dbHostField = new JTextField("localhost"); // Database Host

        configPanel.add(new JLabel("Server ID:"));
        configPanel.add(idField);
        configPanel.add(new JLabel("Port:"));
        configPanel.add(portField);
        configPanel.add(new JLabel("Database Host:"));
        configPanel.add(dbHostField);
        configPanel.add(new JLabel("Peers (id:host:port, ...):"));
        configPanel.add(peersField);

        topPanel.add(configPanel);

        add(topPanel, BorderLayout.NORTH);

        // Log Area
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 14));
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(Color.GREEN);
        // Create a Split Pane: Top for Table, Bottom for Logs
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.5);

        // Dashboard Table
        // Dashboard Table
        String[] columnNames = { "Node ID", "Status", "Load (%)", "Power State", "Transformer", "Last Seen" };
        tableModel = new DefaultTableModel(columnNames, 0);
        dashboardTable = new JTable(tableModel);
        dashboardTable.setFillsViewportHeight(true);
        JScrollPane tableScrollPane = new JScrollPane(dashboardTable);
        tableScrollPane.setBorder(BorderFactory.createTitledBorder("Live Grid Status"));

        splitPane.setTopComponent(tableScrollPane);

        add(splitPane, BorderLayout.CENTER);

        // Log Area (Bottom)
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 14));
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(Color.GREEN);
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createTitledBorder("Server Logs"));

        // Auto-scroll
        DefaultCaret caret = (DefaultCaret) logArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        splitPane.setBottomComponent(logScrollPane);

        // Controls
        JPanel controlPanel = new JPanel();
        startButton = new JButton("Start Server");
        startButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        startButton.addActionListener(e -> startServer());

        exitButton = new JButton("Exit");
        exitButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        exitButton.addActionListener(e -> System.exit(0));

        controlPanel.add(startButton);
        controlPanel.add(exitButton);
        add(controlPanel, BorderLayout.SOUTH);

        // Redirect System.out and System.err
        redirectSystemStreams();
    }

    private void redirectSystemStreams() {
        OutputStream out = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                updateLog(String.valueOf((char) b));
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                updateLog(new String(b, off, len));
            }
        };
        System.setOut(new PrintStream(out, true));
        System.setErr(new PrintStream(out, true));
    }

    private void updateLog(String text) {
        SwingUtilities.invokeLater(() -> logArea.append(text));
    }

    private void startServer() {
        if (running)
            return;

        int port;
        int myId;
        String peersStr;
        String dbHost;

        try {
            port = Integer.parseInt(portField.getText().trim());
            myId = Integer.parseInt(idField.getText().trim());
            peersStr = peersField.getText().trim();
            dbHost = dbHostField.getText().trim();
            if (dbHost.isEmpty())
                dbHost = "localhost";
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid ID or Port number.", "Config Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        startButton.setEnabled(false);
        idField.setEnabled(false);
        portField.setEnabled(false);
        peersField.setEnabled(false);
        dbHostField.setEnabled(false);
        running = true;

        // Configure DB
        DBConnection.configure(dbHost);

        serverThread = new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(port)) {
                this.serverSocket = ss;
                System.out.println("Distributed Server " + myId + " started on port " + port + ".");

                // Setup Election Manager
                NodeMonitor monitor = new NodeMonitor();
                electionManager = new ElectionManager(myId, monitor);

                // Parse Peers
                if (!peersStr.isEmpty()) {
                    String[] peerList = peersStr.split(",");
                    for (String p : peerList) {
                        try {
                            String[] parts = p.trim().split(":");
                            if (parts.length == 3) {
                                int pId = Integer.parseInt(parts[0]);
                                String pHost = parts[1];
                                int pPort = Integer.parseInt(parts[2]);
                                electionManager.addPeer(new Peer(pId, pHost, pPort));
                            }
                        } catch (Exception ex) {
                            System.err.println("Skipping invalid peer config: " + p);
                        }
                    }
                }

                // START DISCOVERY SERVICE (Automatic Peer Detection)
                discoveryService = new DiscoveryService(myId, port, (peer) -> {
                    electionManager.addPeerIfNotExists(peer);
                }, () -> electionManager.isLeader());
                new Thread(discoveryService).start();
                System.out.println("Discovery Service started.");

                // Start Monitor and Election
                Thread monitorThread = new Thread(monitor);
                monitorThread.start();

                // Trigger election async
                new Thread(() -> {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                    }
                    electionManager.startElection();
                }).start();

                System.out.println("Waiting for connections...");
                int clientCounter = 0;

                while (running) {
                    try {
                        Socket socket = serverSocket.accept();
                        clientCounter++;
                        // Log connection (briefly)
                        // System.out.println("Connection from: " + socket.getInetAddress());

                        ClientHandler handler = new ClientHandler(socket, clientCounter, electionManager);
                        new Thread(handler).start();
                    } catch (SocketException se) {
                        if (running) {
                            System.err.println("Socket error: " + se.getMessage());
                        }
                    } catch (Exception e) {
                        if (e.getMessage() != null && e.getMessage().contains("last packet sent successfully")) {
                            // Suppress
                        } else {
                            System.err.println("Error: " + e.getMessage());
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Could not start server: " + e.getMessage());
            } finally {
                running = false;
                if (discoveryService != null)
                    discoveryService.stop();
                SwingUtilities.invokeLater(() -> {
                    startButton.setEnabled(true);
                    idField.setEnabled(true);
                    portField.setEnabled(true);
                    peersField.setEnabled(true);
                    dbHostField.setEnabled(true);
                });
            }
        });
        serverThread.start();

        // Start UI Dashboard Updater
        new Thread(this::updateDashboardLoop).start();
    }

    private void updateDashboardLoop() {
        while (running) {
            try {
                Thread.sleep(2000); // Update every 2 seconds
                // Fetch data from database instead of just in-memory cache
                SwingUtilities.invokeLater(() -> updateTableFromDatabase());
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private void updateTableFromDatabase() {
        try (java.sql.Connection conn = DBConnection.getConnection()) {
            String query = "SELECT node_id, status, last_load_percent, last_power_state, transformer_health, " +
                    "DATE_FORMAT(last_seen, '%H:%i:%s') as last_seen_time " +
                    "FROM nodes ORDER BY node_id";

            try (java.sql.Statement stmt = conn.createStatement();
                    java.sql.ResultSet rs = stmt.executeQuery(query)) {

                // Clear existing rows
                tableModel.setRowCount(0);

                // Add rows from database
                while (rs.next()) {
                    String nodeId = rs.getString("node_id");
                    String status = rs.getString("status");
                    String load = rs.getString("last_load_percent");
                    String power = rs.getString("last_power_state");
                    String transformer = rs.getString("transformer_health");
                    String lastSeen = rs.getString("last_seen_time");

                    tableModel.addRow(new Object[] {
                            nodeId,
                            status != null ? status : "UNKNOWN",
                            load != null ? load : "0",
                            power != null ? power : "unknown",
                            transformer != null ? transformer : "unknown",
                            lastSeen != null ? lastSeen : "never"
                    });
                }
            }
        } catch (Exception e) {
            // Database error - log it but don't crash the UI
            if (e.getMessage() != null && !e.getMessage().contains("last packet sent successfully")) {
                System.err.println("[Dashboard] Error updating table: " + e.getMessage());
            }
        }
    }

    // Keep old method for compatibility, but now unused
    private void updateTable(Map<String, String[]> snapshot) {
        // Clear and reload (simple approach for now)
        tableModel.setRowCount(0);
        for (String[] row : snapshot.values()) {
            tableModel.addRow(row);
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            new ServerGUI().setVisible(true);
        });
    }
}
