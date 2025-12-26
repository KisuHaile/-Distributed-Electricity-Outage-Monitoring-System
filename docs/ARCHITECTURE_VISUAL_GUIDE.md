# System Architecture - Visual Guide

## Complete System Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         ADMINISTRATION LAYER                                │
│                                                                             │
│  ┌──────────────────┐                                                       │
│  │  Admin Console   │  Lab 2: RMI                                          │
│  │  (Remote)        │  - View all nodes                                    │
│  │                  │  - Trigger verification                              │
│  │  Commands:       │  - Mark resolved                                     │
│  │  1. View Nodes   │  - View events (by Lamport time!)                   │
│  │  2. Trigger      │  - Monitor cluster                                   │
│  │  3. Resolve      │                                                       │
│  └────────┬─────────┘                                                       │
│           │                                                                 │
│           │ RMI (Port 1099)                                                │
│           │                                                                 │
└───────────┼─────────────────────────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         SERVER CLUSTER LAYER                                │
│                                                                             │
│  ┌─────────────────────────────────┐   ┌─────────────────────────────────┐ │
│  │  Server 1 (Leader)              │   │  Server 2 (Follower)            │ │
│  │  ID: 1, Port: 9000              │   │  ID: 2, Port: 9001              │ │
│  │  ┌──────────────────────────┐   │   │  ┌──────────────────────────┐   │ │
│  │  │ Lamport Clock: 42        │   │   │  │ Lamport Clock: 41        │   │ │
│  │  │ (Lab 4)                  │   │   │  │ (Lab 4)                  │   │ │
│  │  └──────────────────────────┘   │   │  └──────────────────────────┘   │ │
│  │                                  │   │                                  │ │
│  │  Components:                     │   │  Components:                     │ │
│  │  ✓ RMI Server (Lab 2)           │   │  ✓ RMI Server (Lab 2)           │ │
│  │  ✓ Web Server (Port 3000)       │   │  ✓ Web Server (Port 3001)       │ │
│  │  ✓ TCP Server (Port 9000)       │   │  ✓ TCP Server (Port 9001)       │ │
│  │  ✓ Discovery Service (Lab 5)    │   │  ✓ Discovery Service (Lab 5)    │ │
│  │  ✓ Leader Election (Lab 5)      │   │  ✓ Leader Election (Lab 5)      │ │
│  │                                  │   │                                  │ │
│  │  Threads (Lab 7):                │   │  Threads (Lab 7):                │ │
│  │  • Main Accept Thread            │   │  • Main Accept Thread            │ │
│  │  • ClientHandler × N             │   │  • ClientHandler × N             │ │
│  │  • Verification Monitor ⚡       │   │  • Verification Monitor          │ │
│  │  • Connection Monitor ⚡         │   │  • Connection Monitor            │ │
│  │  • Peer Cleanup                  │   │  • Peer Cleanup                  │ │
│  │  • Discovery Beacon              │   │  • Discovery Beacon              │ │
│  │  • Discovery Listener            │   │  • Discovery Listener            │ │
│  │                                  │   │                                  │ │
│  │  ⚡ = Only runs on Leader       │   │                                  │ │
│  │      (Lab 6: Mutual Exclusion)   │   │                                  │ │
│  └────────────┬─────────────────────┘   └─────────────┬────────────────────┘ │
│               │                                        │                     │
│               │  UDP Multicast (239.0.0.1:4446)       │                     │
│               │  Discovery & Heartbeat (Lab 5)        │                     │
│               │ ◄──────────────────────────────────────┤                     │
│               │                                        │                     │
│               │  TCP Sync Messages (Lab 3 + Lab 4)    │                     │
│               │  Format: SYNC|<LT>|<message>          │                     │
│               │ ◄──────────────────────────────────────┤                     │
│               │                                        │                     │
│               └────────────┬───────────────────────────┘                     │
│                            │                                                 │
│                            ▼                                                 │
│                  ┌──────────────────────┐                                    │
│                  │   Shared Database    │                                    │
│                  │   (MySQL:3310)       │                                    │
│                  │                      │                                    │
│                  │  Tables:             │                                    │
│                  │  • nodes             │                                    │
│                  │    - logical_timestamp (Lab 4)                           │
│                  │  • events            │                                    │
│                  │    - logical_timestamp (Lab 4)                           │
│                  └──────────────────────┘                                    │
│                            ▲                                                 │
└────────────────────────────┼─────────────────────────────────────────────────┘
                             │
                             │ Custom RPC Protocol (Lab 3)
                             │ Format: COMMAND|param1|param2|...
                             │
┌────────────────────────────┼─────────────────────────────────────────────────┐
│                         CLIENT LAYER                                        │
│                            │                                                 │
│  ┌─────────────────┐  ┌────┴──────────────┐  ┌─────────────────┐           │
│  │ Monitor Node A  │  │ Monitor Node B    │  │ Monitor Node C  │           │
│  │ DISTRIBUTOR_001 │  │ DISTRIBUTOR_002   │  │ DISTRIBUTOR_003 │           │
│  │                 │  │                   │  │                 │           │
│  │ Reports:        │  │ Reports:          │  │ Reports:        │           │
│  │ • Voltage       │  │ • Voltage         │  │ • Voltage       │           │
│  │ • Power State   │  │ • Power State     │  │ • Power State   │           │
│  │ • Region        │  │ • Region          │  │ • Region        │           │
│  │                 │  │                   │  │                 │           │
│  │ Receives:       │  │ Receives:         │  │ Receives:       │           │
│  │ • SOLVED_CHECK  │  │ • SOLVED_CHECK    │  │ • SOLVED_CHECK  │           │
│  └─────────────────┘  └───────────────────┘  └─────────────────┘           │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Message Flow Example

### Scenario: Client Reports Outage

```
Step 1: Client Detects Outage
┌──────────────┐
│ Monitor B    │
│ (Client)     │
│              │
│ Voltage: 0V  │
│ State: OUTAGE│
└──────┬───────┘
       │
       │ TCP: "REPORT|DISTRIBUTOR_002|0|OUTAGE|North Addis"
       │
       ▼
┌──────────────────────────────────────────────────────────┐
│ Server 1 (Leader)                                        │
│                                                          │
│ Step 2: Receive & Process                               │
│   ClientHandler Thread:                                 │
│   • Parse message (Lab 3: RPC)                          │
│   • Increment Lamport Clock: 41 → 42 (Lab 4)           │
│   • Log event with LT=42                                │
│   • Update database                                     │
│   • Broadcast to peers                                  │
└──────┬───────────────────────────────────────────────────┘
       │
       │ TCP: "SYNC|42|REPORT|DISTRIBUTOR_002|0|OUTAGE|North Addis"
       │      └──┘
       │      Lamport Timestamp
       ▼
┌──────────────────────────────────────────────────────────┐
│ Server 2 (Follower)                                      │
│                                                          │
│ Step 3: Sync & Update Clock                             │
│   handleServerSession():                                │
│   • Extract LT=42 from message                          │
│   • Update clock: max(41, 42) + 1 = 43 (Lab 4)         │
│   • Process REPORT                                      │
│   • Update local database                               │
└──────────────────────────────────────────────────────────┘

Step 4: Admin Views Event
┌──────────────┐
│ Admin Console│
│ (RMI Client) │
└──────┬───────┘
       │
       │ RMI: adminService.getRecentEvents(10)
       │
       ▼
┌──────────────────────────────────────────────────────────┐
│ Server 1 RMI Service                                     │
│                                                          │
│ Step 5: Query Events                                    │
│   SQL: SELECT * FROM events                             │
│        ORDER BY logical_timestamp DESC                  │
│        LIMIT 10                                         │
│                                                          │
│   Returns:                                              │
│   [                                                     │
│     {                                                   │
│       eventId: "EVT_A1B2",                             │
│       nodeId: "DISTRIBUTOR_002",                       │
│       eventType: "OUTAGE_START",                       │
│       logicalTimestamp: 42,  ← Causally ordered!       │
│       timestamp: "2025-12-26 17:15:23"                 │
│     }                                                   │
│   ]                                                     │
└──────┬───────────────────────────────────────────────────┘
       │
       │ RMI Response
       │
       ▼
┌──────────────┐
│ Admin Console│
│              │
│ Displays:    │
│ EVENT_ID     │ NODE_ID         │ TYPE          │ LT │
│ EVT_A1B2     │ DISTRIBUTOR_002 │ OUTAGE_START  │ 42 │
└──────────────┘
```

## Lab Integration Map

```
┌─────────────────────────────────────────────────────────────┐
│                    YOUR SYSTEM                              │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ Lab 7: Multi-threading                               │  │
│  │ • Concurrent client handling                         │  │
│  │ • Background monitoring threads                      │  │
│  │ • Thread-safe data structures                        │  │
│  └──────────────────────────────────────────────────────┘  │
│                         │                                   │
│                         │ Uses                              │
│                         ▼                                   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ Lab 5: Leader Election                               │  │
│  │ • Bully algorithm (smallest ID wins)                 │  │
│  │ • Automatic failover                                 │  │
│  │ • Peer discovery via multicast                       │  │
│  └──────────────────────────────────────────────────────┘  │
│                         │                                   │
│                         │ Enables                           │
│                         ▼                                   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ Lab 6: Mutual Exclusion                              │  │
│  │ • Leader-based critical sections                     │  │
│  │ • Prevents race conditions                           │  │
│  │ • Coordinated database updates                       │  │
│  └──────────────────────────────────────────────────────┘  │
│                         │                                   │
│                         │ Coordinates                       │
│                         ▼                                   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ Lab 4: Lamport Clocks                                │  │
│  │ • Causal event ordering                              │  │
│  │ • Clock synchronization                              │  │
│  │ • Logical timestamps in all messages                 │  │
│  └──────────────────────────────────────────────────────┘  │
│                         │                                   │
│                         │ Timestamps                        │
│                         ▼                                   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ Lab 3: Custom RPC Protocol                           │  │
│  │ • Pipe-separated messages                            │  │
│  │ • REPORT, OUTAGE, SYNC commands                      │  │
│  │ • TCP socket communication                           │  │
│  └──────────────────────────────────────────────────────┘  │
│                         │                                   │
│                         │ Complemented by                   │
│                         ▼                                   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ Lab 2: RMI                                           │  │
│  │ • Remote administration                              │  │
│  │ • High-level abstraction                             │  │
│  │ • Type-safe method calls                             │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

## Ports & Protocols Reference

| Port | Protocol      | Purpose                      | Lab  |
| ---- | ------------- | ---------------------------- | ---- |
| 9000 | TCP           | Server 1 client connections  | 3, 7 |
| 9001 | TCP           | Server 2 client connections  | 3, 7 |
| 3000 | HTTP          | Server 1 web dashboard       | -    |
| 3001 | HTTP          | Server 2 web dashboard       | -    |
| 1099 | RMI           | Remote admin interface       | 2    |
| 4446 | UDP Multicast | Server discovery & heartbeat | 5    |
| 3310 | MySQL         | Shared database              | -    |

## Thread Architecture (Lab 7)

```
HeadlessServer Process
│
├── Main Thread
│   └── Accepts client connections
│       └── Spawns ClientHandler for each client
│
├── ClientHandler Threads (One per client)
│   ├── Thread 1: Handles DISTRIBUTOR_001
│   ├── Thread 2: Handles DISTRIBUTOR_002
│   └── Thread N: Handles DISTRIBUTOR_00N
│
├── Verification Monitor Thread
│   └── Only runs on Leader (Lab 6)
│       └── Checks for verification timeouts every 5s
│
├── Connection Monitor Thread
│   └── Only runs on Leader (Lab 6)
│       └── Marks timed-out nodes as OFFLINE
│
├── Peer Cleanup Thread
│   └── Removes expired peer servers
│       └── Triggers leader re-election if needed (Lab 5)
│
├── Discovery Beacon Thread
│   └── Broadcasts server presence via multicast (Lab 5)
│       └── Every 5 seconds
│
└── Discovery Listener Thread
    └── Listens for peer server announcements (Lab 5)
        └── Updates peer list
```

## Data Flow: Lamport Clock Synchronization (Lab 4)

```
Time →

Server 1 (Leader):
Clock: 10 → 11 → 12 ────────────────────────────────→ 15
       │    │    │                                     │
       │    │    └─ Sends SYNC|12|...                │
       │    └────── Processes local event            │
       └─────────── Receives client report           │
                                                       │
                                                       │
Server 2 (Follower):                                  │
Clock: 8 ─────────→ 13 ─────────────────────────────→ 14
                    │                                  │
                    └─ Receives SYNC|12|...           │
                       Updates: max(8, 12) + 1 = 13   │
                                                       │
                                                       │
Result: Both servers have causally-ordered clocks!    │
        Events can be sorted by logical_timestamp     │
```

---

This visual guide shows how all 6 labs work together to create a robust distributed system!
