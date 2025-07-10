package walg.liquibase.plugins.audit;

import liquibase.datatype.LiquibaseDataType;
import liquibase.datatype.core.BigIntType;
import liquibase.datatype.core.TimestampType;
import liquibase.statement.AutoIncrementConstraint;
import liquibase.statement.SqlStatement;
import liquibase.statement.core.CreateTableStatement;
import liquibase.statement.core.DropTableStatement;
import liquibase.structure.core.Column;
import liquibase.structure.core.Table;
import walg.liquibase.plugins.shared.DatabaseWrapper;

import java.util.ArrayList;
import java.util.List;

public class AuditTableStatementGenerator {

    public static List<SqlStatement> generateRollbackStatements(Table baseTable) {
        String auditTableName = Names.getAuditTableName(baseTable, false);
        DropTableStatement dropTableStatement = new DropTableStatement(Names.getCatalog(), Names.getSchema(), auditTableName, true);
        return List.of(dropTableStatement);
    }

    public static List<SqlStatement> generateCreateStatements(Table baseTable, DatabaseWrapper databaseWrapper) {
        List<SqlStatement> result = new ArrayList<>();
        result.add(createTable(baseTable, databaseWrapper));
        return result;
    }

    private static SqlStatement createTable(Table baseTable, DatabaseWrapper databaseWrapper) {
        String auditTableName = Names.getAuditTableName(baseTable, false);
        CreateTableStatement createTableStatement = new CreateTableStatement(Names.getCatalog(), Names.getSchema(), auditTableName);
        addAuditColumns(createTableStatement, databaseWrapper);
        addSourceTableColumns(createTableStatement, baseTable, databaseWrapper);
        addConstraints(createTableStatement);
        return createTableStatement;
    }

    private static void addAuditColumns(CreateTableStatement statement, DatabaseWrapper databaseWrapper) {
        statement.addColumn("audit_id", new BigIntType());
        statement.addColumn("audit_timestamp", new TimestampType());
        statement.addColumn("audit_action", databaseWrapper.getLiquibaseDataType("varchar(12)"));
        statement.addColumn("audit_user", databaseWrapper.getLiquibaseDataType("varchar(100)"));
    }

    private static void addConstraints(CreateTableStatement statement) {
        AutoIncrementConstraint constraint = new AutoIncrementConstraint();
        constraint.setColumnName("audit_id");
        constraint.setGenerationType("always");
        statement.addColumnConstraint(constraint);
    }

    private static void addSourceTableColumns(CreateTableStatement statement, Table table, DatabaseWrapper databaseWrapper) {
        for (Column column : table.getColumns()) {
            LiquibaseDataType liquibaseDataType = databaseWrapper.getLiquibaseDataType(column.getType());
            statement.addColumn(column.getName(), liquibaseDataType);
        }
    }
}
