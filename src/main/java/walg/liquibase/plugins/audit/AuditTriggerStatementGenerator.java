package walg.liquibase.plugins.audit;

import liquibase.statement.SqlStatement;
import liquibase.statement.core.RawCallStatement;
import liquibase.structure.core.Table;

import java.util.ArrayList;
import java.util.List;

public class AuditTriggerStatementGenerator {

    private static final String CREATE_TRIGGER = """
            create or replace trigger %s
            after delete or insert or update on %s
            for each row execute function %s;
            """;

    private static final String DROP_TRIGGER = """
            drop trigger if exists %s on %s
            """;

    public static List<SqlStatement> generateRollbackStatements(Table table) {
        String triggerName = Names.getAuditTriggerName();
        String baseTableName = Names.getBaseTableName(table, true);
        String sql = String.format(DROP_TRIGGER, triggerName, baseTableName);
        return List.of(new RawCallStatement(sql));
    }

    public static List<SqlStatement> generateCreateStatements(Table table) {
        List<SqlStatement> result = new ArrayList<>();
        String triggerName = Names.getAuditTriggerName();
        String baseTableName = Names.getBaseTableName(table, true);
        String auditFunctionName = Names.getAuditFunctionName(table, true);
        String sql = String.format(CREATE_TRIGGER, triggerName, baseTableName, auditFunctionName);
        result.add(new RawCallStatement(sql));
        return result;
    }
}
