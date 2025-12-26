package com.electricity.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

/**
 * Lab 2: RMI Remote Admin Interface
 * 
 * This interface defines remote methods that can be called from
 * an admin console to monitor and control the distributed system.
 */
public interface AdminService extends Remote {

    /**
     * Get all nodes and their current status
     * 
     * @return List of node information maps
     */
    List<Map<String, Object>> getAllNodes() throws RemoteException;

    /**
     * Get detailed information about a specific node
     * 
     * @param nodeId the node identifier
     * @return Map containing node details
     */
    Map<String, Object> getNodeDetails(String nodeId) throws RemoteException;

    /**
     * Get recent events from the system
     * 
     * @param limit maximum number of events to return
     * @return List of event maps
     */
    List<Map<String, Object>> getRecentEvents(int limit) throws RemoteException;

    /**
     * Get events for a specific node
     * 
     * @param nodeId the node identifier
     * @param limit  maximum number of events
     * @return List of event maps
     */
    List<Map<String, Object>> getNodeEvents(String nodeId, int limit) throws RemoteException;

    /**
     * Manually trigger verification for a node
     * 
     * @param nodeId the node to verify
     * @return success message
     */
    String triggerVerification(String nodeId) throws RemoteException;

    /**
     * Get cluster status (all servers)
     * 
     * @return Map with cluster information
     */
    Map<String, Object> getClusterStatus() throws RemoteException;

    /**
     * Get server statistics
     * 
     * @return Map with statistics (uptime, message count, etc.)
     */
    Map<String, Object> getServerStats() throws RemoteException;

    /**
     * Manually mark a node as resolved
     * 
     * @param nodeId       the node identifier
     * @param operatorName name of the operator performing the action
     * @return success message
     */
    String markNodeResolved(String nodeId, String operatorName) throws RemoteException;

    /**
     * Get current Lamport clock value (Lab 4 integration)
     * 
     * @return current logical timestamp
     */
    long getCurrentLogicalTime() throws RemoteException;

    /**
     * Check if this server is the current leader
     * 
     * @return true if leader, false otherwise
     */
    boolean isLeader() throws RemoteException;

    /**
     * Get server ID
     * 
     * @return the server's unique identifier
     */
    int getServerId() throws RemoteException;
}
