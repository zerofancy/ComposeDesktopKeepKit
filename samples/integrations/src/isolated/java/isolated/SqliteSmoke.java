package isolated;

import java.sql.*;
public class SqliteSmoke {
 public static void main(String[] args) throws Exception {
  try (Connection c = DriverManager.getConnection("jdbc:sqlite::memory:"); Statement s = c.createStatement(); ResultSet r = s.executeQuery("select 42")) {
   if (!r.next() || r.getInt(1) != 42) throw new AssertionError("SQLite");
  }
  System.out.println("PASS sqlite isolated");
 }
}
