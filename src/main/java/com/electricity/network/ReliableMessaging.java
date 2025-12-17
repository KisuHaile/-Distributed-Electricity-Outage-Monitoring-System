package com.electricity.network;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.*;

/**
 * CORE CONCEPT: Reliable Message Delivery
 * 
 * Demonstrates handling unreliable networks:
 * - Send message
 * - Wait for ACK
 * - Retry on timeout
 * - Give up after max attempts
 * 
 * This shows understanding of:
 * - Partial failures
 * - Network unreliability
 * - At-least-once delivery semantics
 */
public class ReliableMessaging {
    private static final int TIMEOUT_MS = 3000; // 3 seconds
    private static final int MAX_RETRIES = 3;
    private static final int RETRY_DELAY_MS = 1000; // 1 second between retries

    /**
     * Send message with retry logic
     * Returns true if ACK received, false if all retries exhausted
     */
    public static boolean sendWithRetry(String host, int port, String message) {
        int attempt = 0;

        while (attempt < MAX_RETRIES) {
            attempt++;

            try {
                System.out.println("[RELIABLE] Attempt " + attempt + "/" + MAX_RETRIES +
                        " sending: " + message.substring(0, Math.min(50, message.length())));

                // Try to send and get ACK
                if (sendAndWaitForAck(host, port, message)) {
                    System.out.println("[RELIABLE] ✅ ACK received on attempt " + attempt);
                    return true;
                }

                // No ACK received
                System.out.println("[RELIABLE] ⚠️ No ACK on attempt " + attempt);

            } catch (Exception e) {
                System.out.println("[RELIABLE] ❌ Error on attempt " + attempt + ": " + e.getMessage());
            }

            // Wait before retry (except on last attempt)
            if (attempt < MAX_RETRIES) {
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ignored) {
                }
            }
        }

        System.out.println("[RELIABLE] 🔴 FAILED after " + MAX_RETRIES + " attempts");
        return false;
    }

    /**
     * Send message and wait for ACK with timeout
     */
    private static boolean sendAndWaitForAck(String host, int port, String message) throws Exception {
        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(TIMEOUT_MS); // Set read timeout

            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Send auth first (if needed)
            out.println("AUTH|GRID_SEC_2025");
            String authResp = in.readLine();
            if (!"AUTH_OK".equals(authResp)) {
                throw new Exception("Auth failed");
            }

            // Send actual message
            out.println(message);

            // Wait for ACK
            String response = in.readLine();

            // Check if it's an ACK
            return response != null && (response.startsWith("OK") || response.startsWith("ACK"));

        } catch (java.net.SocketTimeoutException e) {
            // Timeout waiting for ACK
            return false;
        }
    }

    /**
     * Critical messages that require ACK
     */
    public static boolean sendCriticalMessage(String host, int port, String message) {
        boolean success = sendWithRetry(host, port, message);

        if (!success) {
            System.err.println("[RELIABLE] 🚨 CRITICAL MESSAGE DELIVERY FAILED: " +
                    message.substring(0, Math.min(50, message.length())));
            // Could log to disk, raise alert, etc.
        }

        return success;
    }

    /**
     * Non-critical messages (send once, don't retry)
     */
    public static void sendBestEffort(String host, int port, String message) {
        try (Socket socket = new Socket(host, port)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            out.println("AUTH|GRID_SEC_2025");
            out.println(message);
        } catch (Exception e) {
            // Silently fail for non-critical messages
            System.out.println("[BEST-EFFORT] Failed: " + e.getMessage());
        }
    }
}
