package com.electricity.db;

import java.sql.Connection;
import java.sql.Statement;

public class MigrateDB {
    public static void main(String[] args) {
        try (Connection conn = DBConnection.getConnection();
                Statement stmt = conn.createStatement()) {

            System.out.println("Migrating database...");
            try {
                stmt.execute("ALTER TABLE nodes ADD COLUMN verification_status VARCHAR(20) DEFAULT 'NONE'");
                System.out.println("Added verification_status column.");
            } catch (Exception e) {
                System.out.println("verification_status column might already exist: " + e.getMessage());
            }

            try {
                stmt.execute("ALTER TABLE nodes ADD COLUMN verification_ts TIMESTAMP NULL");
                System.out.println("Added verification_ts column.");
            } catch (Exception e) {
                System.out.println("verification_ts column might already exist: " + e.getMessage());
            }

            System.out.println("Migration complete.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
