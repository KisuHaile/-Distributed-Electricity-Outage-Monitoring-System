package com.electricity.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    private static String host = "localhost";
    private static final String PORT = "3310";
    private static final String DB_NAME = "electricity";
    private static final String PARAMS = "?serverTimezone=UTC";
    private static final String USER = "root";
    private static final String PASS = "";

    public static void configure(String dbHost) {
        host = dbHost;
    }

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC driver not found.");
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        String url = "jdbc:mysql://" + host + ":" + PORT + "/" + DB_NAME + PARAMS;
        return DriverManager.getConnection(url, USER, PASS);
    }
}
