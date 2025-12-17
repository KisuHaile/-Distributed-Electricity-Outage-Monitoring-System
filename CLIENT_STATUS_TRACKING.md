# Client Online/Offline Status Tracking

## Overview
The system now automatically tracks whether clients are **ONLINE** or **OFFLINE** based on their heartbeat activity.

## How It Works

### 🔄 **Automatic Heartbeat Mechanism**

#### Client Side (ClientGUI):
1. **When client connects**: Automatic heartbeat thread starts
2. **Heartbeat frequency**: Every **10 seconds**
3. **Heartbeat content**: Sends node ID, power state, load percentage, and transformer status
4. **On disconnect**: Heartbeat thread stops automatically

#### Server Side:
1. **On heartbeat received**: Updates node status to **'ONLINE'** and records `last_seen` timestamp
2. **Monitoring thread**: Runs every **15 seconds** (on leader server only)
3. **Timeout detection**: If node hasn't sent heartbeat in **30 seconds** → marks as **'OFFLINE'**
4. **Replication**: Status is synchronized across all servers in the cluster

### 📊 **Status Flow Diagram**

```
Client Starts
    ↓
Auto-Connect to Leader
    ↓
[Connected] → Auto-Heartbeat STARTS
    ↓
Heartbeat sent every 10s
    ↓
Server receives → Status = 'ONLINE'
    ↓
Server broadcasts to peers
    ↓
All servers show 'ONLINE'
    
    [If no heartbeat for 30s]
    ↓
Leader's NodeMonitor detects timeout
    ↓
Status changed to 'OFFLINE'
    ↓
All servers updated
```

## Files Modified

### 1. **ClientGUI.java**
**Location**: `src/main/java/com/electricity/client/ui/ClientGUI.java`

**Changes**:
- Added `heartbeatThread` field
- Added `startAutoHeartbeat()` method that sends heartbeat every 10 seconds
- Modified `connect()` to start automatic heartbeat
- Modified `disconnect()` to stop heartbeat thread

**Key Code**:
```java
private void startAutoHeartbeat() {
    heartbeatThread = new Thread(() -> {
        log("Auto-Heartbeat started (every 10 seconds)...");
        try {
            while (connected && !Thread.currentThread().isInterrupted()) {
                Thread.sleep(10000);  // 10 seconds
                if (connected) {
                    sendHeartbeat();
                }
            }
        } catch (InterruptedException e) {
            log("Auto-Heartbeat stopped.");
        }
    });
    heartbeatThread.setDaemon(true);
    heartbeatThread.start();
}
```

### 2. **ClientHandler.java**
**Location**: `src/main/java/com/electricity/server/ClientHandler.java`

**Changes**:
- Changed status from `'alive'` to `'ONLINE'` in heartbeat handler
- Changed status from `'alive'` to `'ONLINE'` in updateNodeState method

**SQL Updates**:
```sql
-- When heartbeat received:
INSERT INTO nodes (..., status) VALUES (..., 'ONLINE')
ON DUPLICATE KEY UPDATE ..., status='ONLINE'
```

### 3. **NodeMonitor.java**
**Location**: `src/main/java/com/electricity/monitor/NodeMonitor.java`

**Changes**:
- Updated timeout query to match `status='ONLINE'` instead of `'alive'`
- Marks inactive nodes as `'OFFLINE'`

**SQL Update**:
```sql
UPDATE nodes 
SET status='OFFLINE' 
WHERE last_seen < (NOW() - INTERVAL 30 SECOND) 
AND status='ONLINE'
```

### 4. **ElectionManager.java**
**Location**: `src/main/java/com/electricity/service/ElectionManager.java`

**Changes**:
- Updated `syncHeartbeatToDB()` to use `'ONLINE'` status
- Ensures replicated data uses consistent status values

## Configuration Parameters

| Parameter | Value | Location | Description |
|-----------|-------|----------|-------------|
| **Heartbeat Interval** | 10 seconds | ClientGUI.java | How often client sends heartbeat |
| **Monitor Check Interval** | 15 seconds | NodeMonitor.java | How often leader checks for dead nodes |
| **Timeout Threshold** | 30 seconds | NodeMonitor.java | Max time before marking OFFLINE |

## Testing Instructions

### Test 1: Client Online Detection

1. **Start Server**:
   ```
   .\start_server.bat
   ```

2. **Start Client**:
   ```
   .\start_simulator.bat
   ```

3. **Verify**:
   - Client logs show: `Auto-Heartbeat started (every 10 seconds)...`
   - Client sends heartbeat messages automatically
   - Server dashboard shows client status as **ONLINE**

### Test 2: Client Offline Detection

1. **Start server and client** (as above)

2. **Close client** or **disconnect**

3. **Wait 30+ seconds**

4. **Check server dashboard**:
   - Status changes from **ONLINE** to **OFFLINE**
   - Server logs show: `[Monitor] Marked 1 node(s) as OFFLINE due to inactivity.`

### Test 3: Multi-Server Replication

1. **Start Server 1** (ID=1, Port=8080)

2. **Start Server 2** (ID=2, Port=8081, Peers=`1:localhost:8080`)

3. **Start Client** (connects to leader)

4. **Verify**:
   - Both server dashboards show client as **ONLINE**
   - When client disconnects, both eventually show **OFFLINE**

## Status Values

| Status | Meaning | Set By |
|--------|---------|--------|
| **ONLINE** | Client is actively sending heartbeats | ClientHandler on heartbeat receive |
| **OFFLINE** | No heartbeat received for 30+ seconds | NodeMonitor timeout check |

## Database Schema

The `nodes` table stores client status:

```sql
CREATE TABLE nodes (
    node_id VARCHAR(255) PRIMARY KEY,
    last_seen TIMESTAMP,
    last_power_state VARCHAR(50),
    last_load_percent INT,
    transformer_health VARCHAR(50),
    status VARCHAR(50)  -- 'ONLINE' or 'OFFLINE'
);
```

## Logs to Monitor

### Client Logs:
```
Auto-Heartbeat started (every 10 seconds)...
Sent: HEARTBEAT|addis_001|ON|45|ok
Server: OK|HEARTBEAT
```

### Server Logs (Leader):
```
[Client #1] Received: HEARTBEAT|addis_001|ON|45|ok
[Monitor] Marked 1 node(s) as OFFLINE due to inactivity.
```

### Server Logs (Follower):
```
[SYNC] Replicated HEARTBEAT for node addis_001
```

## Advantages

✅ **Automatic**: No manual intervention needed - heartbeats are automatic  
✅ **Real-time**: Status updates within 10-30 seconds  
✅ **Distributed**: All servers have consistent view of client status  
✅ **Fault-tolerant**: Works even if leader fails (new leader takes over monitoring)  
✅ **Resource-efficient**: Minimal network overhead (small heartbeat every 10s)  

## Troubleshooting

### Problem: Client shows OFFLINE even though it's connected

**Possible Causes**:
- Heartbeat thread not starting (check logs for "Auto-Heartbeat started")
- Network connectivity issues
- Server not receiving heartbeats

**Solution**:
- Check client logs for heartbeat messages
- Verify server is receiving heartbeats
- Check network/firewall settings

### Problem: Client takes too long to show OFFLINE after disconnect

**Explanation**: This is normal - it takes up to 30 seconds for timeout detection

**If you need faster detection**:
- Reduce heartbeat interval in ClientGUI (line ~270): `Thread.sleep(5000)` for 5s
- Reduce timeout threshold in NodeMonitor (line ~30): `INTERVAL 15 SECOND` for 15s

### Problem: Status not replicating to follower servers

**Check**:
- Servers are properly configured with peer information
- Election has completed (check for "I am the Leader!" message)
- Network connectivity between servers
- Look for `[SYNC]` messages in follower logs

---

**Implementation Date**: 2025-12-17  
**Status**: ✅ Complete and Tested  
**Files Modified**: 4 (ClientGUI, ClientHandler, NodeMonitor, ElectionManager)
