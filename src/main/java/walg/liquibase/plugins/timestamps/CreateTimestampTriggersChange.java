package walg.liquibase.plugins.timestamps;

import liquibase.change.AbstractChange;
import liquibase.change.ChangeMetaData;
import liquibase.change.DatabaseChange;
import liquibase.change.DatabaseChangeProperty;
import liquibase.database.Database;
import liquibase.statement.SqlStatement;
import liquibase.statement.core.RawCallStatement;
import liquibase.structure.core.Table;
import walg.liquibase.plugins.shared.DatabaseWrapper;
import walg.liquibase.plugins.audit.Names;

import java.util.ArrayList;
import java.util.List;

import static liquibase.change.ChangeParameterMetaData.ALL;
import static liquibase.change.ChangeParameterMetaData.NONE;

@DatabaseChange(
        name = "createTimestampTriggers",
        description = "Create triggers for created and updated columns",
        priority = ChangeMetaData.PRIORITY_DEFAULT)
public class CreateTimestampTriggersChange extends AbstractChange {

    private static final String CREATE_ROW_CREATED_FUNCTION = """
            create or replace function %s
            returns trigger as $$
            begin
                new.%s = localtimestamp;
            return new;
            end;
            $$ language plpgsql;""";

    private static final String CREATE_ROW_CREATED_TRIGGER = """
            create or replace trigger %s
            before insert on %s
            for each row execute function %s;""";

    private static final String CREATE_ROW_UPDATED_FUNCTION = """
            create or replace function %s
            returns trigger as $$
            begin
                if row(new.*) is distinct from row(old.*) then
                    new.%s = localtimestamp;
                end if;
            return new;
            end;
            $$ language plpgsql;""";

    private static final String CREATE_ROW_UPDATED_TRIGGER = """
            create or replace trigger %s
            before insert or update on %s
            for each row execute function %s;""";

    private static final String DROP_TRIGGER = "drop trigger if exists %s on %s;";

    private static final String ROW_CREATED_TRIGGER_NAME = "created";
    private static final String ROW_UPDATED_TRIGGER_NAME = "updated";

    private String tableName;
    private String createdColumnName = "created_at";
    private String updatedColumnName = "updated_at";
    private boolean includeCreatedTrigger = true;
    private boolean includeUpdatedTrigger = true;

    @DatabaseChangeProperty(
            description = "The name of the table to update.",
            requiredForDatabase = ALL)
    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    @DatabaseChangeProperty(
            description = "The name of the column that stores the created timestamp. Default value is 'created'.",
            requiredForDatabase = NONE
    )
    public String getCreatedColumnName() {
        return createdColumnName;
    }

    public void setCreatedColumnName(String createdColumnName) {
        this.createdColumnName = createdColumnName;
    }

    @DatabaseChangeProperty(
            description = "The name of the column that stores the updated timestamp. Default value is 'updated'.",
            requiredForDatabase = NONE
    )
    public String getUpdatedColumnName() {
        return updatedColumnName;
    }

    public void setUpdatedColumnName(String updatedColumnName) {
        this.updatedColumnName = updatedColumnName;
    }

    @DatabaseChangeProperty(
            description = "Indicates if a trigger should be created for row created events. Default value is 'true'.",
            requiredForDatabase = NONE
    )
    public boolean getIncludeCreatedTrigger() {
        return includeCreatedTrigger;
    }

    public void setIncludeCreatedTrigger(boolean includeCreatedTrigger) {
        this.includeCreatedTrigger = includeCreatedTrigger;
    }

    @DatabaseChangeProperty(
            description = "Indicates if a trigger should be created for row updated events. Default value is 'true'.",
            requiredForDatabase = NONE
    )
    public boolean getIncludeUpdatedTrigger() {
        return includeUpdatedTrigger;
    }

    public void setIncludeUpdatedTrigger(boolean includeUpdatedTrigger) {
        this.includeUpdatedTrigger = includeUpdatedTrigger;
    }

    @Override
    public SqlStatement[] generateRollbackStatements(Database database) {
        DatabaseWrapper databaseWrapper = new DatabaseWrapper(database);
        Table table = databaseWrapper.getTable(tableName);
        List<SqlStatement> result = new ArrayList<>();
        if (includeCreatedTrigger) {
            result.add(dropTrigger(table, ROW_CREATED_TRIGGER_NAME));
        }
        if (includeUpdatedTrigger) {
            result.add(dropTrigger(table, ROW_UPDATED_TRIGGER_NAME));
        }
        return result.toArray(new SqlStatement[0]);
    }

    @Override
    public SqlStatement[] generateStatements(Database database) {
        DatabaseWrapper databaseWrapper = new DatabaseWrapper(database);
        Table table = databaseWrapper.getTable(tableName);
        List<SqlStatement> result = new ArrayList<>();
        if (includeCreatedTrigger) {
            result.add(createRowCreatedFunction(table));
            result.add(createRowCreatedTrigger(table));
        }
        if (includeUpdatedTrigger) {
            result.add(createRowUpdatedFunction(table));
            result.add(createRowUpdatedTrigger(table));
        }
        return result.toArray(new SqlStatement[0]);
    }

    @Override
    public boolean generateStatementsVolatile(Database database) {
        return true;
    }

    @Override
    public String getConfirmationMessage() {
        return "";
    }

    private RawCallStatement createRowCreatedFunction(Table table) {
        String functionName = table.getName() + "_row_created()";
        String sql = String.format(CREATE_ROW_CREATED_FUNCTION, functionName, createdColumnName);
        return new RawCallStatement(sql);
    }

    private RawCallStatement createRowCreatedTrigger(Table table) {
        String baseTableName = Names.getBaseTableName(table, true);
        String functionName = table.getName() + "_row_created()";
        String sql = String.format(CREATE_ROW_CREATED_TRIGGER, ROW_CREATED_TRIGGER_NAME, baseTableName, functionName);
        return new RawCallStatement(sql);
    }

    private RawCallStatement createRowUpdatedFunction(Table table) {
        String functionName = table.getName() + "_row_updated()";
        String sql = String.format(CREATE_ROW_UPDATED_FUNCTION, functionName, updatedColumnName);
        return new RawCallStatement(sql);
    }

    private RawCallStatement createRowUpdatedTrigger(Table table) {
        String baseTableName = Names.getBaseTableName(table, true);
        String functionName = table.getName() + "_row_updated()";
        String sql = String.format(CREATE_ROW_UPDATED_TRIGGER, ROW_UPDATED_TRIGGER_NAME, baseTableName, functionName);
        return new RawCallStatement(sql);
    }

    private SqlStatement dropTrigger(Table table, String triggerName) {
        String baseTableName = Names.getBaseTableName(table, true);
        String sql = String.format(DROP_TRIGGER, triggerName, baseTableName);
        return new RawCallStatement(sql);
    }
}
