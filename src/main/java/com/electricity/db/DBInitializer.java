package com.electricity.db;

import java.sql.Connection;
import java.sql.Statement;
import java.io.InputStream;
import java.util.Scanner;
import java.nio.charset.StandardCharsets;

public class DBInitializer {
    public static void main(String[] args) {
        try {
            Connection conn = java.sql.DriverManager.getConnection(
                    "jdbc:mysql://localhost:3310/?allowMultiQueries=true&serverTimezone=UTC", "root", "");
            Statement stmt = conn.createStatement();

            InputStream is = DBInitializer.class.getClassLoader().getResourceAsStream("schema.sql");
            if (is == null) {
                System.err.println("schema.sql not found in resources!");
                return;
            }
            try (Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name())) {
                String sql = scanner.useDelimiter("\\A").next();
                try {
                    stmt.execute(sql);
                } catch (Exception e) {
                    System.out.println("Schema execution skipped/partial: " + e.getMessage());
                }
            }

            try {
                System.out.println("Granting remote access...");
                try {
                  stmt.execute("CREATE USER IF NOT EXISTS 'root'@'%' IDENTIFIED BY ''");
                } catch(Exception ex) {}
                stmt.execute("GRANT ALL PRIVILEGES ON *.* TO 'root'@'%' WITH GRANT OPTION");
                stmt.execute("FLUSH PRIVILEGES");
                System.out.println("Remote access granted for root@%");
            } catch (Exception e) {
                System.out.println("Warning granting remote access: " + e.getMessage());
            }

            System.out.println("Database initialized successfully.");
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
