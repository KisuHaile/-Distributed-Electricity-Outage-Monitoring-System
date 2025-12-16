import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    private static final String URL = "jdbc:mysql://localhost:3306/electricity?serverTimezone=UTC";
    private static final String USER = "root";
    private static final String PASS = "";

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("MySQL JDBC driver not found.");
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        // For high scalability, use a connection pool (e.g., HikariCP) in production.
        // For this implementation, we use direct connections which are sufficient for
        // <100 concurrent clients.
        return DriverManager.getConnection(URL, USER, PASS);
    }
}
