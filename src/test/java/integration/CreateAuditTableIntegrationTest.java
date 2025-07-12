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
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CreateAuditTableIntegrationTest {

    private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15.1"))
            .withDatabaseName("testdb")
            .withUsername("testuser")
            .withPassword("testpass");

    private static final Timestamp TIMESTAMP = Timestamp.valueOf(LocalDateTime.of(2000, 1, 2, 3, 4, 5));
    private static final Timestamp TIMESTAMP2 = Timestamp.valueOf(LocalDateTime.of(2000, 1, 2, 3, 4, 6));

    private static Connection connection;
    private static Liquibase liquibase;

    @BeforeAll
    static void beforeAll() throws SQLException, LiquibaseException {
        postgres.start();

        connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());

        Database database = new PostgresDatabase();
        database.setConnection(new JdbcConnection(connection));
        liquibase = new Liquibase("db/changelog/create-audit-table-tests.xml", new ClassLoaderResourceAccessor(), database);
        liquibase.update("");
    }

    @BeforeEach
    void beforeEach() throws SQLException {
        deleteAllRows("track_stream");
        deleteAllRows("track_stream_audit");
    }

    @AfterAll
    static void afterAll() {
        postgres.stop();
    }

    @Test
    void auditTableExists() throws Exception {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM information_schema.tables where table_name = 'track_stream_audit'")) {
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    void auditTableWorksWithInsert() throws Exception {
        // When
        insertRow();

        // Then
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM track_stream_audit")) {
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM track_stream_audit")) {
            assertTrue(rs.next());
            assertTrue(0 < rs.getInt("audit_id"));
            assertEquals("INSERT", rs.getString("audit_action"));
            assertEquals("testuser", rs.getString("audit_user"));
            assertEquals("some-type", rs.getString("type"));
            assertEquals(TIMESTAMP, rs.getTimestamp("created_at"));
            assertEquals(100, rs.getInt("total"));
        }
    }

    @Test
    void auditTableWorksWithUpdate() throws Exception {
        // When
        insertRow();
        deleteAllRows("track_stream_audit");
        String sql = "update track_stream set type = ?, created_at = ?, total = ?";

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.setString(1, "some-other-type");
            preparedStatement.setTimestamp(2, TIMESTAMP2);
            preparedStatement.setInt(3, 101);
            preparedStatement.executeUpdate();
        }

        // Then
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM track_stream_audit")) {
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM track_stream_audit")) {
            rs.next();
            assertTrue(0 < rs.getInt("audit_id"));
            assertEquals("UPDATE", rs.getString("audit_action"));
            assertEquals("testuser", rs.getString("audit_user"));
            assertEquals("some-other-type", rs.getString("type"));
            assertEquals(TIMESTAMP2, rs.getTimestamp("created_at"));
            assertEquals(101, rs.getInt("total"));
        }
    }

    @Test
    public void auditTableWorksWithDelete() throws Exception {
        // When
        insertRow();
        deleteAllRows("track_stream_audit");
        deleteAllRows("track_stream");

        // Then
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM track_stream_audit")) {
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM track_stream_audit")) {
            rs.next();
            assertTrue(0 < rs.getInt("audit_id"));
            assertEquals("DELETE", rs.getString("audit_action"));
            assertEquals("testuser", rs.getString("audit_user"));
            assertEquals("some-type", rs.getString("type"));
            assertEquals(TIMESTAMP, rs.getTimestamp("created_at"));
            assertEquals(100, rs.getInt("total"));
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
