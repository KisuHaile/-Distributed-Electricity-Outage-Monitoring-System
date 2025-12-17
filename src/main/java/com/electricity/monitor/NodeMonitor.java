package com.electricity.monitor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import com.electricity.db.DBConnection;
import com.electricity.model.NodeState;

/**
 * CORE CONCEPT: Leader-Based Failure Detection with Uncertainty
 * 
 * ACADEMIC VALUE:
 * - Only LEADER monitors (demonstrates leader-based coordination)
 * - Implements gradual failure detection (ONLINE → SUSPECTED → OFFLINE)
 * - Shows understanding of failure uncertainty
 * 
 * KEY INSIGHT:
 * We don't declare nodes dead immediately!
 * This models real-world distributed systems accurately.
 */
public class NodeMonitor implements Runnable {

    private volatile boolean active = false;

    public void setActive(boolean active) {
        this.active = active;
        System.out.println("[Monitor] State changed to: " + (active ? "ACTIVE (Leader)" : "PASSIVE (Follower)"));

        if (active) {
            System.out.println("[Monitor] As LEADER, I am responsible for failure detection");
        }
    }

    @Override
    public void run() {
        System.out.println("[Monitor] Failure Detector started (Waiting for election...).");

        while (true) {
            try {
                // Check every 10 seconds (more responsive)
                Thread.sleep(10000);

                if (!active) {
                    continue; // Only leader monitors
                }

                try (Connection conn = DBConnection.getConnection()) {

                    // STEP 1: Mark nodes as SUSPECTED if missed one heartbeat
                    String suspectSQL = "UPDATE nodes SET status='SUSPECTED' " +
                            "WHERE last_seen < (NOW() - INTERVAL " + NodeState.SUSPECT_THRESHOLD + " SECOND) " +
                            "AND last_seen >= (NOW() - INTERVAL " + NodeState.OFFLINE_THRESHOLD + " SECOND) " +
                            "AND status='ONLINE'";

                    try (PreparedStatement ps = conn.prepareStatement(suspectSQL)) {
                        int suspected = ps.executeUpdate();
                        if (suspected > 0) {
                            System.out.println(
                                    "[Monitor] " + suspected + " node(s) now SUSPECTED (missed heartbeat)");
                        }
                    }

                    // STEP 2: Mark nodes as OFFLINE if suspected long enough
                    String offlineSQL = "UPDATE nodes SET status='OFFLINE' " +
                            "WHERE last_seen < (NOW() - INTERVAL " + NodeState.OFFLINE_THRESHOLD + " SECOND) " +
                            "AND (status='ONLINE' OR status='SUSPECTED')";

                    try (PreparedStatement ps = conn.prepareStatement(offlineSQL)) {
                        int offline = ps.executeUpdate();
                        if (offline > 0) {
                            System.out.println(
                                    "[Monitor] " + offline + " node(s) confirmed OFFLINE (no heartbeat for " +
                                            NodeState.OFFLINE_THRESHOLD + "s)");
                        }
                    }

                    // STEP 3: Detect RECOVERED nodes (were OFFLINE, now sending heartbeats)
                    String recoveredSQL = "UPDATE nodes SET status='RECOVERED' " +
                            "WHERE last_seen >= (NOW() - INTERVAL " + NodeState.SUSPECT_THRESHOLD + " SECOND) " +
                            "AND status='OFFLINE'";

                    try (PreparedStatement ps = conn.prepareStatement(recoveredSQL)) {
                        int recovered = ps.executeUpdate();
                        if (recovered > 0) {
                            System.out.println("[Monitor] " + recovered + " node(s) RECOVERED from failure!");
                        }
                    }

                    // STEP 4: Transition RECOVERED → ONLINE (after grace period)
                    String normalizeSQL = "UPDATE nodes SET status='ONLINE' " +
                            "WHERE status='RECOVERED' " +
                            "AND last_seen < (NOW() - INTERVAL 5 SECOND)"; // After 5s, consider fully recovered

                    try (PreparedStatement ps = conn.prepareStatement(normalizeSQL)) {
                        ps.executeUpdate();
                        // Silent - this is just housekeeping
                    }

                } catch (SQLException e) {
                    if (e.getMessage() != null && e.getMessage().contains("last packet sent successfully")) {
                        // Suppress MySQL warning
                    } else {
                        System.err.println("[Monitor] Database error: " + e.getMessage());
                    }
                }

            } catch (InterruptedException e) {
                System.out.println("[Monitor] Monitor interrupted.");
                break;
            } catch (Exception e) {
                System.err.println("[Monitor] Unexpected error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
