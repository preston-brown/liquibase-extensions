package walg.liquibase.plugins.shared;

import liquibase.database.Database;
import liquibase.datatype.DataTypeFactory;
import liquibase.datatype.LiquibaseDataType;
import liquibase.exception.DatabaseException;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.snapshot.SnapshotControl;
import liquibase.snapshot.SnapshotGeneratorFactory;
import liquibase.structure.core.DataType;
import liquibase.structure.core.Table;

import java.util.Set;

public class DatabaseWrapper {

    private final Database database;

    public DatabaseWrapper(Database database) {
        this.database = database;
    }

    public Set<Table> getTables() {
        return getDatabaseSnapshot(database).get(Table.class);
    }

    public Table getTable(String tableName) {
        for (Table table : getDatabaseSnapshot(database).get(Table.class)) {
            if (table.getName().equalsIgnoreCase(tableName)) {
                return table;
            }
        }
        throw new IllegalArgumentException(String.format("Table does not exist: %s", tableName));
    }

    public boolean getTableExists(String tableName) {
        for (Table table : getDatabaseSnapshot(database).get(Table.class)) {
            if (table.getName().equalsIgnoreCase(tableName)) {
                return true;
            }
        }
        return false;
    }

    private static DatabaseSnapshot getDatabaseSnapshot(Database database) {
        try {
            return SnapshotGeneratorFactory.getInstance()
                    .createSnapshot(database.getDefaultSchema(), database,  new SnapshotControl(database));
        } catch (DatabaseException | InvalidExampleException e) {
            throw new RuntimeException(e);
        }
    }

    public LiquibaseDataType getLiquibaseDataType(DataType type) {
        return DataTypeFactory.getInstance().from(type, database);
    }

    public LiquibaseDataType getLiquibaseDataType(String description) {
        return DataTypeFactory.getInstance().fromDescription(description, database);
    }
}
