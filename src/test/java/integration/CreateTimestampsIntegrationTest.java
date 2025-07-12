package integration;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.core.PostgresDatabase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

public class CreateTimestampsIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15.1"))
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    private static Connection connection;
    private static Liquibase liquibase;

    @BeforeAll
    static void beforeAll() throws SQLException, LiquibaseException {
        postgres.start();

        connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());

        Database database = new PostgresDatabase();
        database.setConnection(new JdbcConnection(connection));
        liquibase = new Liquibase("db/changelog/timestamp-tests.xml", new ClassLoaderResourceAccessor(), database);
        liquibase.update("");
    }

    @BeforeEach
    void beforeEach() throws SQLException {
        deleteAllRows("table_1");
        deleteAllRows("table_2");
        deleteAllRows("table_3");
        deleteAllRows("table_4");
    }

    @AfterAll
    static void afterAll() {
        postgres.stop();
    }

    @Test
    void timestampsAreSet() throws Exception {
        // When
        insertRow("table_1");

        // Then
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT created_at, updated_at FROM table_1")) {
            rs.next();
            assertNotNull(rs.getTimestamp("created_at"));
            assertNotNull(rs.getTimestamp("updated_at"));

            Instant created = rs.getTimestamp("created_at").toInstant();
            long difference = Math.abs(ChronoUnit.SECONDS.between(created, Instant.now()));
            assertTrue(difference < 5);

            Instant updated = rs.getTimestamp("updated_at").toInstant();
            difference = Math.abs(ChronoUnit.SECONDS.between(updated, Instant.now()));
            assertTrue(difference < 5);
        }
    }

    @Test
    void nonStandardNamedColumnsAreSet() throws Exception {
        // When
        insertRow("table_2");

        // Then
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM table_2")) {
            rs.next();
            assertNotNull(rs.getTimestamp("created_ts"));
            assertNotNull(rs.getTimestamp("updated_ts"));

            Instant created = rs.getTimestamp("created_ts").toInstant();
            long difference = Math.abs(ChronoUnit.SECONDS.between(created, Instant.now()));
            assertTrue(difference < 5);

            Instant updated = rs.getTimestamp("updated_ts").toInstant();
            difference = Math.abs(ChronoUnit.SECONDS.between(updated, Instant.now()));
            assertTrue(difference < 5);
        }
    }

    @Test
    void canToggleOffCreatedTrigger() throws Exception {
        // When
        insertRow("table_3");

        // Then
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM table_3")) {
            rs.next();
            assertNull(rs.getTimestamp("created_at"));
            assertNotNull(rs.getTimestamp("updated_at"));

            Instant updated = rs.getTimestamp("updated_at").toInstant();
            long difference = Math.abs(ChronoUnit.SECONDS.between(updated, Instant.now()));
            assertTrue(difference < 5);
        }
    }

    @Test
    void canToggleOffUpdatedTrigger() throws Exception {
        // When
        insertRow("table_4");

        // Then
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM table_4")) {
            rs.next();
            assertNotNull(rs.getTimestamp("created_at"));
            assertNull(rs.getTimestamp("updated_at"));

            Instant created = rs.getTimestamp("created_at").toInstant();
            long difference = Math.abs(ChronoUnit.SECONDS.between(created, Instant.now()));
            assertTrue(difference < 5);
        }
    }

    private void insertRow(String tableName) throws SQLException {
        String sql = String.format("INSERT INTO %s (type) VALUES (?)", tableName);

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, "some-type");
            preparedStatement.executeUpdate();
        }
    }

    private void deleteAllRows(String tableName) throws SQLException {
        String sql = String.format("DELETE FROM %s", tableName);

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.executeUpdate();
        }
    }
}
