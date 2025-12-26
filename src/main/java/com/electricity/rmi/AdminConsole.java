package com.electricity.rmi;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Lab 2: RMI Admin Console Client
 * 
 * This is a command-line interface that connects to the RMI server
 * and allows remote administration of the distributed system.
 */
public class AdminConsole {

    private AdminService adminService;
    private Scanner scanner;

    public AdminConsole(String host, int port) throws Exception {
        Registry registry = LocateRegistry.getRegistry(host, port);
        adminService = (AdminService) registry.lookup("ElectricityAdmin");
        scanner = new Scanner(System.in);

        System.out.println("============================================================");
        System.out.println("   Distributed Electricity Monitoring - Admin Console      ");
        System.out.println("   Lab 2: Remote Method Invocation (RMI)                   ");
        System.out.println("============================================================");
        System.out.println();
        System.out.println("SUCCESS: Connected to RMI server at " + host + ":" + port);
        System.out.println();
    }

    public void run() {
        boolean running = true;

        while (running) {
            printMenu();
            System.out.print("\n➤ Enter command: ");
            String command = scanner.nextLine().trim();

            try {
                switch (command) {
                    case "1":
                        showAllNodes();
                        break;
                    case "2":
                        showNodeDetails();
                        break;
                    case "3":
                        showRecentEvents();
                        break;
                    case "4":
                        showNodeEvents();
                        break;
                    case "5":
                        triggerVerification();
                        break;
                    case "6":
                        markResolved();
                        break;
                    case "7":
                        showClusterStatus();
                        break;
                    case "8":
                        showServerStats();
                        break;
                    case "9":
                        showLogicalTime();
                        break;
                    case "0":
                        running = false;
                        System.out.println("\nGoodbye!");
                        break;
                    default:
                        System.out.println("ERROR: Invalid command. Please try again.");
                }
            } catch (Exception e) {
                System.err.println("ERROR: " + e.getMessage());
                e.printStackTrace();
            }

            if (running) {
                System.out.println("\nPress Enter to continue...");
                scanner.nextLine();
            }
        }
    }

    private void printMenu() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("                    ADMIN MENU");
        System.out.println("=".repeat(60));
        System.out.println("  1. View All Nodes");
        System.out.println("  2. View Node Details");
        System.out.println("  3. View Recent Events");
        System.out.println("  4. View Node Events");
        System.out.println("  5. Trigger Node Verification");
        System.out.println("  6. Mark Node as Resolved");
        System.out.println("  7. View Cluster Status");
        System.out.println("  8. View Server Statistics");
        System.out.println("  9. View Logical Time (Lamport Clock)");
        System.out.println("  0. Exit");
        System.out.println("=".repeat(60));
    }

    private void showAllNodes() throws Exception {
        System.out.println("\n=== ALL NODES ===");
        System.out.println("-".repeat(100));
        System.out.printf("%-15s %-25s %-12s %-15s %-12s%n",
                "NODE ID", "REGION", "STATUS", "POWER STATE", "LOGICAL TIME");
        System.out.println("-".repeat(100));

        List<Map<String, Object>> nodes = adminService.getAllNodes();
        for (Map<String, Object> node : nodes) {
            System.out.printf("%-15s %-25s %-12s %-15s %-12d%n",
                    node.get("nodeId"),
                    node.get("region"),
                    node.get("status"),
                    node.get("powerState"),
                    node.get("logicalTimestamp"));
        }
        System.out.println("-".repeat(100));
        System.out.println("Total nodes: " + nodes.size());
    }

    private void showNodeDetails() throws Exception {
        System.out.print("\nEnter Node ID: ");
        String nodeId = scanner.nextLine().trim();

        Map<String, Object> details = adminService.getNodeDetails(nodeId);

        System.out.println("\n=== NODE DETAILS: " + nodeId + " ===");
        System.out.println("-".repeat(60));
        details.forEach((key, value) -> {
            System.out.printf("  %-20s: %s%n", key, value);
        });
        System.out.println("-".repeat(60));
    }

    private void showRecentEvents() throws Exception {
        System.out.print("\nNumber of events to show (default 10): ");
        String input = scanner.nextLine().trim();
        int limit = input.isEmpty() ? 10 : Integer.parseInt(input);

        System.out.println("\n=== RECENT EVENTS (Ordered by Lamport Clock) ===");
        System.out.println("-".repeat(120));
        System.out.printf("%-12s %-15s %-25s %-12s %-20s%n",
                "EVENT ID", "NODE ID", "EVENT TYPE", "LOGICAL TIME", "TIMESTAMP");
        System.out.println("-".repeat(120));

        List<Map<String, Object>> events = adminService.getRecentEvents(limit);
        for (Map<String, Object> event : events) {
            System.out.printf("%-12s %-15s %-25s %-12d %-20s%n",
                    event.get("eventId"),
                    event.get("nodeId"),
                    event.get("eventType"),
                    event.get("logicalTimestamp"),
                    event.get("timestamp"));
        }
        System.out.println("-".repeat(120));
    }

    private void showNodeEvents() throws Exception {
        System.out.print("\nEnter Node ID: ");
        String nodeId = scanner.nextLine().trim();
        System.out.print("Number of events (default 10): ");
        String input = scanner.nextLine().trim();
        int limit = input.isEmpty() ? 10 : Integer.parseInt(input);

        System.out.println("\n=== EVENTS FOR NODE: " + nodeId + " ===");
        System.out.println("-".repeat(100));
        System.out.printf("%-12s %-25s %-12s %-30s%n",
                "EVENT ID", "EVENT TYPE", "LOGICAL TIME", "METADATA");
        System.out.println("-".repeat(100));

        List<Map<String, Object>> events = adminService.getNodeEvents(nodeId, limit);
        for (Map<String, Object> event : events) {
            String metadata = (String) event.get("metadata");
            if (metadata != null && metadata.length() > 27) {
                metadata = metadata.substring(0, 27) + "...";
            }
            System.out.printf("%-12s %-25s %-12d %-30s%n",
                    event.get("eventId"),
                    event.get("eventType"),
                    event.get("logicalTimestamp"),
                    metadata);
        }
        System.out.println("-".repeat(100));
    }

    private void triggerVerification() throws Exception {
        System.out.print("\nEnter Node ID to verify: ");
        String nodeId = scanner.nextLine().trim();

        String result = adminService.triggerVerification(nodeId);
        System.out.println("\nSUCCESS: " + result);
    }

    private void markResolved() throws Exception {
        System.out.print("\nEnter Node ID: ");
        String nodeId = scanner.nextLine().trim();
        System.out.print("Enter your name (operator): ");
        String operator = scanner.nextLine().trim();

        String result = adminService.markNodeResolved(nodeId, operator);
        System.out.println("\nSUCCESS: " + result);
    }

    private void showClusterStatus() throws Exception {
        Map<String, Object> status = adminService.getClusterStatus();

        System.out.println("\n=== CLUSTER STATUS ===");
        System.out.println("-".repeat(60));
        System.out.println("  Server ID        : " + status.get("serverId"));
        System.out.println("  Server Port      : " + status.get("serverPort"));
        System.out.println("  Is Leader        : " + (Boolean.TRUE.equals(status.get("isLeader")) ? "[YES]" : "[NO]"));
        System.out.println("  Logical Time     : " + status.get("logicalTime"));
        System.out.println("  Online Nodes     : " + status.get("onlineNodes"));
        System.out.println("  Offline Nodes    : " + status.get("offlineNodes"));
        System.out.println("  Outage Nodes     : " + status.get("outageNodes"));
        System.out.println("-".repeat(60));
    }

    private void showServerStats() throws Exception {
        Map<String, Object> stats = adminService.getServerStats();

        System.out.println("\n=== SERVER STATISTICS ===");
        System.out.println("-".repeat(60));
        System.out.println("  Server ID        : " + stats.get("serverId"));
        System.out.println("  Is Leader        : " + (Boolean.TRUE.equals(stats.get("isLeader")) ? "[YES]" : "[NO]"));
        System.out.println("  Uptime (minutes) : " + stats.get("uptimeMinutes"));
        System.out.println("  Message Count    : " + stats.get("messageCount"));
        System.out.println("  Logical Time     : " + stats.get("logicalTime"));
        System.out.println("-".repeat(60));
    }

    private void showLogicalTime() throws Exception {
        long logicalTime = adminService.getCurrentLogicalTime();
        boolean isLeader = adminService.isLeader();
        int serverId = adminService.getServerId();

        System.out.println("\n=== LAMPORT LOGICAL CLOCK (Lab 4) ===");
        System.out.println("-".repeat(60));
        System.out.println("  Server ID        : " + serverId);
        System.out.println("  Is Leader        : " + (isLeader ? "[YES]" : "[NO]"));
        System.out.println("  Current Time     : " + logicalTime);
        System.out.println("-".repeat(60));
        System.out.println("\nThis represents the " + logicalTime + "th causally-ordered event");
        System.out.println("in the distributed system since startup.");
    }

    public static void main(String[] args) {
        String host = "localhost";
        int port = 1099;

        if (args.length > 0) {
            host = args[0];
        }
        if (args.length > 1) {
            port = Integer.parseInt(args[1]);
        }

        try {
            AdminConsole console = new AdminConsole(host, port);
            console.run();
        } catch (Exception e) {
            System.err.println("ERROR: Failed to connect to RMI server: " + e.getMessage());
            System.err.println("\nMake sure the server is running with RMI enabled.");
            e.printStackTrace();
        }
    }
}
