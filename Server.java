import java.io.*;
import java.net.*;

public class Server {

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(8080)) {
            System.out.println("Distributed Server started on port 8080.");

            // EDGE CASE HANDLING: Start Dead Node Monitor
            Thread monitorThread = new Thread(new NodeMonitor());
            monitorThread.start();

            System.out.println("Waiting for clients...");

            int clientCounter = 0;

            while (true) {
                try {
                    Socket socket = serverSocket.accept();
                    clientCounter++;
                    System.out.println("Client #" + clientCounter + " connected: " + socket.getInetAddress());

                    // Create a new thread for this client
                    ClientHandler handler = new ClientHandler(socket, clientCounter);
                    Thread thread = new Thread(handler);
                    thread.start();
                } catch (Exception e) {
                    System.err.println("Error accepting client connection: " + e.getMessage());
                }
            }

        } catch (IOException e) {
            System.err.println("Fatal: Could not start server on port 8080.");
            System.err.println("Is another server already running? Try closing all Java windows.");
            e.printStackTrace();
        }
    }

}
