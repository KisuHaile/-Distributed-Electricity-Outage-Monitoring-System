package com.electricity.network;

import java.util.Random;

/**
 * CORE CONCEPT: Network Unreliability Simulation
 * 
 * THIS UPGRADES YOUR PROJECT FROM "SOCKET PROGRAMMING" TO "DISTRIBUTED SYSTEMS"
 * 
 * Real networks are unreliable:
 * - Messages can be delayed
 * - Messages can be dropped
 * - Messages can be reordered (not simulated here for simplicity)
 * 
 * This class simulates these failures to test system robustness.
 */
public class NetworkSimulator {
    private static final Random random = new Random();

    // Configuration (can be changed at runtime)
    private static volatile double DROP_RATE = 0.0;
    private static volatile int MAX_DELAY_MS = 0;
    private static volatile boolean ENABLED = false;

    /**
     * Enable network simulation with specified parameters
     */
    public static void enable(double dropRate, int maxDelayMs) {
        DROP_RATE = Math.max(0.0, Math.min(1.0, dropRate));
        MAX_DELAY_MS = Math.max(0, maxDelayMs);
        ENABLED = true;

        System.out.println("[NETWORK SIM] ENABLED");
        System.out.println("   Drop Rate: " + (int) (DROP_RATE * 100) + "%");
        System.out.println("   Max Delay: " + MAX_DELAY_MS + "ms");
    }

    /**
     * Disable network simulation (normal reliable network)
     */
    public static void disable() {
        ENABLED = false;
        System.out.println("[NETWORK SIM] DISABLED - using reliable network");
    }

    /**
     * Should this message be dropped?
     */
    public static boolean shouldDrop() {
        if (!ENABLED || DROP_RATE == 0.0) {
            return false;
        }

        boolean drop = random.nextDouble() < DROP_RATE;

        if (drop) {
            System.out.println("[NETWORK SIM] MESSAGE DROPPED");
        }

        return drop;
    }

    /**
     * Simulate network delay
     */
    public static void simulateDelay() {
        if (!ENABLED || MAX_DELAY_MS == 0) {
            return;
        }

        int delayMs = random.nextInt(MAX_DELAY_MS);

        if (delayMs > 0) {
            System.out.println("[NETWORK SIM] Delaying " + delayMs + "ms");
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException ignored) {
            }
        }
    }

    /**
     * Simulate sending a message through unreliable network
     */
    public static boolean simulateSend(String messageType) {
        if (!ENABLED) {
            return true;
        }

        if (shouldDrop()) {
            System.out.println("[NETWORK SIM] " + messageType + " DROPPED in transit");
            return false;
        }

        simulateDelay();

        return true;
    }

    /**
     * Get current configuration
     */
    public static String getStatus() {
        if (!ENABLED) {
            return "Network Simulation: OFF (Reliable)";
        }

        return String.format("Network Simulation: ON | Drop: %d%% | Delay: 0-%dms",
                (int) (DROP_RATE * 100), MAX_DELAY_MS);
    }

    /**
     * Preset configurations
     */
    public static void setPreset(String preset) {
        switch (preset.toUpperCase()) {
            case "RELIABLE":
                disable();
                break;

            case "FLAKY":
                enable(0.05, 200);
                System.out.println("Preset: FLAKY network (minor issues)");
                break;

            case "UNSTABLE":
                enable(0.15, 500);
                System.out.println("Preset: UNSTABLE network (significant issues)");
                break;

            case "CHAOS":
                enable(0.30, 1000);
                System.out.println("Preset: CHAOS mode (extreme conditions)");
                break;

            default:
                System.err.println("Unknown preset: " + preset);
        }
    }
}
