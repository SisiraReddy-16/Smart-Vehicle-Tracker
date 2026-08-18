import java.sql.Connection;
import java.sql.DriverManager;

public class TestDB {
    public static void main(String[] args) {
        String[] urls = {
            "jdbc:mysql://localhost:3306/garagepulse?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
            "jdbc:mysql://127.0.0.1:3306/garagepulse?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
        };
        String user = "root";
        String pass = "sisira@160806";

        for (String url : urls) {
            try {
                System.out.println("Trying to connect to " + url);
                Connection conn = DriverManager.getConnection(url, user, pass);
                System.out.println("SUCCESS for " + url);
                conn.close();
            } catch (Exception e) {
                System.out.println("FAILED for " + url + " : " + e.getMessage());
            }
        }
    }
}
