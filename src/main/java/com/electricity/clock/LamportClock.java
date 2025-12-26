package com.electricity.clock;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Lab 4: Implementation of Lamport Logical Clock
 * 
 * This class implements Lamport's logical clock algorithm for distributed
 * systems.
 * It ensures causal ordering of events across multiple servers without relying
 * on
 * physical clock synchronization.
 * 
 * Rules:
 * 1. Before each local event, increment the clock
 * 2. When sending a message, include the current clock value
 * 3. When receiving a message, update clock to max(local, received) + 1
 */
public class LamportClock {
    private final AtomicLong clock;

    public LamportClock() {
        this.clock = new AtomicLong(0);
    }

    /**
     * Increment clock for a local event
     * 
     * @return the new clock value
     */
    public long tick() {
        return clock.incrementAndGet();
    }

    /**
     * Update clock when receiving a message
     * 
     * @param receivedTime the timestamp from the received message
     * @return the new clock value
     */
    public long update(long receivedTime) {
        long currentTime;
        long newTime;
        do {
            currentTime = clock.get();
            newTime = Math.max(currentTime, receivedTime) + 1;
        } while (!clock.compareAndSet(currentTime, newTime));

        return newTime;
    }

    /**
     * Get current clock value without incrementing
     * 
     * @return current logical time
     */
    public long getTime() {
        return clock.get();
    }

    /**
     * Set clock to a specific value (used for initialization)
     * 
     * @param time the time to set
     */
    public void setTime(long time) {
        clock.set(time);
    }

    @Override
    public String toString() {
        return "LamportClock[" + clock.get() + "]";
    }
}
