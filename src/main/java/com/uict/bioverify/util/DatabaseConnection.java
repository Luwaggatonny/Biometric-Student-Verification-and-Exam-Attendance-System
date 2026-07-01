package com.uict.bioverify.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.LinkedBlockingQueue;

public class DatabaseConnection {

    private static String url;
    private static String username;
    private static String password;
    private static final LinkedBlockingQueue<Connection> connectionPool = new LinkedBlockingQueue<>();
    private static final int INITIAL_POOL_SIZE = 10;
    private static final int MAX_POOL_SIZE = 20;

    public static void initialize(Properties props) throws ClassNotFoundException, SQLException {
        url = props.getProperty("spring.datasource.url");
        username = props.getProperty("spring.datasource.username");
        password = props.getProperty("spring.datasource.password", "");
        String driver = props.getProperty("spring.datasource.driver-class-name");
        
        Class.forName(driver);

        for (int i = 0; i < INITIAL_POOL_SIZE; i++) {
            connectionPool.add(createConnection());
        }
    }

    private static Connection createConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    public static Connection getConnection() throws SQLException {
        Connection conn = connectionPool.poll();
        if (conn == null) {
            return createConnection();
        }
        try {
            if (conn.isClosed()) {
                return createConnection();
            }
        } catch (SQLException e) {
            return createConnection();
        }
        return conn;
    }

    public static void releaseConnection(Connection connection) {
        if (connection == null) return;
        try {
            if (connection.isClosed()) {
                return;
            }
        } catch (SQLException e) {
            return;
        }
        if (connectionPool.size() < MAX_POOL_SIZE) {
            connectionPool.add(connection);
        } else {
            try {
                connection.close();
            } catch (SQLException e) {
                // ignore
            }
        }
    }
}
