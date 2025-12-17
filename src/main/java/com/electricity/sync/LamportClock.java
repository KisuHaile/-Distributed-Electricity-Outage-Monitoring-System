package com.electricity.sync;

/**
 * Lab 4: Implementation of Lamport Logical Clock for Clock Synchronization
 * 
 * This implements the Lamport logical clock algorithm for maintaining
 * causal ordering of events in the distributed system.
 */
public class LamportClock {
    private int counter;

    public LamportClock() {
        this.counter = 0;
    }

    /**
     * Increment clock for local event
     */
    public synchronized int tick() {
        counter++;
        return counter;
    }

    /**
     * Update clock when receiving message
     * Rule: clock = max(local_clock, received_clock) + 1
     */
    public synchronized int update(int receivedTimestamp) {
        counter = Math.max(counter, receivedTimestamp) + 1;
        return counter;
    }

    /**
     * Get current timestamp for sending
     */
    public synchronized int sendTimestamp() {
        counter++;
        return counter;
    }

    /**
     * Get current clock value (read-only)
     */
    public synchronized int getTime() {
        return counter;
    }

    /**
     * Set clock to specific value (for testing/initialization)
     */
    public synchronized void setTime(int time) {
        this.counter = time;
    }

    @Override
    public String toString() {
        return "LC:" + counter;
    }
}
