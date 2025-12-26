package com.electricity.rmi;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Lab 2: RMI Server Starter
 * 
 * This class starts the RMI registry and binds the AdminService
 * to make it available for remote clients.
 */
public class RMIServer {

    private static final int RMI_PORT = 1099;
    private static final String SERVICE_NAME = "ElectricityAdmin";

    public static void start() {
        try {
            // Create and export the admin service
            AdminServiceImpl adminService = new AdminServiceImpl();

            // Create RMI registry on default port
            Registry registry;
            try {
                registry = LocateRegistry.createRegistry(RMI_PORT);
                System.out.println("[RMI] Created RMI registry on port " + RMI_PORT);
            } catch (Exception e) {
                // Registry might already exist
                registry = LocateRegistry.getRegistry(RMI_PORT);
                System.out.println("[RMI] Using existing RMI registry on port " + RMI_PORT);
            }

            // Bind the service
            registry.rebind(SERVICE_NAME, adminService);

            System.out.println("[RMI] SUCCESS: AdminService bound successfully!");
            System.out.println("[RMI] Service Name: " + SERVICE_NAME);
            System.out.println("[RMI] RMI URL: rmi://localhost:" + RMI_PORT + "/" + SERVICE_NAME);
            System.out.println("[RMI] Remote admin console can now connect.");

        } catch (Exception e) {
            System.err.println("[RMI] ERROR: Failed to start RMI server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        start();
        System.out.println("[RMI] Press Ctrl+C to stop...");

        // Keep alive
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("[RMI] Shutting down...");
        }
    }
}
