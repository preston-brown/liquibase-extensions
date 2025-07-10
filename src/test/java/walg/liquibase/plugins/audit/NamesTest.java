package walg.liquibase.plugins.audit;

import liquibase.structure.core.Table;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

public class NamesTest {

    @Mock
    private Table table;
    private AutoCloseable mocks;

    @BeforeEach
    public void beforeEach() {
        mocks = MockitoAnnotations.openMocks(this);
        when(table.getName()).thenReturn("test_table");
    }

    @AfterEach
    public void afterEach() throws Exception {
        mocks.close();
    }

    @Test
    void getBaseTableName_includeSchema() {
        // Act
        String baseTableName = Names.getBaseTableName(table, true);

        // Assert
        assertEquals("public.test_table", baseTableName);
    }

    @Test
    void getBaseTableName_excludeSchema() {
        // Act
        String baseTableName = Names.getBaseTableName(table, false);

        // Assert
        assertEquals("test_table", baseTableName);
    }
}