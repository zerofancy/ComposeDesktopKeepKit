package isolated;

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver;
import java.sql.*;
import java.util.Properties;

public class SqldelightSmoke {
    public static void main(String[] args) throws Exception {
        JdbcSqliteDriver driver = new JdbcSqliteDriver("jdbc:sqlite::memory:", new Properties());
        try (Statement statement = driver.getConnection().createStatement();
             ResultSet result = statement.executeQuery("select 42")) {
            if (!result.next() || result.getInt(1) != 42) throw new AssertionError("SQLDelight");
        } finally { driver.close(); }
        System.out.println("PASS sqldelight isolated");
    }
}
