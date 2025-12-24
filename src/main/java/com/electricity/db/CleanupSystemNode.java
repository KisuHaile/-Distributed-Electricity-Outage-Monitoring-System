package com.electricity.db;

import java.sql.Connection;
import java.sql.Statement;

public class CleanupSystemNode {
    public static void main(String[] args) {
        try (Connection conn = DBConnection.getConnection();
                Statement stmt = conn.createStatement()) {

            System.out.println("Cleaning up SYSTEM node...");
            int rows = stmt.executeUpdate("DELETE FROM nodes WHERE node_id='SYSTEM'");

            if (rows > 0) {
                System.out.println("Successfully removed SYSTEM node.");
            } else {
                System.out.println("SYSTEM node not found or already removed.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
