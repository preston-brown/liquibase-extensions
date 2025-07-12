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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    /**
     * Returns all the SQL statements necessary to synchronize an existing database table with the contents of a
     * CSV file. Statements returned will include inserts, updates, and deletes.
     * <p>
     * If the CSV file is empty, don't return any statements. In that case we don't want to perform any updates. The
     * alternative would be to delete every row in the table, and that seems like a bad assumption to make.
     *
     * @param database the database to operate against
     * @return an array of SqlStatement objects
     */
    @Override
    public SqlStatement[] generateStatements(Database database) {
        CsvFile csvFile = new CsvFile(file);
        if (csvFile.size() == 0) {
            return new SqlStatement[0];
        }
        List<SqlStatement> sqlStatements = new ArrayList<>();
        sqlStatements.add(buildDeleteStatement(csvFile));
        sqlStatements.addAll(buildUpsertStatements(csvFile, database));
        return sqlStatements.toArray(new SqlStatement[0]);
    }

    /**
     * Create a delete statement that will delete every row whose primary key is not in the provided file.
     *
     * @param csvFile CSV file containing the data to load
     * @return a SqlStatement that will delete all the rows in the table not represented in the CSV file
     */
    private SqlStatement buildDeleteStatement(CsvFile csvFile) {
        DeleteStatement deleteStatement = new DeleteStatement(null, null, tableName);
        List<String> headers = csvFile.getHeaders();
        List<Integer> pkIndexes = Stream.of(primaryKey.split(",")).map(headers::indexOf).toList();
        String primaryKeys = pkIndexes.stream().map(headers::get).collect(Collectors.joining(", ", "(", ")"));
        // A string that looks like this (?, ?, ?) containing a ? for every primary key field
        String inListItem = Collections.nCopies(pkIndexes.size(), "?").stream().collect(Collectors.joining(", ", "(", ")"));
        // We need one (?, ?, ?) for every row in the file
        String inListItems = Collections.nCopies(csvFile.size(), inListItem).stream().collect(Collectors.joining(", ", "(", ")"));
        String whereClause = primaryKeys + " not in " + inListItems;
        deleteStatement.setWhere(whereClause);
        // Add parameters to the query to account for all the placeholders added above
        for (List<String> row : csvFile.getRows()) {
            for (int index : pkIndexes) {
                deleteStatement.addWhereParameter(row.get(index));
            }
        }
        return deleteStatement;
    }

    /**
     * Build an upsert statement for every row in the provided file. An upsert statement executes an update statement
     * first. If the update had no impact, it then executes an insert statement.
     *
     * @param csvFile the CSV file to load data from
     * @param database the database to operate in
     * @return a list of SqlStatement objects
     */
    private List<SqlStatement> buildUpsertStatements(CsvFile csvFile, Database database) {
        List<SqlStatement> result = new ArrayList<>();
        List<String> headers = csvFile.getHeaders();
        List<String> primaryKeyColumnNames = List.of(primaryKey.split(","));
        List<Integer> primaryKeyColumnIndexes = primaryKeyColumnNames.stream().map(headers::indexOf).toList();
        List<List<String>> rows = csvFile.getRows();
        for (List<String> row : rows) {
            SqlStatement updateStatement = buildUpdateStatement(headers, row, primaryKeyColumnIndexes);
            SqlStatement insertStatement = buildInsertStatement(headers, row);
            SqlStatement upsertStatement = buildUpsertStatement(updateStatement, insertStatement, database);
            result.add(upsertStatement);
        }
        return result;
    }

    /**
     * Create an update statement putting the key columns in the where clause and the other columns in the update clause.
     *
     * @param columnNames the columnNames in the table being updated
     * @param values the values from one row of the CSV file, in the same order as the values in columnNames
     * @param pkIndexes the locations of the primary key columns in the columnNames list
     * @return a SqlStatement that updates a single row.
     */
    private SqlStatement buildUpdateStatement(List<String> columnNames, List<String> values, List<Integer> pkIndexes) {
        UpdateStatement result = new UpdateStatement(null, null, tableName);
        List<String> whereConditions = new ArrayList<>();
        for (int i = 0; i < columnNames.size(); i++) {
            String columnName = columnNames.get(i);
            String value = values.get(i);
            if (pkIndexes.contains(i)) {
                whereConditions.add(columnName + " = :value");
                result.addWhereParameter(value);
            } else {
                result.addNewColumnValue(columnName, value);
            }
        }
        result.setWhereClause(String.join(" and ", whereConditions));
        return result;
    }

    /**
     * Build a statement to insert a new row.
     *
     * @param columnNames the names of the columns in the table we are inserting into
     * @param values the values to insert
     * @return An SqlStatement to insert a single row.
     */
    private SqlStatement buildInsertStatement(List<String> columnNames, List<String> values) {
        InsertStatement result = new InsertStatement(null, null, tableName);
        for (int i = 0; i < columnNames.size(); i++) {
            result.addColumnValue(columnNames.get(i), values.get(i));
        }
        return result;
    }

    /**
     * Combine an update statement and an insert statement into a statement that will only attempt to insert a new row
     * if the primary key doesn't yet exist. This lets the database make the insert/update decision instead of trying
     * to do it here in the plugin.
     *
     * @param updateStatement an update statement
     * @param insertStatement an insert statement
     * @param database the database we are operating in
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
     *
     * @param sqlStatement Liquibase SqlStatment
     * @param database the current database
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
        CsvFile csvFile = new CsvFile(file);
        if (csvFile.getRows().size() > 100) {
            errors.addError("The file must contain 100 or fewer records.");
        }
        return errors;
    }
}
