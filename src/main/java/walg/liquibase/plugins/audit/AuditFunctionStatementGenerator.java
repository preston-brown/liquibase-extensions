package walg.liquibase.plugins.audit;

import liquibase.statement.SqlStatement;
import liquibase.statement.core.RawCallStatement;
import liquibase.structure.core.Column;
import liquibase.structure.core.Table;

import java.util.List;
import java.util.stream.Stream;

public class AuditFunctionStatementGenerator {

    private static final List<String> STANDARD_AUDIT_COLUMN_NAMES = List.of("audit_timestamp", "audit_action", "audit_user");
    private static final List<String> STANDARD_AUDIT_COLUMN_VALUES = List.of("LOCALTIMESTAMP", "TG_OP", "USER");
    private static final String CREATE_AUDIT_FUNCTION = """
        create or replace function %s returns trigger
        language 'plpgsql'
        volatile
        as $body$
        begin
            -- on insert
            %s
            -- on update
            %s
            -- on delete
            %s
            return null;
        end
        $body$;
        """;

    private static final String DROP_AUDIT_FUNCTION = "drop function if exists %s";

    public static List<SqlStatement> generateRollbackStatements(Table table) {
        String auditFunctionName = Names.getAuditFunctionName(table, true);
        String sql = String.format(DROP_AUDIT_FUNCTION, auditFunctionName);
        return List.of(new RawCallStatement(sql));
    }

    public static List<SqlStatement> generateCreateStatements(Table table) {
        String auditTableName = Names.getAuditTableName(table, true);
        String auditFunctionName = Names.getAuditFunctionName(table, true);
        String insert = getIfStatementForInsertAction(auditTableName, table);
        String update = getIfStatementForUpdateAction(auditTableName, table);
        String delete = getIfStatementForDeleteAction(auditTableName, table);
        String sql = String.format(CREATE_AUDIT_FUNCTION, auditFunctionName, insert, update, delete);
        return List.of(new RawCallStatement(sql));
    }

    private static String getIfStatementForInsertAction(String auditTableName, Table baseTable) {
        String insertStatement = getAuditTableInsertStatement(auditTableName, baseTable, "NEW");
        return String.format("if (tg_op = 'INSERT') then %s end if;", insertStatement);
    }

    private static String getIfStatementForUpdateAction(String auditTableName, Table baseTable) {
        String insertStatement = getAuditTableInsertStatement(auditTableName, baseTable, "NEW");
        return String.format("if (tg_op = 'UPDATE' and new is distinct from old) then %s end if;", insertStatement);
    }

    private static String getIfStatementForDeleteAction(String auditTableName, Table baseTable) {
        String insertStatement = getAuditTableInsertStatement(auditTableName, baseTable, "OLD");
        return String.format("if (tg_op = 'DELETE') then %s end if;", insertStatement);
    }

    private static String getAuditTableInsertStatement(String auditTableName, Table baseTable, String valuePrefix) {
        List<String> baseTableColumnNames = baseTable.getColumns().stream().map(Column::getName).toList();
        List<String> baseTableColumNamesWithPrefix = baseTableColumnNames.stream().map(a -> valuePrefix + "." + a).toList();
        List<String> insertColumnNames = Stream.concat(STANDARD_AUDIT_COLUMN_NAMES.stream(), baseTableColumnNames.stream()).toList();
        List<String> insertColumnValues = Stream.concat(STANDARD_AUDIT_COLUMN_VALUES.stream(), baseTableColumNamesWithPrefix.stream()).toList();
        return String.format("insert into %s (%s) values (%s);", auditTableName, String.join(", ", insertColumnNames), String.join(", ", insertColumnValues));
    }
}
