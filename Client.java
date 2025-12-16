import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public class Client {

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public static void main(String[] args) {
        BufferedReader keyboard = new BufferedReader(new InputStreamReader(System.in));
        String nodeId = "addis_001";
        String serverIpInput = "localhost";

        try {
            System.out.print("Enter Node ID (default: addis_001): ");
            String input = keyboard.readLine();
            if (input != null && !input.trim().isEmpty()) {
                nodeId = input.trim();
            }

            System.out.print("Enter Server IP(s) (comma separated, default: localhost): ");
            String ipInput = keyboard.readLine();
            if (ipInput != null && !ipInput.trim().isEmpty()) {
                serverIpInput = ipInput.trim();
            }
            String[] serverIps = serverIpInput.split(",");

            // Retry/Failover Loop
            Socket socket = null;
            PrintStream out = null;
            BufferedReader in = null;
            boolean connected = false;

            while (!connected) {
                for (String ip : serverIps) {
                    try {
                        System.out.println("Trying to connect to " + ip.trim() + "...");
                        socket = new Socket(ip.trim(), 8080);
                        out = new PrintStream(socket.getOutputStream());
                        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                        // SECURITY: Send Auth Token
                        out.println("AUTH|GRID_SEC_2025");
                        String authResp = in.readLine();
                        if (!"AUTH_OK".equals(authResp)) {
                            System.err.println("Authentication Rejected by Server!");
                            socket.close();
                            return;
                        }

                        System.out.println("Connected and Authenticated to " + ip.trim());
                        connected = true;
                        break;
                    } catch (IOException e) {
                        System.out.println("Failed to connect to " + ip.trim());
                    }
                }

                if (!connected) {
                    System.out.println("All servers failed. Retrying in 5 seconds...");
                    Thread.sleep(5000);
                }
            }

            // Command Loop
            try {
                System.out.println("Connected to server as " + nodeId);
                System.out.println("Commands: hb, outage_start, outage_end, exit");

                while (true) {
                    System.out.print("Enter command: ");
                    String command = keyboard.readLine();
                    if (command == null)
                        break;

                    switch (command) {
                        case "hb": {
                            String powerState = "ON";
                            int load = 30 + (int) (Math.random() * 50);
                            String transformer = "ok";
                            String msg = String.format("HEARTBEAT|%s|%s|%d|%s", nodeId, powerState, load, transformer);
                            out.println(msg);
                            System.out.println("Sent: " + msg);

                            // Reliable Delivery: Wait for confirmation
                            // In a clearer implementation, we would implement ACK/NACK
                            String resp = in.readLine();
                            System.out.println("Server says: " + resp);
                            break;
                        }
                        case "outage_start": {
                            String eventId = UUID.randomUUID().toString();
                            String ts = LocalDateTime.now().format(DF);
                            String msg = String.format("OUTAGE|%s|%s|OUTAGE_START|%s|line_fault", eventId, nodeId, ts);
                            out.println(msg);
                            System.out.println("Sent: " + msg);
                            String resp = in.readLine();
                            System.out.println("Server says: " + resp);
                            break;
                        }
                        case "outage_end": {
                            String eventId = UUID.randomUUID().toString();
                            String ts = LocalDateTime.now().format(DF);
                            String msg = String.format("OUTAGE|%s|%s|OUTAGE_END|%s|restored", eventId, nodeId, ts);
                            out.println(msg);
                            System.out.println("Sent: " + msg);
                            String resp = in.readLine();
                            System.out.println("Server says: " + resp);
                            break;
                        }
                        case "exit":
                            out.println("exit");
                            System.out.println("Closing connection...");
                            socket.close();
                            return;
                        default:
                            System.out.println("Unknown command.");
                    }
                }
            } finally {
                if (socket != null && !socket.isClosed())
                    socket.close();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
