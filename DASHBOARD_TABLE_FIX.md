# Server Dashboard Table Display Fix

## Problem
The server dashboard table was not displaying any client/node data, even when clients were connected and sending heartbeats.

## Root Cause
The dashboard was trying to display data from an **in-memory cache** (`ElectionManager.nodeStates`) which:
1. Only stored data temporarily during the current session
2. Was lost when the server restarted
3. Wasn't always populated if the election/sync mechanism had issues

## Solution
Changed the dashboard to fetch data **directly from the database** instead of relying on the in-memory cache.

## Changes Made

### File: `ServerGUI.java`
**Location**: `src/main/java/com/electricity/server/ui/ServerGUI.java`

#### What Changed:

**Before** (Lines 277-297):
```java
private void updateDashboardLoop() {
    while (running) {
        try {
            Thread.sleep(2000);
            if (electionManager != null) {
                Map<String, String[]> snapshot = electionManager.getNodeStateSnapshot();
                SwingUtilities.invokeLater(() -> updateTable(snapshot));
            }
        } catch (Exception e) {
            // ignore
        }
    }
}
```

**After**:
```java
private void updateDashboardLoop() {
    while (running) {
        try {
            Thread.sleep(2000);
            // Fetch data from database instead of just in-memory cache
            SwingUtilities.invokeLater(() -> updateTableFromDatabase());
        } catch (Exception e) {
            // ignore
        }
    }
}
```

#### New Method Added: `updateTableFromDatabase()`

```java
private void updateTableFromDatabase() {
    try (java.sql.Connection conn = DBConnection.getConnection()) {
        String query = "SELECT node_id, status, last_load_percent, last_power_state, " +
                "transformer_health, DATE_FORMAT(last_seen, '%H:%i:%s') as last_seen_time " +
                "FROM nodes ORDER BY node_id";
        
        try (java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery(query)) {
            
            tableModel.setRowCount(0); // Clear existing rows
            
            // Add rows from database
            while (rs.next()) {
                String nodeId = rs.getString("node_id");
                String status = rs.getString("status");
                String load = rs.getString("last_load_percent");
                String power = rs.getString("last_power_state");
                String transformer = rs.getString("transformer_health");
                String lastSeen = rs.getString("last_seen_time");
                
                tableModel.addRow(new Object[]{
                    nodeId, 
                    status != null ? status : "UNKNOWN", 
                    load != null ? load : "0", 
                    power != null ? power : "unknown", 
                    transformer != null ? transformer : "unknown", 
                    lastSeen != null ? lastSeen : "never"
                });
            }
        }
    } catch (Exception e) {
        // Database error - log but don't crash UI
        if (e.getMessage() != null && !e.getMessage().contains("last packet sent successfully")) {
            System.err.println("[Dashboard] Error updating table: " + e.getMessage());
        }
    }
}
```

## Benefits

### ✅ **Persistent Data**
- Data survives server restarts
- All historical data is visible

### ✅ **Complete View**
- Shows ALL nodes from the database
- Not limited to currently active connections

### ✅ **Accurate Status**
- Status reflects database state (ONLINE/OFFLINE)
- Updates every 2 seconds from source of truth

### ✅ **Consistent Across Servers**
- All servers show the same data
- No synchronization issues with in-memory state

## How It Works Now

```
Every 2 seconds:
    ↓
Query Database
    ↓
SELECT all nodes with their status
    ↓
Clear table
    ↓
Populate with fresh data
    ↓
Display in dashboard
```

## Dashboard Columns

| Column | Source | Description |
|--------|--------|-------------|
| **Node ID** | `node_id` | Unique identifier for the client |
| **Status** | `status` | ONLINE or OFFLINE |
| **Load (%)** | `last_load_percent` | Last reported load percentage |
| **Power State** | `last_power_state` | ON or OFF |
| **Transformer** | `transformer_health` | ok, degraded, etc. |
| **Last Seen** | `last_seen` | Time of last heartbeat (HH:MM:SS) |

## Database Query

The dashboard uses this SQL query:

```sql
SELECT 
    node_id, 
    status, 
    last_load_percent, 
    last_power_state, 
    transformer_health, 
    DATE_FORMAT(last_seen, '%H:%i:%s') as last_seen_time 
FROM nodes 
ORDER BY node_id
```

## Testing

### Before the Fix:
- ❌ Dashboard table was empty
- ❌ No data visible even with connected clients
- ❌ In-memory cache wasn't populated

### After the Fix:
- ✅ Dashboard shows all nodes from database
- ✅ Updates every 2 seconds
- ✅ Status displays correctly (ONLINE/OFFLINE)
- ✅ All client information visible

### How to Verify:

1. **Start Database** (if not running):
   ```
   .\init_db.bat
   ```

2. **Start Server**:
   ```
   .\start_server.bat
   ```

3. **Check Dashboard**:
   - Should show empty table initially (no nodes in database yet)

4. **Start Client**:
   ```
   .\start_simulator.bat
   ```

5. **Send Heartbeat** from client (or wait 10s for auto-heartbeat)

6. **Verify Dashboard**:
   - Table should populate with client data
   - Status shows "ONLINE"
   - Load, Power State, etc. all visible
   - Last Seen shows recent timestamp

7. **Disconnect Client**

8. **Wait 30+ seconds**

9. **Verify Dashboard**:
   - Status changes to "OFFLINE"
   - All other data remains visible

## Edge Cases Handled

### Empty Database
- Shows empty table (no error)
- As soon as first client sends heartbeat, appears in table

### Database Connection Error
- Error logged to console
- UI doesn't crash
- Will retry on next update cycle (2 seconds)

### Null Values
- All null values have sensible defaults:
  - Status: "UNKNOWN"
  - Load: "0"
  - Power/Transformer: "unknown"
  - Last Seen: "never"

### Large Number of Nodes
- Query is ordered by node_id for consistent display
- Updates efficiently (clears and repopulates)

## Performance

- **Update Frequency**: Every 2 seconds
- **Query Complexity**: Simple SELECT (very fast)
- **Network Impact**: Minimal (local database query)
- **UI Impact**: Smooth updates (uses SwingUtilities.invokeLater)

## Troubleshooting

### Problem: Table still empty after connecting client

**Check**:
1. Is database running?
2. Did client send heartbeat? (check client logs)
3. Did server receive heartbeat? (check server logs)
4. Is data in database? Run: `SELECT * FROM nodes;`

### Problem: Table shows old data

**Explanation**: This is normal - the database stores all historical nodes

**To clear old data**:
```sql
DELETE FROM nodes WHERE status = 'OFFLINE' AND last_seen < NOW() - INTERVAL 1 DAY;
```

### Problem: Table updates slowly

**Current update interval**: 2 seconds

**To make faster** (edit ServerGUI.java line ~280):
```java
Thread.sleep(1000); // Update every 1 second
```

---

**Status**: ✅ Fixed and Tested  
**Date**: 2025-12-17  
**File Modified**: ServerGUI.java
