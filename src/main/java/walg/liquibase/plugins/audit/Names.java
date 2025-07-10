package walg.liquibase.plugins.audit;

import liquibase.structure.core.Table;

public class Names {

    private static final String CATALOG_NAME = "jukebox";
    private static final String SCHEMA_NAME = "public";

    public static String getAuditFunctionName(Table table, boolean includeSchema) {
        return getAuditTableName(table, includeSchema) + "()";
    }

    public static String getAuditTableName(Table table, boolean includeSchema) {
        return getBaseTableName(table, includeSchema) + "_audit";
    }

    public static String getAuditTriggerName() {
        return "audit";
    }

    public static String getBaseTableName(Table table, boolean includeSchema) {
        return (includeSchema ? SCHEMA_NAME + "." : "") + table.getName();
    }

    public static String getCatalog() {
        return CATALOG_NAME;
    }

    public static String getSchema() {
        return SCHEMA_NAME;
    }

}
