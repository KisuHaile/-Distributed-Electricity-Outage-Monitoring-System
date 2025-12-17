package com.electricity.service;

import com.electricity.model.Peer;
import com.electricity.monitor.NodeMonitor;
import com.electricity.sync.LamportClock;
import com.electricity.sync.MutualExclusion;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Election Manager with integrated:
 * - Lab 5: Bully Election Algorithm
 * - Lab 4: Lamport Logical Clock
 * - Lab 6: Ricart-Agrawala Mutual Exclusion
 * - Lab 7: Multi-threaded communication
 */
public class ElectionManager {
    private int myId;
    private NodeMonitor monitor;
    private List<Peer> peers = new ArrayList<>();
    private ConcurrentHashMap<String, String[]> nodeStates = new ConcurrentHashMap<>();
    private volatile boolean isLeader = false;

    // Lab 4: Lamport Logical Clock
    private LamportClock lamportClock;

    // Lab 6: Mutual Exclusion
    private MutualExclusion mutualExclusion;

    public ElectionManager(int myId, NodeMonitor monitor) {
        this.myId = myId;
        this.monitor = monitor;

        // Initialize Lamport Clock
        this.lamportClock = new LamportClock();

        // Initialize Mutual Exclusion (peers will be added later)
        this.mutualExclusion = new MutualExclusion(myId, lamportClock, new HashSet<>());

        System.out.println("[Lab 4] Lamport Clock initialized for Node " + myId);
        System.out.println("[Lab 6] Mutual Exclusion initialized for Node " + myId);
    }

    public synchronized void addPeer(Peer p) {
        peers.add(p);
    }

    public synchronized void addPeerIfNotExists(Peer p) {
        boolean exists = peers.stream().anyMatch(peer -> peer.getId() == p.getId());
        if (!exists) {
            addPeer(p);
            System.out.println("Discovered Peer: " + p.getId() + " at " + p.getHost());
        }
    }

    public boolean isLeader() {
        return isLeader;
    }

    public void startElection() {
        System.out.println("Starting Election...");
        int highestId = myId;
        for (Peer p : peers) {
            if (p.getId() > highestId) {
                highestId = p.getId();
            }
        }
        if (highestId == myId) {
            isLeader = true;
            monitor.setActive(true);
            System.out.println("I am the Leader!");
            broadcast("COORDINATOR|" + myId);
        } else {
            isLeader = false;
            monitor.setActive(false);
            // Send ELECTION to higher nodes? Simplified: just wait.
            // Or broadcast I am looking for leader
        }
    }

    public String processMessage(String msg) {
        // Handle inter-server messages
        String[] parts = msg.split("\\|", -1);
        if (parts.length == 0)
            return "OK";

        String type = parts[0].trim().toUpperCase();

        switch (type) {
            case "COORDINATOR":
                // Someone else won the election
                isLeader = false;
                monitor.setActive(false);
                return "ACK";

            case "HEARTBEAT":
                // HEARTBEAT|nodeId|powerState|load|transformer|timestamp
                if (parts.length >= 6) {
                    String nodeId = parts[1];
                    String powerState = parts[2];
                    String load = parts[3];
                    String transformer = parts[4];
                    String lastSeen = parts[5];

                    // Update local cache
                    updateLocalNodeState(nodeId, "Alive", load, powerState, transformer, lastSeen);

                    // Also persist to database (ONLY IF LEADER)
                    if (isLeader()) {
                        syncHeartbeatToDB(nodeId, powerState, load, transformer);
                    }
                }
                return "OK|SYNC";

            case "OUTAGE":
                // OUTAGE|eventId|nodeId|eventType|timestamp|metadata
                if (parts.length >= 6) {
                    // (ONLY IF LEADER)
                    if (isLeader()) {
                        syncOutageToDB(parts[1], parts[2], parts[3], parts[4], parts[5]);
                    }
                }
                return "OK|SYNC";

            default:
                return "OK";
        }
    }

    private void syncHeartbeatToDB(String nodeId, String powerState, String load, String transformer) {
        try (java.sql.Connection conn = com.electricity.db.DBConnection.getConnection()) {
            java.sql.Timestamp now = new java.sql.Timestamp(System.currentTimeMillis());
            String upsert = "INSERT INTO nodes (node_id, last_seen, last_power_state, last_load_percent, transformer_health, status) "
                    +
                    "VALUES (?, ?, ?, ?, ?, 'ONLINE') " +
                    "ON DUPLICATE KEY UPDATE last_seen = VALUES(last_seen), last_power_state = VALUES(last_power_state), "
                    +
                    "last_load_percent = VALUES(last_load_percent), transformer_health = VALUES(transformer_health), status='ONLINE'";
            try (java.sql.PreparedStatement ps = conn.prepareStatement(upsert)) {
                ps.setString(1, nodeId);
                ps.setTimestamp(2, now);
                ps.setString(3, powerState);
                ps.setInt(4, Integer.parseInt(load));
                ps.setString(5, transformer);
                ps.executeUpdate();
            }
            System.out.println("  [SYNC] Replicated HEARTBEAT for node " + nodeId);
        } catch (Exception e) {
            System.err.println("Failed to sync heartbeat to DB: " + e.getMessage());
        }
    }

    private void syncOutageToDB(String eventId, String nodeId, String eventType, String timestampStr, String metadata) {
        try (java.sql.Connection conn = com.electricity.db.DBConnection.getConnection()) {
            java.sql.Timestamp ts;
            try {
                java.time.LocalDateTime ldt = java.time.LocalDateTime.parse(timestampStr,
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
                ts = java.sql.Timestamp.valueOf(ldt);
            } catch (Exception e) {
                ts = new java.sql.Timestamp(System.currentTimeMillis());
            }

            String insertEvent = "INSERT INTO events (event_id, node_id, event_type, timestamp, metadata) VALUES (?, ?, ?, ?, ?)";
            try (java.sql.PreparedStatement ps = conn.prepareStatement(insertEvent)) {
                ps.setString(1, eventId);
                ps.setString(2, nodeId);
                ps.setString(3, eventType);
                ps.setTimestamp(4, ts);
                ps.setString(5, metadata);
                ps.executeUpdate();
                System.out.println("  [SYNC] Replicated OUTAGE event " + eventId);
            } catch (java.sql.SQLIntegrityConstraintViolationException dup) {
                // Already exists, that's fine
                System.out.println("  [SYNC] OUTAGE " + eventId + " already exists (OK)");
            }
        } catch (Exception e) {
            System.err.println("Failed to sync outage to DB: " + e.getMessage());
        }
    }

    public void updateLocalNodeState(String nodeId, String status, String load, String power, String transformer,
            String lastSeen) {
        nodeStates.put(nodeId, new String[] { nodeId, status, load, power, transformer, lastSeen });
    }

    public void broadcast(String msg) {
        for (Peer p : peers) {
            new Thread(() -> {
                try (Socket s = new Socket(p.getHost(), p.getPort());
                        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                        PrintWriter out = new PrintWriter(s.getOutputStream(), true)) {

                    // Authenticate as server peer
                    out.println("AUTH|SERVER_PEER");

                    // Wait for AUTH_OK
                    String authResponse = in.readLine();
                    if (authResponse == null || !authResponse.equals("AUTH_OK")) {
                        System.err.println("  [BROADCAST] Auth failed to peer " + p.getId() + ": " + authResponse);
                        return;
                    }

                    // Send the actual message
                    out.println(msg);

                    // Optionally read response
                    String response = in.readLine();
                    if (response != null && response.startsWith("OK")) {
                        // Success - suppress log to avoid spam
                        // System.out.println(" [BROADCAST] Peer " + p.getId() + " acknowledged: " +
                        // response);
                    }
                } catch (IOException e) {
                    // Peer might be down, suppress error to avoid log spam
                    // System.err.println(" [BROADCAST] Failed to reach peer " + p.getId() + ": " +
                    // e.getMessage());
                }
            }).start();
        }
    }

    public Map<String, String[]> getNodeStateSnapshot() {
        return nodeStates;
    }
}
