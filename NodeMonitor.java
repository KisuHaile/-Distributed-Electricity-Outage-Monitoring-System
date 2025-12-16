import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class NodeMonitor implements Runnable {

    @Override
    public void run() {
        System.out.println("[Monitor] Dead Node Monitor started.");
        while (true) {
            try {
                // Check every 15 seconds
                Thread.sleep(15000); // 15 seconds

                try (Connection conn = DBConnection.getConnection()) {
                    // Mark nodes as OFFLINE if not seen in 30 seconds
                    String sql = "UPDATE nodes SET status='OFFLINE' WHERE last_seen < (NOW() - INTERVAL 30 SECOND) AND status='alive'";
                    try (PreparedStatement ps = conn.prepareStatement(sql)) {
                        int rows = ps.executeUpdate();
                        if (rows > 0) {
                            System.out.println("[Monitor] Marked " + rows + " node(s) as OFFLINE due to inactivity.");
                        }
                    }
                } catch (SQLException e) {
                    System.err.println("[Monitor] Database error: " + e.getMessage());
                }

            } catch (InterruptedException e) {
                System.out.println("[Monitor] Monitor interrupted.");
                break;
            } catch (Exception e) {
                System.err.println("[Monitor] Unexpected error: " + e.getMessage());
            }
        }
    }
}
