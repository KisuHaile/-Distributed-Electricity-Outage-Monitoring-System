import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class ClientGUI extends JFrame {
    private JTextField nodeIdField;
    private JTextField serverIpField;
    private JButton connectButton;
    private JButton disconnectButton;

    // Control buttons
    private JButton heartbeatButton;
    private JButton outageStartButton;
    private JButton outageEndButton;

    private JTextArea logArea;
    private JLabel statusLabel;

    private Socket socket;
    private PrintStream out;
    private BufferedReader in;
    private Thread listenerThread;
    private volatile boolean connected = false;

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public ClientGUI() {
        setTitle("Electricity Client Node");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        // Header
        JPanel headerPanel = new JPanel();
        headerPanel.setBackground(new Color(52, 152, 219));
        headerPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        JLabel titleLabel = new JLabel("Client Node Controller");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        headerPanel.add(titleLabel);
        add(headerPanel, BorderLayout.NORTH);

        // Main Content
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Setup Panel (Top)
        JPanel setupPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        setupPanel.setBorder(BorderFactory.createTitledBorder("Connection Settings"));

        setupPanel.add(new JLabel("Node ID:"));
        nodeIdField = new JTextField("addis_001", 10);
        setupPanel.add(nodeIdField);

        setupPanel.add(new JLabel("Server IP:"));
        serverIpField = new JTextField("localhost", 15);
        setupPanel.add(serverIpField);

        connectButton = new JButton("Connect");
        connectButton.setBackground(new Color(46, 204, 113));
        connectButton.setForeground(Color.WHITE);
        connectButton.addActionListener(e -> connect());
        setupPanel.add(connectButton);

        disconnectButton = new JButton("Disconnect");
        disconnectButton.setBackground(new Color(231, 76, 60));
        disconnectButton.setForeground(Color.WHITE);
        disconnectButton.setEnabled(false);
        disconnectButton.addActionListener(e -> disconnect());
        setupPanel.add(disconnectButton);

        statusLabel = new JLabel("Status: Disconnected");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        setupPanel.add(Box.createHorizontalStrut(20));
        setupPanel.add(statusLabel);

        mainPanel.add(setupPanel, BorderLayout.NORTH);

        // Actions Panel (Center)
        JPanel actionsPanel = new JPanel(new GridLayout(1, 3, 10, 10));
        actionsPanel.setBorder(BorderFactory.createTitledBorder("Actions"));

        heartbeatButton = new JButton("Send Heartbeat");
        heartbeatButton.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        heartbeatButton.addActionListener(e -> sendHeartbeat());
        heartbeatButton.setEnabled(false);

        outageStartButton = new JButton("Report Outage START");
        outageStartButton.setBackground(new Color(231, 76, 60));
        outageStartButton.setForeground(Color.WHITE);
        outageStartButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        outageStartButton.addActionListener(e -> sendOutage("START"));
        outageStartButton.setEnabled(false);

        outageEndButton = new JButton("Report Outage END");
        outageEndButton.setBackground(new Color(46, 204, 113));
        outageEndButton.setForeground(Color.WHITE);
        outageEndButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        outageEndButton.addActionListener(e -> sendOutage("END"));
        outageEndButton.setEnabled(false);

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

    private void connect() {
        String ip = serverIpField.getText().trim();
        int port = 8080;
        String nodeId = nodeIdField.getText().trim();

        if (ip.isEmpty() || nodeId.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter Node ID and Server IP.");
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
        updateUIState(false);
    }

    private void updateUIState(boolean isConnected) {
        SwingUtilities.invokeLater(() -> {
            connectButton.setEnabled(!isConnected);
            disconnectButton.setEnabled(isConnected);
            nodeIdField.setEnabled(!isConnected);
            serverIpField.setEnabled(!isConnected);

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
