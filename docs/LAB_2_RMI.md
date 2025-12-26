# Lab 2: Implementation of RMI (Remote Method Invocation)

## Overview

This implementation adds **Java RMI** (Remote Method Invocation) to enable remote administration and monitoring of the Distributed Electricity Outage Monitoring System. Administrators can now connect from any machine on the network to view system status, trigger actions, and monitor events.

## Theoretical Background

### What is RMI?

**RMI** (Remote Method Invocation) is Java's implementation of **RPC** (Remote Procedure Call). It allows you to:

- Call methods on objects running on a different JVM (Java Virtual Machine)
- Treat remote objects as if they were local
- Automatically handle network communication, serialization, and deserialization

### Architecture

```
┌─────────────────┐         RMI          ┌──────────────────┐
│  Admin Console  │ ◄──────────────────► │  Server (Stub)   │
│   (Client)      │    Network Call      │                  │
└─────────────────┘                      └──────────────────┘
                                                  │
                                                  ▼
                                         ┌──────────────────┐
                                         │ AdminServiceImpl │
                                         │  (Actual Object) │
                                         └──────────────────┘
```

### Key Components

1. **Remote Interface**: Defines methods that can be called remotely
2. **Implementation**: The actual object that performs the work
3. **RMI Registry**: A naming service that maps names to remote objects
4. **Stub**: Client-side proxy that handles network communication
5. **Skeleton**: Server-side handler (automatically generated in modern Java)

## Implementation Details

### 1. Remote Interface

**File**: `src/main/java/com/electricity/rmi/AdminService.java`

```java
public interface AdminService extends Remote {
    List<Map<String, Object>> getAllNodes() throws RemoteException;
    Map<String, Object> getNodeDetails(String nodeId) throws RemoteException;
    String triggerVerification(String nodeId) throws RemoteException;
    // ... more methods
}
```

**Key Points**:

- Extends `java.rmi.Remote`
- All methods throw `RemoteException`
- Parameters and return types must be serializable

### 2. Service Implementation

**File**: `src/main/java/com/electricity/rmi/AdminServiceImpl.java`

```java
public class AdminServiceImpl extends UnicastRemoteObject implements AdminService {

    @Override
    public List<Map<String, Object>> getAllNodes() throws RemoteException {
        // Query database
        // Return node information
    }

    @Override
    public String triggerVerification(String nodeId) throws RemoteException {
        // Update database
        // Send command to client
        // Log event with Lamport timestamp (Lab 4 integration!)
        // Broadcast to peers
    }
}
```

**Features**:

- Extends `UnicastRemoteObject` for automatic remote object export
- Integrates with existing database (DBConnection)
- Uses Lamport clocks for event logging (Lab 4 integration)
- Broadcasts changes to peer servers

### 3. RMI Server

**File**: `src/main/java/com/electricity/rmi/RMIServer.java`

```java
public class RMIServer {
    public static void start() {
        AdminServiceImpl adminService = new AdminServiceImpl();
        Registry registry = LocateRegistry.createRegistry(1099);
        registry.rebind("ElectricityAdmin", adminService);
    }
}
```

**Process**:

1. Create the service implementation
2. Create or locate the RMI registry (port 1099)
3. Bind the service with a name ("ElectricityAdmin")

### 4. Admin Console Client

**File**: `src/main/java/com/electricity/rmi/AdminConsole.java`

```java
public class AdminConsole {
    private AdminService adminService;

    public AdminConsole(String host, int port) {
        Registry registry = LocateRegistry.getRegistry(host, port);
        adminService = (AdminService) registry.lookup("ElectricityAdmin");
    }

    // Interactive menu for remote administration
}
```

**Features**:

- Interactive command-line interface
- View all nodes and their status
- View events (ordered by Lamport clock!)
- Trigger verification remotely
- Mark nodes as resolved
- View cluster status and statistics

## Integration with Existing System

### HeadlessServer Integration

The RMI server starts automatically when HeadlessServer launches:

```java
// In HeadlessServer.main()
SimpleWebServer webServer = new SimpleWebServer(webPort);
webServer.start();

com.electricity.rmi.RMIServer.start();  // ← RMI starts here

startServerThreads(id, port, peerConfig);
```

### Lab 4 Integration

All RMI operations that modify state use Lamport clocks:

```java
public String triggerVerification(String nodeId) {
    // ... update database ...

    long lamportTime = HeadlessServer.getClock().tick();
    EventLogger.logEvent(nodeId, "MANUAL_VERIFY",
        "RMI Admin triggered verification", lamportTime);

    HeadlessServer.broadcastSync("VERIFY_RELAY|" + nodeId);
}
```

This ensures:

- Events triggered via RMI are causally ordered
- Changes are synchronized across all servers
- Audit trail is maintained

## Available Remote Methods

### Monitoring Methods

| Method                         | Description                         | Returns           |
| ------------------------------ | ----------------------------------- | ----------------- |
| `getAllNodes()`                | Get all nodes and their status      | List of node maps |
| `getNodeDetails(nodeId)`       | Get detailed info about a node      | Node details map  |
| `getRecentEvents(limit)`       | Get recent events (by Lamport time) | List of events    |
| `getNodeEvents(nodeId, limit)` | Get events for specific node        | List of events    |
| `getClusterStatus()`           | Get cluster-wide statistics         | Status map        |
| `getServerStats()`             | Get server statistics               | Stats map         |

### Control Methods

| Method                               | Description                   | Returns         |
| ------------------------------------ | ----------------------------- | --------------- |
| `triggerVerification(nodeId)`        | Request node to verify status | Success message |
| `markNodeResolved(nodeId, operator)` | Manually resolve an outage    | Success message |

### System Methods

| Method                    | Description               | Returns        |
| ------------------------- | ------------------------- | -------------- |
| `getCurrentLogicalTime()` | Get Lamport clock value   | long timestamp |
| `isLeader()`              | Check if server is leader | boolean        |
| `getServerId()`           | Get server's unique ID    | int            |

## Running the System

### 1. Start the Server (with RMI)

```bash
java -cp bin com.electricity.server.HeadlessServer
```

Output:

```
[DB] Auto-initializing database...
[RMI] Starting Remote Admin Interface...
[RMI] Created RMI registry on port 1099
[RMI] ✅ AdminService bound successfully!
[RMI] Service Name: ElectricityAdmin
[RMI] RMI URL: rmi://localhost:1099/ElectricityAdmin
```

### 2. Start the Admin Console

```bash
java -cp bin com.electricity.rmi.AdminConsole localhost 1099
```

Output:

```
╔════════════════════════════════════════════════════════════╗
║   Distributed Electricity Monitoring - Admin Console      ║
║   Lab 2: Remote Method Invocation (RMI)                   ║
╚════════════════════════════════════════════════════════════╝

✅ Connected to RMI server at localhost:1099

============================================================
                    ADMIN MENU
============================================================
  1. View All Nodes
  2. View Node Details
  3. View Recent Events
  ...
```

### 3. Remote Administration

From the console, you can:

- View all nodes across all servers (shared database)
- Trigger verification commands
- View causally-ordered events (Lamport timestamps)
- Monitor cluster health

## Network Configuration

### Firewall Rules

If connecting from a different machine, ensure:

1. Port **1099** (RMI Registry) is open
2. Ephemeral ports for RMI communication are allowed

### Remote Access

To connect from a different machine:

```bash
java -cp bin com.electricity.rmi.AdminConsole 192.168.1.100 1099
```

## Security Considerations

### Current Implementation

- **No authentication**: Anyone who can reach port 1099 can administer the system
- **No encryption**: All data is sent in plaintext

### Production Recommendations

1. **Use SSL/TLS**: Wrap RMI with SSL sockets
2. **Add authentication**: Require username/password
3. **Implement authorization**: Role-based access control
4. **Firewall**: Restrict RMI port to admin network only
5. **VPN**: Require VPN connection for remote access

## Comparison: RMI vs Custom Protocol

### Our Custom Protocol (Labs 5, 6, 7)

```
Client → Server: "REPORT|NODE_001|220|NORMAL|Region_A"
Server → Client: "OK|ACK_REPORT"
```

- Manual parsing
- Custom error handling
- We control everything

### RMI (Lab 2)

```java
adminService.triggerVerification("NODE_001");
```

- Automatic serialization
- Built-in error handling
- Java handles network details

**RMI is higher-level abstraction of what we built manually!**

## Testing

### Test Case 1: Remote Monitoring

1. Start Server 1
2. Connect client to Server 1
3. From a different terminal, start AdminConsole
4. View all nodes → Should see the connected client
5. View recent events → Should see connection event

### Test Case 2: Remote Control

1. Simulate an outage on a node
2. From AdminConsole, trigger verification
3. Verify that:
   - Database is updated
   - Event is logged with Lamport timestamp
   - Client receives SOLVED_CHECK command

### Test Case 3: Multi-Server

1. Start Server 1 and Server 2
2. Connect AdminConsole to Server 1
3. View cluster status → Should show both servers
4. Trigger action on Server 1
5. Verify that Server 2 receives the sync message

## Benefits

1. **Ease of Use**: Call remote methods as if they were local
2. **Type Safety**: Compile-time checking of method signatures
3. **Automatic Serialization**: No manual parsing
4. **Exception Handling**: Network errors are Java exceptions
5. **Integration**: Works seamlessly with existing Java code

## Limitations

1. **Java-Only**: Can't call from Python, C++, etc. (use REST API for that)
2. **Firewall Issues**: RMI uses dynamic ports which can be blocked
3. **Performance**: Slower than raw sockets for high-frequency calls
4. **Versioning**: Interface changes require client updates

## Future Enhancements

- **Web-based Admin UI**: Use RMI as backend for a web dashboard
- **Mobile App**: Create Android app using RMI
- **Batch Operations**: Add methods to update multiple nodes at once
- **Streaming**: Use RMI callbacks for real-time event notifications

---

**Lab Completion Status**: ✅ FULLY IMPLEMENTED
**Date**: December 26, 2025
**System**: Distributed Electricity Outage Monitoring System
