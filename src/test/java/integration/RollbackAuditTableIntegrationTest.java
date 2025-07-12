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
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RollbackAuditTableIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15.1"))
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    private static final Timestamp TIMESTAMP = Timestamp.valueOf(LocalDateTime.of(2000, 1, 2, 3, 4, 5));

    private static Connection connection;
    private static Liquibase liquibase;

    @BeforeAll
    static void beforeAll() throws Exception {
        postgres.start();

        connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());

        Database database = new PostgresDatabase();
        database.setConnection(new JdbcConnection(connection));
        liquibase = new Liquibase("db/changelog/create-audit-table-tests.xml", new ClassLoaderResourceAccessor(), database);
    }

    @BeforeEach
    public void beforeEach() throws LiquibaseException {
        liquibase.update("");
    }

    @AfterAll
    static void afterAll() {
        postgres.stop();
    }

    @Test
    void auditTableDoesNotExist() throws SQLException, LiquibaseException {
        // When
        liquibase.rollback(1, "");

        // Then
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM information_schema.tables where table_name = 'track_stream_audit'")) {
            rs.next();
            assertEquals(0, rs.getInt(1));
        }
    }

    @Test
    void baseTableWorksAfterRollback() throws Exception {
        // When
        liquibase.rollback(1, "");
        deleteAllRows("track_stream");
        insertRow();

        // Then
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM track_stream")) {
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
    }


    private void insertRow() throws SQLException {
        String sql = "INSERT INTO track_stream (type, created_at, total) VALUES (?, ?, ?)";

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, "some-type");
            preparedStatement.setTimestamp(2, TIMESTAMP);
            preparedStatement.setInt(3, 100);
            preparedStatement.executeUpdate();
        }
    }

    private void deleteAllRows(String table) throws SQLException {
        String sql = String.format("delete from %s", table);

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.executeUpdate();
        }
    }
}
