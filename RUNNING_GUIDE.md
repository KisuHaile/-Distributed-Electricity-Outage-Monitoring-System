# 🚀 Complete Running Guide - Enhanced System

## 📋 Quick Start (TL;DR)

```powershell
# 1. Initialize database (once)
.\init_db.bat

# 2. Start servers (in separate terminals)
.\start_server.bat  # Server 1: ID=1, Port=8080, Peers=(empty)
.\start_server.bat  # Server 2: ID=2, Port=8081, Peers=1:localhost:8080
.\start_server.bat  # Server 3: ID=3, Port=8082, Peers=1:localhost:8080,2:localhost:8081

# 3. Start clients (in separate terminals)
.\start_simulator.bat  # Client 1: NodeID=addis_001
.\start_simulator.bat  # Client 2: NodeID=bahirdar_001

# 4. Test network simulation (optional)
.\test_network.bat
```

---

## 📖 Detailed Step-by-Step Guide

### **STEP 1: Database Setup** (First Time Only)

```powershell
cd "d:\kis files\Univeristy\Third year\Second semister\distributing system\⚡\-Distributed-Electricity-Outage-Monitoring-System"

.\init_db.bat
```

**What it does**:

- Creates `electricity_grid` database
- Creates `nodes` and `events` tables
- Sets up schema

**Expected output**:

```
Compiling DBInitializer...
Initializing Database...
Database initialized successfully!
```

---

### **STEP 2: Start Servers**

#### **Server 1 (First Server)**

**Terminal 1**:

```powershell
.\start_server.bat
```

**Configuration in GUI**:

- Server ID: `1`
- Port: `8080`
- Database Host: `localhost`
- Peers: _(leave empty)_

**Click "Start Server"**

**Expected logs**:

```
[Lab 4] Lamport Clock initialized for Node 1
[Lab 6] Mutual Exclusion initialized for Node 1
Distributed Server 1 started on port 8080
Starting Election...
I am the Leader!
[Monitor] As LEADER, I am responsible for failure detection
```

---

#### **Server 2 (Second Server)** - Optional

**Terminal 2**:

```powershell
.\start_server.bat
```

**Configuration**:

- Server ID: `2`
- Port: `8081`
- Database Host: `localhost`
- Peers: `1:localhost:8080`

**Click "Start Server"**

**Expected logs**:

```
[Lab 4] Lamport Clock initialized for Node 2
Distributed Server 2 started on port 8081
Discovered Peer: 1 at localhost
Starting Election...
```

---

#### **Server 3 (Third Server)** - Recommended for demos

**Terminal 3**:

```powershell
.\start_server.bat
```

**Configuration**:

- Server ID: `3`
- Port: `8082`
- Database Host: `localhost`
- Peers: `1:localhost:8080,2:localhost:8081`

**Click "Start Server"**

**Expected logs**:

```
[Lab 4] Lamport Clock initialized for Node 3
Distributed Server 3 started on port 8082
Discovered Peer: 1 at localhost
Discovered Peer: 2 at localhost
Starting Election...
I am the Leader!  ← Takes over from Server 1
[Monitor] As LEADER, I am responsible for failure detection
```

---

### **STEP 3: Start Clients**

#### **Client 1**

**Terminal 4**:

```powershell
.\start_simulator.bat
```

**Configuration**:

- Node ID: `addis_001`

**Click "Auto Connect"**

**Expected logs**:

```
Scanning for Leader Server...
Found Leader at 192.168.x.x:8082
Connected and Authenticated!
Auto-Heartbeat started (every 10 seconds)...
Sent: HEARTBEAT|addis_001|ON|45|ok
Server: OK|HEARTBEAT
```

---

#### **Client 2** - Optional

**Terminal 5**:

```powershell
.\start_simulator.bat
```

**Configuration**:

- Node ID: `bahirdar_001`

**Click "Auto Connect"**

---

### **STEP 4: Verify Everything Works**

#### **Check Server Dashboard**

On any server window, you should see the **Live Grid Status** table:

| Node ID      | Status | Load | Power | Transformer | Last Seen |
| ------------ | ------ | ---- | ----- | ----------- | --------- |
| addis_001    | ONLINE | 45   | ON    | ok          | 16:05:23  |
| bahirdar_001 | ONLINE | 60   | ON    | ok          | 16:05:25  |

✅ **System is running!**

---

## 🧪 Testing New Features

### **Test 1: Gradual Failure Detection** (NEW!)

**Demonstrates**: ONLINE → SUSPECTED → OFFLINE → RECOVERED

**Steps**:

1. Start 1 server + 1 client
2. Client shows **ONLINE** in dashboard
3. **Close client window** (simulate failure)
4. **Watch dashboard** (refresh every 2s):
   - **T+15s**: Status → **SUSPECTED** ⚠️
   - **T+30s**: Status → **OFFLINE** 🔴
5. **Check server logs**:
   ```
   [Monitor] 1 node(s) now SUSPECTED (missed heartbeat)
   [Monitor] 1 node(s) confirmed OFFLINE (no heartbeat for 30s)
   ```
6. **Restart client** (same Node ID)
7. **Watch dashboard**:
   - Status → **RECOVERED** ✅
   - After 5s → **ONLINE** 🟢
8. **Check server logs**:
   ```
   [Monitor] 1 node(s) RECOVERED from failure!
   ```

**Academic Value**: Shows understanding of failure **uncertainty** (FLP impossibility)

---

### **Test 2: Leader Election & Failover**

**Demonstrates**: Bully Election Algorithm

**Steps**:

1. Start 3 servers (IDs: 1, 2, 3)
2. **Verify**: Server 3 is leader (highest ID)
   - Only Server 3 logs: `[Monitor] As LEADER...`
3. **Kill Server 3** (close window)
4. **Watch Server 2**:
   ```
   Starting Election...
   I am the Leader!
   [Monitor] As LEADER, I am responsible for failure detection
   ```
5. **Server 2 is now leader!**
6. **Restart Server 3**
7. **Watch Server 3**:
   ```
   Starting Election...
   I am the Leader!
   ```
8. **Server 3 reclaims leadership**

**Academic Value**: Demonstrates leader-based coordination

---

### **Test 3: Network Unreliability Simulation** (⭐ NEW!)

**Demonstrates**: System works despite unreliable network

**Steps**:

1. **Run network test**:

   ```powershell
   .\test_network.bat
   ```

2. **Expected output**:

   ```
   Test 1: RELIABLE network
     Message 1: DELIVERED
     Message 2: DELIVERED
     ...all 10 delivered

   Test 2: FLAKY network (5% drop, 200ms delay)
   [NETWORK SIM] ENABLED
      Drop Rate: 5%
      Max Delay: 200ms
     Message 1: DELIVERED
   [NETWORK SIM] Delaying 134ms
     Message 2: DELIVERED
   [NETWORK SIM] MESSAGE DROPPED
     Message 3: DROPPED
     ...

   Test 4: CHAOS mode (30% drop, 1000ms delay)
   [NETWORK SIM] ENABLED
      Drop Rate: 30%
      Max Delay: 1000ms
     Message 1: DELIVERED
   [NETWORK SIM] MESSAGE DROPPED
     Message 2: DROPPED
   [NETWORK SIM] Delaying 847ms
     Message 3: DELIVERED
     ...
   ```

**Academic Value**: Proves system handles unreliable networks (not just sockets!)

---

### **Test 4: Lamport Clock Ordering**

**Demonstrates**: Causal ordering of events

**Steps**:

1. Start 2 servers
2. Send messages between them
3. **Check logs** for Lamport timestamps:
   ```
   [Server 1] Event at LC:5
   [Server 2] Received message, updating LC:3 -> LC:6
   [Server 1] Received response at LC:7
   ```

**Academic Value**: Shows causal ordering maintained across nodes

---

## 🎯 Common Actions

### **Send Manual Heartbeat**

- Click **"Send Heartbeat (Normal)"** button in client
- See it appear in server dashboard immediately

### **Simulate Power Outage**

- Click **"Trigger OUTAGE (Fault)"** button
- Creates outage event in database
- All servers see the event

### **Restore Power**

- Click **"Resolve OUTAGE (Restored)"** button
- Creates restoration event

### **Test Offline Detection**

1. Connect client
2. Close client window
3. Wait 30 seconds
4. Dashboard shows **OFFLINE**

---

## 📊 What You Should See

### **Server GUI**:

- **Live Grid Status Table** (updates every 2s)
- **Server logs** at bottom
- **Configuration** at top

### **Server Logs** (Leader):

```
[Lab 4] Lamport Clock initialized for Node 3
[Lab 6] Mutual Exclusion initialized for Node 3
Distributed Server 3 started on port 8082
Starting Election...
I am the Leader!
[Monitor] As LEADER, I am responsible for failure detection
[Client #1] Received: HEARTBEAT|addis_001|ON|45|ok
[SYNC] Replicated HEARTBEAT for node addis_001
[Monitor] 1 node(s) now SUSPECTED (missed heartbeat)
[Monitor] 1 node(s) confirmed OFFLINE (no heartbeat for 30s)
[Monitor] 1 node(s) RECOVERED from failure!
```

### **Client GUI**:

- **Status**: Connected (green) or Disconnected (red)
- **Auto-heartbeat logs** every 10 seconds
- **Buttons**: Send Heartbeat, Trigger Outage, Resolve Outage

---

## ⚠️ Troubleshooting

### **Problem**: "Port already in use"

**Solution**: Another server is using that port. Use different ports (8080, 8081, 8082, etc.)

### **Problem**: "Database connection failed"

**Solution**:

1. Check MySQL is running
2. Verify database host is `localhost`
3. Run `.\init_db.bat` again

### **Problem**: "Compilation Failed"

**Solution**:

1. Check Java JDK is installed: `java -version`
2. Check it's in PATH
3. Try running from project root directory

### **Problem**: Client can't find server

**Solution**:

1. Make sure at least one server is running
2. Check firewall settings
3. Verify servers are on same network

### **Problem**: Dashboard table is empty

**Solution**:

1. Wait 10 seconds for first heartbeat
2. Or click "Send Heartbeat" manually
3. Check server logs for errors

### **Problem**: No SUSPECTED state visible

**Solution**:

1. Dashboard updates every 2 seconds
2. SUSPECTED only lasts 15 seconds (15s-30s window)
3. Watch carefully or check server logs

---

## 📁 File Structure

```
Project Root/
├── init_db.bat              ← Initialize database
├── start_server.bat         ← Start server instance
├── start_simulator.bat      ← Start client instance
├── test_network.bat         ← Test network simulation (NEW!)
├── lib/
│   └── mysql-connector-j-9.2.0.jar
├── src/main/java/com/electricity/
│   ├── model/
│   │   ├── NodeState.java   ← NEW: State machine
│   │   └── Peer.java
│   ├── network/
│   │   ├── NetworkSimulator.java      ← NEW: Unreliability sim
│   │   └── ReliableMessaging.java     ← NEW: ACK/retry
│   ├── monitor/
│   │   └── NodeMonitor.java           ← UPDATED: 4 states
│   ├── service/
│   │   └── ElectionManager.java       ← UPDATED: Clocks
│   ├── sync/
│   │   ├── LamportClock.java          ← NEW: Logical clock
│   │   └── MutualExclusion.java       ← NEW: Mutex
│   ├── test/
│   │   └── NetworkTest.java           ← NEW: Test class
│   └── ...
└── Documentation/
    ├── SYSTEM_REFINEMENT.md           ← NEW: Transformation guide
    ├── LAB_IMPLEMENTATION_GUIDE.md
    ├── LAB_QUICK_REFERENCE.md
    ├── CLIENT_STATUS_TRACKING.md
    └── ...
```

---

## ✅ Quick Checklist

Before demonstrating:

- [ ] MySQL is running
- [ ] Database initialized (`.\init_db.bat`)
- [ ] At least 1 server running
- [ ] At least 1 client connected
- [ ] Dashboard shows client as ONLINE
- [ ] Server logs show heartbeats
- [ ] Tested failure detection (disconnect client)
- [ ] Tested leader election (kill leader server)
- [ ] (Optional) Tested network simulation

---

## 🎓 For Academic Demonstration

### **Recommended Demo Flow**:

1. **Start**: Show 3 servers, 2 clients
2. **Feature 1**: Gradual failure (disconnect client, show SUSPECTED → OFFLINE)
3. **Feature 2**: Leader election (kill leader, show failover)
4. **Feature 3**: Recovery (restart client, show RECOVERED)
5. **Feature 4**: Network chaos (run `test_network.bat`)
6. **Explain**: How this proves distributed systems concepts

### **Key Points to Mention**:

- "Only LEADER monitors - leader-based coordination"
- "SUSPECTED state shows failure uncertainty (FLP)"
- "Lamport clocks maintain causal ordering"
- "Network simulation proves robustness"

---

## 🚀 You're Ready!

Your system demonstrates:

- ✅ Failure detection with uncertainty
- ✅ Leader-based coordination
- ✅ Causal ordering (Lamport clocks)
- ✅ Network unreliability handling
- ✅ Automatic recovery
- ✅ Multi-threading
- ✅ Data replication

**Just run the batch files and watch distributed systems theory come to life!** 🎉
