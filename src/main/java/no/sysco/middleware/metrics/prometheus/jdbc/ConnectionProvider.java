package no.sysco.middleware.metrics.prometheus.jdbc;

import static java.util.Objects.requireNonNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

interface ConnectionProvider {

    /** A connection provider that simply delegates to {@link java.sql.DriverManager}. */
    static ConnectionProvider DRIVER_MANAGER = new ConnectionProvider() {
        @Override
        public Connection getConnection(String url, Map<String, String> props) throws SQLException {
            final var properties = new Properties();
            props.forEach(properties::setProperty);
            return DriverManager.getConnection(requireNonNull(url), properties);
        }

        @Override
        public String toString() {
            return "ConnectionProvider.DRIVER_MANAGER";
        }
    };

    /**
     * Connects to a database via JDBC.
     *
     * @param url
     *            JDBC URL that describes the database connection to be opened
     * @param props
     *            additional connection properties passed to the JDBC driver
     * @throws SQLException
     *             if an error occurred while establishing a connection to the database
     * @throws NullPointerException
     *             if any of the parameters is {@code null}
     */
    Connection getConnection(String url, Map<String, String> props) throws SQLException;
}
