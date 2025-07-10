package walg.liquibase.plugins.data;

import liquibase.change.AbstractChange;
import liquibase.change.ChangeMetaData;
import liquibase.change.DatabaseChange;
import liquibase.change.DatabaseChangeProperty;
import liquibase.database.Database;
import liquibase.exception.ValidationErrors;
import liquibase.sql.Sql;
import liquibase.sqlgenerator.SqlGeneratorFactory;
import liquibase.statement.SqlStatement;
import liquibase.statement.core.DeleteStatement;
import liquibase.statement.core.InsertStatement;
import liquibase.statement.core.RawCallStatement;
import liquibase.statement.core.UpdateStatement;

import java.util.*;

import static liquibase.change.ChangeParameterMetaData.ALL;

@DatabaseChange(name = "loadUpdateDeleteData",
        description = "Makes the data in a table identical to the referenced CSV file. Performs inserts, updates, and deletes as needed.",
        priority = ChangeMetaData.PRIORITY_DEFAULT)
public class LoadUpdateDeleteDataChange extends AbstractChange {

    private static final String UPSERT_SQL_TEMPLATE = """
       DO $$
       BEGIN
           -- update sql
           %s
           IF NOT FOUND THEN
               -- insert sql
               %s
           END IF;
       END;
       $$;""";

    private String file;
    private String primaryKey;
    private String tableName;

    @DatabaseChangeProperty(
            description = "The CSV file to load",
            requiredForDatabase = ALL)
    public String getFile() {
        return file;
    }

    @DatabaseChangeProperty(
            description = "The name of the column representing the primary key of the table (cannot be composite).",
            requiredForDatabase = ALL)
    public String getPrimaryKey() {
        return primaryKey;
    }

    @DatabaseChangeProperty(
            description = "The name of the table to update.",
            requiredForDatabase = ALL)
    public String getTableName() {
        return tableName;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public void setPrimaryKey(String primaryKey) {
        this.primaryKey = primaryKey;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    @Override
    public String getConfirmationMessage() {
        return "";
    }

    @Override
    public String getDescription() {
        return "Synced table %s with file %s".formatted(tableName, file);
    }

    @Override
    public SqlStatement[] generateStatements(Database database) {
        CsvFile csvFile = new CsvFile(file);
        List<SqlStatement> sqlStatements = new ArrayList<>();
        sqlStatements.add(buildDeleteStatement(csvFile));
        sqlStatements.addAll(buildUpsertStatements(csvFile, database));
        return sqlStatements.toArray(new SqlStatement[0]);
    }

    /**
     * Create a delete statement that will delete every row whose primary key is not in the provided file.
     * This will work fine for small files.
     * @param csvFile
     * @return
     */
    private SqlStatement buildDeleteStatement(CsvFile csvFile) {
        DeleteStatement deleteStatement = new DeleteStatement(null, null, tableName);
        List<String> valuesToRetain = csvFile.getValues(primaryKey);
        String inList = String.join(",", Collections.nCopies(valuesToRetain.size(), ":value"));
        deleteStatement.setWhere("%s NOT IN (%s)".formatted(primaryKey, inList));
        for (String value : valuesToRetain) {
            deleteStatement.addWhereParameter(value);
        }
        return deleteStatement;
    }

    /**
     * Build an upsert statement for every row in the provided file.
     * @param csvFile
     * @param database
     * @return a list of SqlStatement objects
     */
    private List<SqlStatement> buildUpsertStatements(CsvFile csvFile, Database database) {
        List<SqlStatement> result = new ArrayList<>();
        List<String> headers = csvFile.getHeaders();
        int primaryKeyIndex = headers.indexOf(primaryKey);
        List<List<String>> rows = csvFile.getRows();
        for (List<String> row : rows) {
            String primaryKeyValue = row.get(primaryKeyIndex);
            SqlStatement updateStatement = buildUpdateStatement(headers, row, primaryKeyIndex, primaryKeyValue);
            SqlStatement insertStatement = buildInsertStatement(headers, row);
            SqlStatement upsertStatement = buildUpsertStatement(updateStatement, insertStatement, database);
            result.add(upsertStatement);
        }
        return result;
    }

    /**
     * Create an update statement.
     * @param headers
     * @param values
     * @param primaryKeyIndex
     * @param primaryKeyValue
     * @return a SqlStatement that updates a single row.
     */
    private SqlStatement buildUpdateStatement(List<String> headers, List<String> values, int primaryKeyIndex, String primaryKeyValue) {
        UpdateStatement result = new UpdateStatement(null, null, tableName);
        for (int i = 0; i < headers.size(); i++) {
            if (i != primaryKeyIndex) {
                result.addNewColumnValue(headers.get(i), values.get(i));
            }
        }
        result.setWhereClause(":name = :value");
        result.addWhereColumnName(primaryKey);
        result.addWhereParameter(primaryKeyValue);
        return result;
    }

    /**
     * Build a statement to insert a new row.
     * @param headers
     * @param values
     * @return An SqlStatement to insert a single row.
     */
    private SqlStatement buildInsertStatement(List<String> headers, List<String> values) {
        InsertStatement result = new InsertStatement(null, null, tableName);
        for (int i = 0; i < headers.size(); i++) {
            result.addColumnValue(headers.get(i), values.get(i));
        }
        return result;
    }

    /**
     * Combine an update statement and an insert statement into a statement that will only attempt to insert a new row
     * if the primary key doesn't yet exist. This lets the database make the insert/update decision instead of trying
     * to do it here in the plugin. 
     * @param updateStatement
     * @param insertStatement
     * @param database
     * @return a SqlStatement that contains a bit of PL/pgSQL to simulate an upsert statement.
     */
    private SqlStatement buildUpsertStatement(SqlStatement updateStatement, SqlStatement insertStatement, Database database) {
        String updateSql = convertToSql(updateStatement, database);
        String insertSql = convertToSql(insertStatement, database);
        String upsertSql = String.format(UPSERT_SQL_TEMPLATE, updateSql, insertSql);
        return new RawCallStatement(upsertSql);
    }

    /**
     * Given a SqlStatement, extract the underlying SQL as a String.
     * @param sqlStatement
     * @param database
     * @return The SQL that this SqlStatement would execute.
     */
    private String convertToSql(SqlStatement sqlStatement, Database database) {
        Sql[] sqls = SqlGeneratorFactory.getInstance().generateSql(sqlStatement, database);
        StringBuilder sb = new StringBuilder();
        for (Sql sql : sqls) {
            sb.append(sql.toSql()).append(";\n");
        }
        return sb.toString();
    }

    @Override
    public ValidationErrors validate(Database database) {
        ValidationErrors errors = new ValidationErrors();
        if (file == null || file.isEmpty()) {
            errors.addError("file is required.");
        }
        if (primaryKey == null || primaryKey.isEmpty()) {
            errors.addError("primaryKey is required.");
        }
        if (tableName == null || tableName.isEmpty()) {
            errors.addError("table is required.");
        }
        if (primaryKey.indexOf(',') != -1) {
            errors.addError("primaryKey must be a single column. Composite keys are not supported.");
        }
        CsvFile csvFile = new CsvFile(file);
        if (csvFile.getRows().size() > 100) {
            errors.addError("The file must contain 100 or fewer records.");
        }
        if (!csvFile.getHeaders().contains(primaryKey)) {
            errors.addError("%s not found in file.".formatted(primaryKey));
        }
        return errors;
    }
}
