package com.electricity.test;

import com.electricity.network.NetworkSimulator;

/**
 * Quick test to demonstrate network unreliability simulation
 * 
 * Run this before starting servers to enable network chaos
 */
public class NetworkTest {
    public static void main(String[] args) {
        System.out.println("=== Network Simulation Test ===\n");

        // Test 1: Reliable network (default)
        System.out.println("Test 1: RELIABLE network");
        NetworkSimulator.setPreset("RELIABLE");
        testSending();

        // Test 2: Flaky network
        System.out.println("\nTest 2: FLAKY network (5% drop, 200ms delay)");
        NetworkSimulator.setPreset("FLAKY");
        testSending();

        // Test 3: Unstable network
        System.out.println("\nTest 3: UNSTABLE network (15% drop, 500ms delay)");
        NetworkSimulator.setPreset("UNSTABLE");
        testSending();

        // Test 4: Chaos mode
        System.out.println("\nTest 4: CHAOS mode (30% drop, 1000ms delay)");
        NetworkSimulator.setPreset("CHAOS");
        testSending();

        System.out.println("\n=== Test Complete ===");
        System.out.println("Status: " + NetworkSimulator.getStatus());
    }

    private static void testSending() {
        for (int i = 1; i <= 10; i++) {
            boolean sent = NetworkSimulator.simulateSend("Message-" + i);
            if (sent) {
                System.out.println("  Message " + i + ": DELIVERED");
            } else {
                System.out.println("  Message " + i + ": DROPPED");
            }
        }
    }
}
