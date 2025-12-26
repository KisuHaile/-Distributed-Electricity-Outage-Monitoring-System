package com.electricity.db;

import java.sql.Connection;
import java.sql.Statement;

/**
 * Lab 4: Database Migration for Lamport Clock Support
 * 
 * This migration adds logical_timestamp columns to existing tables
 * to support Lamport logical clock synchronization.
 */
public class MigrateLamportClock {

    public static void main(String[] args) {
        String host = "localhost";
        if (args.length > 0) {
            host = args[0];
        }
        migrate(host);
    }

    public static void migrate(String dbHost) {
        System.out.println("[Migration] Adding Lamport Clock support to database...");

        try {
            DBConnection.configure(dbHost);
            try (Connection conn = DBConnection.getConnection();
                    Statement stmt = conn.createStatement()) {

                // Add logical_timestamp to nodes table if it doesn't exist
                try {
                    stmt.execute("ALTER TABLE nodes ADD COLUMN logical_timestamp BIGINT DEFAULT 0");
                    System.out.println("[Migration] SUCCESS: Added logical_timestamp to nodes table");
                } catch (Exception e) {
                    if (e.getMessage().contains("Duplicate column")) {
                        System.out.println("[Migration] SUCCESS: logical_timestamp already exists in nodes table");
                    } else {
                        throw e;
                    }
                }

                // Add logical_timestamp to events table if it doesn't exist
                try {
                    stmt.execute("ALTER TABLE events ADD COLUMN logical_timestamp BIGINT DEFAULT 0");
                    System.out.println("[Migration] SUCCESS: Added logical_timestamp to events table");
                } catch (Exception e) {
                    if (e.getMessage().contains("Duplicate column")) {
                        System.out.println("[Migration] SUCCESS: logical_timestamp already exists in events table");
                    } else {
                        throw e;
                    }
                }

                System.out.println("[Migration] SUCCESS: Database migration completed successfully!");

            }
        } catch (Exception e) {
            System.err.println("[Migration] ERROR: Migration failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
