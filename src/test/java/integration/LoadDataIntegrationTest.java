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

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LoadDataIntegrationTest {

    private static PostgreSQLContainer<?> postgres;
    private static Connection connection;
    private static Liquibase liquibase;

    @BeforeAll
    static void beforeAll() throws SQLException, LiquibaseException {
        postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:15.1"))
                .withDatabaseName("testdb")
                .withUsername("testuser")
                .withPassword("testpass");
        postgres.start();

        connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());

        Database database = new PostgresDatabase();
        database.setConnection(new JdbcConnection(connection));
        liquibase = new Liquibase("db/changelog/load-data-tests.xml", new ClassLoaderResourceAccessor(), database);
        liquibase.update("init");
    }

    @AfterAll
    static void afterAll() {
        postgres.stop();
    }

    @FunctionalInterface
    public interface ResultSetConsumer {
        void accept(ResultSet rs) throws SQLException;
    }

    private void withSingleRow(String sql, ResultSetConsumer consumer) throws Exception {
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (!rs.next()) {
                throw new AssertionError("Expected at least one row for query: " + sql);
            }
            consumer.accept(rs);
        }
    }

    @Test
    public void tableWithCompositeKeys() throws Exception {
        deleteAllRows("composite_key_tbl");
        liquibase.update("composite-test-1");
        assertCountIsOne("select count(*) from composite_key_tbl where key1 = 'a' and key2 = 1 and value = 'alpha'");
        assertCountIsOne("select count(*) from composite_key_tbl where key1 = 'b' and key2 = 2 and value = 'beta'");
        assertCountIsOne("select count(*) from composite_key_tbl where key1 = 'c' and key2 = 3 and value = 'gamma'");
        assertCountIsOne("select count(*) from composite_key_tbl where key1 = 'd' and key2 = 4 and value = 'delta'");
        withSingleRow("select count(*) from composite_key_tbl", rs -> assertEquals(4, rs.getInt(1)));
        liquibase.update("composite-test-2");
        assertCountIsOne("select count(*) from composite_key_tbl where key1 = 'a' and key2 = 1 and value = 'epsilon'");
        assertCountIsOne("select count(*) from composite_key_tbl where key1 = 'd' and key2 = 4 and value = 'zeta'");
        withSingleRow("select count(*) from composite_key_tbl", rs -> assertEquals(2, rs.getInt(1)));
        liquibase.update("composite-test-3");
        assertCountIsOne("select count(*) from composite_key_tbl where key1 = 'a' and key2 = 1 and value = 'epsilon'");
        assertCountIsOne("select count(*) from composite_key_tbl where key1 = 'd' and key2 = 4 and value = 'zeta'");
        withSingleRow("select count(*) from composite_key_tbl", rs -> assertEquals(2, rs.getInt(1)));
    }

    @Test
    public void tableWithSimpleKeys() throws Exception {
        deleteAllRows("simple_key_tbl");
        liquibase.update("simple-test-1");
        assertCountIsOne("select count(*) from simple_key_tbl where key = 1 and value = 'alpha'");
        assertCountIsOne("select count(*) from simple_key_tbl where key = 2 and value = 'beta'");
        assertCountIsOne("select count(*) from simple_key_tbl where key = 3 and value = 'gamma'");
        withSingleRow("select count(*) from simple_key_tbl", rs -> assertEquals(3, rs.getInt(1)));
        liquibase.update("simple-test-2");
        assertCountIsOne("select count(*) from simple_key_tbl where key = 2 and value = 'delta'");
        withSingleRow("select count(*) from simple_key_tbl", rs -> assertEquals(1, rs.getInt(1)));
        liquibase.update("simple-test-3");
        assertCountIsOne("select count(*) from simple_key_tbl where key = 2 and value = 'delta'");
        withSingleRow("select count(*) from simple_key_tbl", rs -> assertEquals(1, rs.getInt(1)));
    }

    private void assertCountIsOne(String sql) throws Exception {
        withSingleRow(sql, rs -> assertEquals(1, rs.getInt(1)));
    }

    private void deleteAllRows(String table) throws SQLException {
        String sql = String.format("delete from %s", table);
        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            preparedStatement.executeUpdate();
        }
    }
}
