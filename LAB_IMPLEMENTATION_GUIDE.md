# Distributed Systems Lab Implementation Guide
## Electricity Outage Monitoring System

---

## 📚 **Lab Requirements Coverage**

This document maps how the **Distributed Electricity Outage Monitoring System** implements each lab requirement.

---

## ✅ **IMPLEMENTED LABS**

### **Lab 4: Clock Synchronization** ✅

**Requirement**: Implementation of Clock Synchronization (logical/physical)

**Implementation**: **Lamport Logical Clock Algorithm**

**Location**: `src/main/java/com/electricity/sync/LamportClock.java`

**How it Works**:
```
Event Ordering Rules:
1. Local Event → Clock++ 
2. Send Message → Clock++, attach timestamp
3. Receive Message → Clock = max(local, received) + 1
```

**Key Features**:
- Maintains causal ordering of events
- Synchronizes timestamps across distributed servers
- Used in heartbeat synchronization and mutual exclusion

**Usage in System**:
```java
// In ElectionManager
lamportClock.tick();  // Local event
int timestamp = lamportClock.sendTimestamp();  // Sending message
lamportClock.update(receivedTime);  // Receiving message
```

**Testing**:
1. Start multiple servers
2. Send messages between servers
3. Observe log files - timestamps maintain causal ordering
4. Events from Server 1 before Server 2 will have lower timestamps

**Output Example**:
```
[Lab 4] Lamport Clock initialized for Node 1
[Server 1] Event at LC:5
[Server 2] Received message, updating LC:3 -> LC:6
```

---

### **Lab 5: Election Algorithm** ✅

**Requirement**: Implementation of Election algorithm

**Implementation**: **Bully Election Algorithm**

**Location**: `src/main/java/com/electricity/service/ElectionManager.java`

**Algorithm**:
```
Bully Election Rules:
1. Node with highest ID becomes leader
2. When leader fails, any node can start election
3. Send ELECTION to all higher-ID nodes
4. If no response → declare self as COORDINATOR
5. Broadcast COORDINATOR message to all
```

**Key Features**:
- Automatic leader detection
- Handles leader failures
- Deterministic (highest ID wins)

**Code Example**:
```java
public void startElection() {
    int highestId = myId;
    for (Peer p : peers) {
        if (p.getId() > highestId) {
            highestId = p.getId();
        }
    }
    if (highestId == myId) {
        isLeader = true;
        broadcast("COORDINATOR|" + myId);
    } else {
        isLeader = false;
    }
}
```

**Testing**:
1. Start Server 1 (ID=1), Server 2 (ID=2), Server 3 (ID=3)
2. Server 3 becomes leader (highest ID)
3. Stop Server 3
4. Server 2 becomes new leader
5. Restart Server 3
6. Server 3 reclaims leadership

**Output Example**:
```
Starting Election...
I am the Leader!
broadcasting COORDINATOR|3
```

---

### **Lab 6: Mutual Exclusion** ✅

**Requirement**: Implementation of Mutual Exclusion algorithms

**Implementation**: **Ricart-Agrawala Algorithm**

**Location**: `src/main/java/com/electricity/sync/MutualExclusion.java`

**Algorithm**:
```
Ricart-Agrawala Rules:
1. Request CS → Broadcast REQUEST(timestamp, nodeId)
2. Receive REQUEST → 
   - If not requesting: Send REPLY
   - If requesting with lower timestamp: Defer
   - If requesting with higher timestamp: Send REPLY
3. Enter CS → After receiving ALL replies
4. Exit CS → Send deferred REPLYs
```

**Key Features**:
- Distributed mutual exclusion (no central coordinator)
- Fair ordering based on Lamport timestamps
- Deadlock-free

**Code Example**:
```java
// Request critical section
mutualExclusion.requestCriticalSection();
// ... wait for all replies ...
// Enter critical section
// ... access shared resource ...
mutualExclusion.releaseCriticalSection();
```

**Use Cases in System**:
- Coordinated database access
- Leader election synchronization
- Preventing write conflicts

**Testing**:
1. Multiple servers try to write to database simultaneously
2. MutEx ensures only one writes at a time
3. Requests are ordered by timestamp
4. No conflicts or race conditions

**Output Example**:
```
[MutEx] Node 1 requesting CS at T=10
[MutEx] Node 2 grants request from 1
[MutEx] ✅ Node 1 ENTERED critical section
[MutEx] Node 1 EXITED critical section
```

---

### **Lab 7: Multi-threaded Client/Server** ✅

**Requirement**: Implementation of multi-threaded client/server processes

**Implementation**: **Thread-per-Client Model**

**Location**: `src/main/java/com/electricity/server/ClientHandler.java`

**Architecture**:
```
Server (Main Thread)
  ↓
ServerSocket.accept()
  ↓
For each client connection:
  ↓
new Thread(ClientHandler) → Dedicated thread
  ↓
Concurrent handling of multiple clients
```

**Key Features**:
- Separate thread for each client connection
- Non-blocking server (can accept new clients while serving existing ones)
- Thread-safe data structures (ConcurrentHashMap)

**Code Example**:
```java
// In ServerGUI.java
Socket socket = serverSocket.accept();
ClientHandler handler = new ClientHandler(socket, clientNumber, electionManager);
new Thread(handler).start();  // New thread for this client
```

**Thread Safety**:
- `synchronized` methods for critical sections
- `ConcurrentHashMap` for shared node state
- `volatile` flags for cross-thread communication

**Testing**:
1. Start server
2. Connect multiple clients simultaneously
3. Each client gets dedicated thread
4. All clients can send/receive concurrently

**Output Example**:
```
Connection from: /192.168.1.100
[Thread-5] Client #1 connected
[Thread-6] Client #2 connected
[Thread-7] Client #3 connected
```

---

## 📝 **DOCUMENTED LABS** (Not Fully Implemented)

### **Lab 2: RMI (Remote Method Invocation)**

**Current Status**: System uses **Socket-based communication**

**To Convert to RMI**:

1. Create RMI Interface:
```java
public interface ElectricityService extends Remote {
    void reportHeartbeat(String nodeId, String powerState, int load) 
        throws RemoteException;
    void reportOutage(String eventId, String nodeId, String type) 
        throws RemoteException;
}
```

2. Implement Server:
```java
public class ElectricityServiceImpl extends UnicastRemoteObject 
                                   implements ElectricityService {
    public void reportHeartbeat(...) throws RemoteException {
        // Implementation
    }
}
```

3. Register with RMI Registry:
```java
ElectricityService service = new ElectricityServiceImpl();
Naming.rebind("//localhost/ElectricityService", service);
```

4. Client Lookup:
```java
ElectricityService service = (ElectricityService) 
    Naming.lookup("//server_ip/ElectricityService");
service.reportHeartbeat(...);
```

**Note**: RMI version can be provided as separate implementation

---

### **Lab 3: RPC (Remote Procedure Call)**

**Current Status**: Not implemented

**Conversion Approach**: Use gRPC for Java

1. Define Protocol Buffers:
```protobuf
service ElectricityMonitor {
    rpc ReportHeartbeat(HeartbeatRequest) returns (Response);
    rpc ReportOutage(OutageRequest) returns (Response);
}
```

2. Generate stubs with `protoc`
3. Implement server and client

---

### **Lab 8: Process/Code Migration**

**Conceptual Implementation**:

**Scenario**: Migrate client monitoring task to another server

```java
public class MigrationManager {
    public void migrateClientHandler(ClientHandler handler, String targetServer) {
        // 1. Serialize handler state
        byte[] state = serializeState(handler);
        
        // 2. Send to target server
        sendToServer(targetServer, state);
        
        // 3. Deserialize and resume on target
        // 4. Terminate local handler
    }
}
```

**Use Case**: Load balancing - move clients from overloaded server to idle one

---

### **Lab 9: Enterprise JavaBeans (EJB)**

**Not Implemented**

**How to Adapt**:
- Create Session Beans for business logic
- Entity Beans for database access
- Deploy on application server (WildFly, GlassFish)

---

### **Lab 10: CORBA**

**Not Implemented** (Deprecated technology)

**Alternative**: Keep current socket-based or use modern alternatives (gRPC, REST)

---

### **Lab 11: .NET Framework**

**Not Applicable** (This is a Java system)

**Alternative**: Could create .NET client using C# that communicates with Java server via sockets/REST

---

## 🎯 **Summary Table**

| Lab | Requirement | Status | Implementation |
|-----|-------------|--------|----------------|
| **Lab 2** | RMI | 📝 Documented | Socket-based (can convert) |
| **Lab 3** | RPC | 📝 Documented | Can use gRPC |
| **Lab 4** | Clock Sync | ✅ **DONE** | Lamport Clock |
| **Lab 5** | Election | ✅ **DONE** | Bully Algorithm |
| **Lab 6** | Mutual Exclusion | ✅ **DONE** | Ricart-Agrawala |
| **Lab 7** | Multi-threading | ✅ **DONE** | Thread-per-client |
| **Lab 8** | Migration | 📝 Conceptual | Can add Serialization |
| **Lab 9** | EJB | ❌ N/A | Different architecture |
| **Lab 10** | CORBA | ❌ Deprecated | Modern alternatives |
| **Lab 11** | .NET | ❌ N/A | Java system |

---

## 🧪 **Complete Testing Procedure**

### **Test 1: Lab 4 (Clock Synchronization)**
```bash
# Terminal 1
.\start_server.bat  # Server 1 (ID=1, Port=8080)

# Terminal 2  
.\start_server.bat  # Server 2 (ID=2, Port=8081, Peers=1:localhost:8080)

# Terminal 3
.\start_simulator.bat  # Client

# Observe logs: Lamport timestamps maintain causal ordering
```

### **Test 2: Lab 5 (Election)**
```bash
# Start 3 servers (IDs: 1, 2, 3)
# Server 3 becomes leader (highest ID)
# Stop Server 3
# Server 2 becomes new leader
# Check logs for election messages
```

### **Test 3: Lab  6 (Mutual Exclusion)**
```bash
# Multiple servers compete for database access
# MutEx ensures orderly access
# Check logs for CS entry/exit messages
```

### **Test 4: Lab 7 (Multi-threading)**
```bash
# Start 1 server
# Connect 5+ clients simultaneously
# All clients work concurrently
# Check dashboard shows all clients
```

---

## 📁 **File Structure**

```
src/main/java/com/electricity/
├── sync/
│   ├── LamportClock.java         (Lab 4)
│   └── MutualExclusion.java      (Lab 6)
├── service/
│   └── ElectionManager.java      (Lab 5, integrates 4 & 6)
├── server/
│   ├── ClientHandler.java        (Lab 7)
│   └── ui/ServerGUI.java
├── client/
│   └── ui/ClientGUI.java
└── monitor/
    └── NodeMonitor.java
```

---

## 🎓 **Lab Report Tips**

### For Lab 4 (Clock Sync):
- Explain Lamport Clock algorithm
- Show code from `LamportClock.java`
- Demonstrate with screenshots of timestamps
- Explain advantages over physical clocks

### For Lab 5 (Election):
- Explain Bully algorithm
- Show `ElectionManager.startElection()`
- Demonstrate leader failover
- Compare with other algorithms (Ring, etc.)

### For Lab 6 (Mutual Exclusion):
- Explain Ricart-Agrawala algorithm
- Show `MutualExclusion.java` implementation
- Demonstrate conflict resolution
- Analyze message complexity (2(N-1) messages)

### For Lab 7 (Multi-threading):
- Explain thread-per-client model
- Show `ClientHandler` as Runnable
- Demonstrate concurrent clients
- Discuss thread safety measures

---

## 📞 **Support**

**Files**:
- Full implementation in current codebase
- Documentation in `*.md` files
- Test scripts in `*.bat` files

**Demonstration**:
- Run servers and clients
- Show logs proving each algorithm works
- Take screenshots of dashboard during tests

---

**Date**: 2025-12-17  
**System**: Distributed Electricity Outage Monitoring  
**Labs Covered**: 4, 5, 6, 7 (fully) + 2, 3, 8 (documented)
