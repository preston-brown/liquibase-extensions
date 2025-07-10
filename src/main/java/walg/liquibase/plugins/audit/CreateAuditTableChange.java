package walg.liquibase.plugins.audit;

import liquibase.change.AbstractChange;
import liquibase.change.ChangeMetaData;
import liquibase.change.DatabaseChange;
import liquibase.change.DatabaseChangeProperty;
import liquibase.database.Database;
import liquibase.statement.SqlStatement;
import liquibase.structure.core.Table;
import walg.liquibase.plugins.shared.DatabaseWrapper;

import java.util.ArrayList;
import java.util.List;

import static liquibase.change.ChangeParameterMetaData.ALL;

@DatabaseChange(
        name = "createAuditTable",
        description = "Create a new audit table",
        priority = ChangeMetaData.PRIORITY_DEFAULT)
public class CreateAuditTableChange extends AbstractChange {

    private String tableName;

    @DatabaseChangeProperty(
            description = "The name of the based table to base the audit table on.",
            requiredForDatabase = ALL)
    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    @Override
    public String getConfirmationMessage() {
        return "";
    }

    @Override
    public boolean generateStatementsVolatile(Database database) {
        return true;
    }

    @Override
    public SqlStatement[] generateRollbackStatements(Database database) {
        DatabaseWrapper databaseWrapper = new DatabaseWrapper(database);
        Table table = databaseWrapper.getTable(tableName);
        List<SqlStatement> result = new ArrayList<>();
        result.addAll(AuditTriggerStatementGenerator.generateRollbackStatements(table));
        result.addAll(AuditFunctionStatementGenerator.generateRollbackStatements(table));
        result.addAll(AuditTableStatementGenerator.generateRollbackStatements(table));
        return result.toArray(new SqlStatement[0]);
    }

    @Override
    public SqlStatement[] generateStatements(Database database) {
        DatabaseWrapper databaseWrapper = new DatabaseWrapper(database);
        Table table = databaseWrapper.getTable(tableName);
        List<SqlStatement> result = new ArrayList<>();
        result.addAll(AuditTableStatementGenerator.generateCreateStatements(table, databaseWrapper));
        result.addAll(AuditFunctionStatementGenerator.generateCreateStatements(table));
        result.addAll(AuditTriggerStatementGenerator.generateCreateStatements(table));
        return result.toArray(new SqlStatement[0]);
    }
}
