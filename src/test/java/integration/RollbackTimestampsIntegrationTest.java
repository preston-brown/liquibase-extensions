package integration;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.core.PostgresDatabase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

public class RollbackTimestampsIntegrationTest {

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

    @AfterAll
    static void afterAll() {
        postgres.stop();
        postgres.close();
    }

    @Test
    void timestampsNotSetAfterRollback() throws Exception {
        // When
        deleteAllRows();
        liquibase.rollback(1, "");
        insertRow();

        // Then
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT created, updated FROM table_1")) {
            rs.next();
            assertNull(rs.getTimestamp("created"));
            assertNull(rs.getTimestamp("updated"));
        }

        // When
        deleteAllRows();
        liquibase.update("");
        insertRow();

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT created, updated FROM table_1")) {
            rs.next();
            assertNotNull(rs.getTimestamp("created"));
            assertNotNull(rs.getTimestamp("updated"));
            Instant created = rs.getTimestamp("created").toInstant();
            Instant updated = rs.getTimestamp("updated").toInstant();
            long difference = Math.abs(ChronoUnit.SECONDS.between(created, Instant.now()));
            assertTrue(difference < 5);
            difference = Math.abs(ChronoUnit.SECONDS.between(updated, Instant.now()));
            assertTrue(difference < 5);
        }
    }

    private void insertRow() throws SQLException {
        String sql = "INSERT INTO table_1 (type) VALUES (?)";

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, "some-type");
            preparedStatement.executeUpdate();
        }
    }

    private void deleteAllRows() throws SQLException {
        String sql = "DELETE FROM table_1";

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.executeUpdate();
        }
    }
}
