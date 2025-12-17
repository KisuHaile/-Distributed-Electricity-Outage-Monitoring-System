package com.electricity.sync;

import java.util.*;
import java.util.concurrent.*;

/**
 * Lab 6: Implementation of Ricart-Agrawala Mutual Exclusion Algorithm
 * 
 * This implements distributed mutual exclusion for coordinating
 * access to shared resources (like database) across servers.
 */
public class MutualExclusion {
    private final int nodeId;
    private final LamportClock clock;
    private final Set<Integer> peerIds;

    // State
    private volatile boolean requesting = false;
    private volatile boolean inCriticalSection = false;
    private int requestTimestamp;

    // Track replies received
    private final Set<Integer> repliesReceived = new ConcurrentHashMap<Integer, Boolean>().keySet(true);

    // Deferred requests queue
    private final Queue<Request> deferredQueue = new ConcurrentLinkedQueue<>();

    public MutualExclusion(int nodeId, LamportClock clock, Set<Integer> peerIds) {
        this.nodeId = nodeId;
        this.clock = clock;
        this.peerIds = new HashSet<>(peerIds);
    }

    /**
     * Request entry to critical section
     * Broadcasts REQUEST to all peers
     */
    public synchronized void requestCriticalSection() {
        requesting = true;
        requestTimestamp = clock.sendTimestamp();
        repliesReceived.clear();

        System.out.println("[MutEx] Node " + nodeId + " requesting CS at T=" + requestTimestamp);

        // In real implementation, broadcast REQUEST(timestamp, nodeId) to all peers
        // For now, we simulate by setting requesting flag
    }

    /**
     * Handle REQUEST from another node
     * Returns true if we should send REPLY
     */
    public synchronized boolean handleRequest(int senderId, int timestamp) {
        clock.update(timestamp);

        // If not requesting or in CS, always grant
        if (!requesting && !inCriticalSection) {
            System.out.println("[MutEx] Node " + nodeId + " grants request from " + senderId);
            return true;
        }

        // If we are requesting, compare timestamps
        if (requesting) {
            // Grant if their request has higher priority
            // Priority: lower timestamp wins; if equal, lower nodeId wins
            if (timestamp < requestTimestamp ||
                    (timestamp == requestTimestamp && senderId < nodeId)) {
                System.out.println("[MutEx] Node " + nodeId + " grants (lower priority) to " + senderId);
                return true;
            } else {
                // Defer this request
                System.out.println("[MutEx] Node " + nodeId + " defers request from " + senderId);
                deferredQueue.add(new Request(senderId, timestamp));
                return false;
            }
        }

        // In critical section - defer
        deferredQueue.add(new Request(senderId, timestamp));
        return false;
    }

    /**
     * Receive REPLY from a peer
     */
    public synchronized void receiveReply(int senderId) {
        repliesReceived.add(senderId);
        System.out.println("[MutEx] Node " + nodeId + " received reply from " + senderId +
                " (" + repliesReceived.size() + "/" + peerIds.size() + ")");

        // Check if we can enter CS
        if (repliesReceived.size() == peerIds.size() && requesting) {
            enterCriticalSection();
        }
    }

    /**
     * Enter critical section (after receiving all replies)
     */
    private void enterCriticalSection() {
        requesting = false;
        inCriticalSection = true;
        System.out.println("[MutEx] ✅ Node " + nodeId + " ENTERED critical section");
    }

    /**
     * Exit critical section and send deferred REPLYs
     */
    public synchronized void releaseCriticalSection() {
        if (!inCriticalSection) {
            System.err.println("[MutEx] ERROR: Not in critical section!");
            return;
        }

        inCriticalSection = false;
        clock.tick();

        System.out.println("[MutEx] Node " + nodeId + " EXITED critical section");

        // Send REPLY to all deferred requests
        while (!deferredQueue.isEmpty()) {
            Request req = deferredQueue.poll();
            System.out.println("[MutEx] Sending deferred REPLY to " + req.senderId);
            // In real implementation: send REPLY message to req.senderId
        }
    }

    /**
     * Check if currently in critical section
     */
    public boolean isInCriticalSection() {
        return inCriticalSection;
    }

    /**
     * Get current request timestamp
     */
    public int getRequestTimestamp() {
        return requestTimestamp;
    }

    /**
     * Update peer list
     */
    public synchronized void updatePeers(Set<Integer> newPeerIds) {
        this.peerIds.clear();
        this.peerIds.addAll(newPeerIds);
    }

    // Helper class to represent a request
    private static class Request {
        final int senderId;
        final int timestamp;

        Request(int senderId, int timestamp) {
            this.senderId = senderId;
            this.timestamp = timestamp;
        }
    }
}
