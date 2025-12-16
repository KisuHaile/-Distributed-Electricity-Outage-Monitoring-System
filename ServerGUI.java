import javax.swing.*;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.io.*;
import java.net.*;

public class ServerGUI extends JFrame {
    private JTextArea logArea;
    private JButton startButton;
    private JButton exitButton;
    private Thread serverThread;
    private ServerSocket serverSocket;
    private volatile boolean running = false;

    public ServerGUI() {
        setTitle("Electricity Monitoring Server");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Header
        JPanel headerPanel = new JPanel();
        headerPanel.setBackground(new Color(44, 62, 80));
        JLabel titleLabel = new JLabel("Distributed Server Monitor");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        headerPanel.add(titleLabel);
        add(headerPanel, BorderLayout.NORTH);

        // Log Area
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 14));
        logArea.setBackground(new Color(30, 30, 30));
        logArea.setForeground(Color.GREEN);
        JScrollPane scrollPane = new JScrollPane(logArea);

        // Auto-scroll
        DefaultCaret caret = (DefaultCaret) logArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        add(scrollPane, BorderLayout.CENTER);

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
        startButton.setEnabled(false);
        running = true;

        serverThread = new Thread(() -> {
            try (ServerSocket ss = new ServerSocket(8080)) {
                this.serverSocket = ss;
                System.out.println("Distributed Server started on port 8080.");

                // Start Node Monitor
                new Thread(new NodeMonitor()).start();

                System.out.println("Waiting for clients...");
                int clientCounter = 0;

                while (running) {
                    try {
                        Socket socket = serverSocket.accept();
                        clientCounter++;
                        System.out.println("Client #" + clientCounter + " connected: " + socket.getInetAddress());

                        ClientHandler handler = new ClientHandler(socket, clientCounter);
                        new Thread(handler).start();
                    } catch (SocketException se) {
                        if (running) {
                            System.err.println("Socket error: " + se.getMessage());
                        }
                    } catch (Exception e) {
                        System.err.println("Error: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                System.err.println("Could not start server: " + e.getMessage());
            } finally {
                running = false;
                SwingUtilities.invokeLater(() -> startButton.setEnabled(true));
            }
        });
        serverThread.start();
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
