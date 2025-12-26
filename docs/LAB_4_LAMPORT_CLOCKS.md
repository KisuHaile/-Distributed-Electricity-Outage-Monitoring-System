# Lab 4: Implementation of Lamport Logical Clock Synchronization

## Overview

This implementation adds **Lamport Logical Clocks** to the Distributed Electricity Outage Monitoring System, ensuring causal ordering of events across multiple distributed servers without relying on physical clock synchronization.

## Theoretical Background

### The Problem

In distributed systems, physical clocks on different machines can drift, making it impossible to determine the true order of events using timestamps alone. This is critical for our system where:

- Multiple servers process outage reports
- Events must be ordered causally (e.g., "outage detected" must happen before "outage resolved")
- Servers synchronize state via network messages

### Lamport's Solution

Leslie Lamport proposed a logical clock algorithm with three simple rules:

1. **Local Event**: Before each local event, increment the clock
2. **Send Message**: Include the current clock value with every message
3. **Receive Message**: Update clock to `max(local_clock, received_clock) + 1`

This ensures that if event A causally affects event B, then `timestamp(A) < timestamp(B)`.

## Implementation Details

### 1. Core Clock Implementation

**File**: `src/main/java/com/electricity/clock/LamportClock.java`

```java
public class LamportClock {
    private final AtomicLong clock;

    public long tick() {
        return clock.incrementAndGet();
    }

    public long update(long receivedTime) {
        long newTime = Math.max(currentTime, receivedTime) + 1;
        return newTime;
    }
}
```

**Key Features**:

- Thread-safe using `AtomicLong`
- `tick()` for local events
- `update()` for received messages

### 2. Database Schema Changes

**File**: `src/main/resources/schema.sql`

Added `logical_timestamp BIGINT` to:

- `nodes` table: Tracks logical time of last update
- `events` table: Ensures events are causally ordered

**Migration**: Run `MigrateLamportClock.java` to update existing databases.

### 3. Server Integration

**File**: `src/main/java/com/electricity/server/HeadlessServer.java`

```java
private static final LamportClock lamportClock = new LamportClock();

public static void broadcastSync(String msg) {
    long timestamp = lamportClock.tick();  // Increment before sending
    String syncMsg = "SYNC|" + timestamp + "|" + msg;
    // ... send to peers
}
```

**Every server maintains its own logical clock** and:

- Increments before broadcasting sync messages
- Includes timestamp in all SYNC messages

### 4. Message Processing

**File**: `src/main/java/com/electricity/server/ClientHandler.java`

```java
private void handleServerSession(BufferedReader in) {
    String[] parts = message.split("\\|", 2);
    long receivedTimestamp = Long.parseLong(parts[0]);
    HeadlessServer.getClock().update(receivedTimestamp);  // Update on receive
    handleMessage(parts[1]);
}
```

When receiving SYNC messages:

1. Extract timestamp from message
2. Update local clock using Lamport rule
3. Process the actual message

### 5. Event Logging

**File**: `src/main/java/com/electricity/db/EventLogger.java`

```java
public static void logEvent(String nodeId, String eventType,
                           String metadata, long logicalTimestamp) {
    // Store both physical and logical timestamps
    ps.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
    ps.setLong(5, logicalTimestamp);
}
```

All events now store:

- **Physical timestamp**: For human readability
- **Logical timestamp**: For causal ordering

## Message Protocol

### Before Lab 4

```
SYNC|REPORT|NODE_001|220|NORMAL|Region_A
```

### After Lab 4

```
SYNC|42|REPORT|NODE_001|220|NORMAL|Region_A
     ^^
     Lamport timestamp
```

The timestamp `42` means this event is the 42nd causally-ordered event in the distributed system.

## Testing the Implementation

### Test Case 1: Event Ordering

1. Start Server 1 (ID=1)
2. Start Server 2 (ID=2)
3. Client connects to Server 1, reports OUTAGE at LT=5
4. Server 1 broadcasts to Server 2 with LT=6
5. Server 2 receives, updates clock to max(2, 6) + 1 = 7
6. Query events table: All events are causally ordered by `logical_timestamp`

### Test Case 2: Concurrent Events

1. Server 1 processes event A (LT=10)
2. Server 2 processes event B (LT=8) simultaneously
3. Both sync to each other
4. Final clocks: Both servers converge to LT > 10

### Verification Query

```sql
SELECT node_id, event_type, logical_timestamp, timestamp
FROM events
ORDER BY logical_timestamp ASC;
```

Events will be in **causal order**, even if physical timestamps are out of order.

## Benefits for Our System

1. **Accurate Event History**: Outage events are ordered correctly even across servers
2. **Conflict Resolution**: When servers sync, we know which update is "later"
3. **Debugging**: Logical timestamps help trace message flow
4. **No Clock Drift Issues**: Works even if server clocks are hours apart

## Limitations

- **Concurrent events** (events that don't causally affect each other) may have arbitrary ordering
- **Timestamps grow unbounded** (but BIGINT supports 2^63 events)
- **Not a global time**: Cannot determine absolute time, only causal order

## Future Enhancements

- **Vector Clocks**: Track causality per server (Lab extension)
- **Hybrid Clocks**: Combine physical and logical time (TrueTime-style)
- **Clock Compression**: Periodically reset clocks during quiet periods

## References

- Lamport, L. (1978). "Time, Clocks, and the Ordering of Events in a Distributed System"
- Distributed Systems: Principles and Paradigms (Tanenbaum & Van Steen)

---

**Lab Completion Status**: ✅ FULLY IMPLEMENTED
**Date**: December 26, 2025
**System**: Distributed Electricity Outage Monitoring System
