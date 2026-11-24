// [P3C A20 豁免] A20/A30/A31: SQL/日志字符串字面量场景，豁免。
package com.chua.starter.datasource.runtime.sharding;

import com.chua.starter.datasource.runtime.model.DsShardingRule;
import com.chua.starter.datasource.runtime.model.ShardingDbMember;
import com.chua.starter.datasource.runtime.support.MemberDataSourcePool;
import com.chua.starter.datasource.support.DataSourceContextSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 分片物理表管理服务。
 *
 * <p>边界（用户定版）：只开放「按规则幂等补建分片表（CREATE TABLE IF NOT EXISTS）」
 * 与「追加列（ALTER TABLE ADD COLUMN，存在即跳过）」两类受限 DDL，不提供任意 DDL 通道。
 * 所有执行前均可先走预览接口拿到将要执行的 SQL 清单。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
public class ShardingPhysicalTableService {

    /**
     * CREATE TABLE 语句中逻辑表名的匹配（含可选 IF NOT EXISTS / 反引号 / schema 前缀）
     */
    private static final Pattern CREATE_TABLE_PATTERN = Pattern.compile(
            "(?i)^(\\s*CREATE\\s+TABLE\\s+)(?:IF\\s+NOT\\s+EXISTS\\s+)?(?:`?[a-zA-Z0-9_]+`?\\.)?`?([a-zA-Z0-9_]+)`?");

    private final ShardingRuleManager ruleManager;

    public ShardingPhysicalTableService(ShardingRuleManager ruleManager) {
        this.ruleManager = ruleManager;
    }

    /**
     * 按规则推导期望的物理表名清单。
     * MOD：前缀_0 .. 前缀_(n-1)；INTERVAL：前缀_按时间粒度的分区后缀。
     */
    public List<String> expectedPhysicalTables(DsShardingRule rule) {
        List<String> tables = new ArrayList<>();
        String prefix = rule.getLogicTable();
        boolean interval = DsShardingRule.ALGO_INTERVAL.equalsIgnoreCase(rule.getAlgorithm())
                || (rule.getTimeUnit() != null && !rule.getTimeUnit().isBlank());
        if (interval) {
            String pattern = intervalPattern(rule.getTimeUnit());
            SimpleDateFormat format = new SimpleDateFormat(pattern);
            try {
                Date lower = new SimpleDateFormat("yyyy-MM-dd").parse(
                        rule.getTimeLower() == null ? "2000-01-01" : rule.getTimeLower());
                Date upper = new SimpleDateFormat("yyyy-MM-dd").parse(
                        rule.getTimeUpper() == null ? "2099-12-31" : rule.getTimeUpper());
                // 预览上限：最多看到当前时间 + 1 年，避免为遥远的未来期生成海量 DDL
                Calendar horizon = Calendar.getInstance();
                horizon.add(Calendar.YEAR, 1);
                Date end = upper.before(horizon.getTime()) ? upper : horizon.getTime();
                Calendar cursor = Calendar.getInstance();
                cursor.setTime(lower);
                while (!format.format(cursor.getTime()).equals(format.format(end)) && tables.size() < 1024) {
                    tables.add(prefix + "_" + format.format(cursor.getTime()));
                    advance(cursor, rule.getTimeUnit());
                }
                tables.add(prefix + "_" + format.format(end));
            } catch (Exception e) {
                throw new IllegalArgumentException("时间范围解析失败: " + e.getMessage(), e);
            }
        } else {
            int shardCount = rule.getShardCount() == null || rule.getShardCount() <= 0 ? 16 : rule.getShardCount();
            for (int i = 0; i < shardCount; i++) {
                tables.add(prefix + "_" + i);
            }
        }
        return tables;
    }

    private void advance(Calendar cursor, String timeUnit) {
        String unit = timeUnit == null ? "MONTH" : timeUnit.toUpperCase();
        switch (unit) {
            case "YEAR" -> cursor.add(Calendar.YEAR, 1);
            case "QUARTER" -> cursor.add(Calendar.MONTH, 3);
            case "WEEK" -> cursor.add(Calendar.WEEK_OF_YEAR, 1);
            case "DAY" -> cursor.add(Calendar.DAY_OF_MONTH, 1);
            case "HOUR" -> cursor.add(Calendar.HOUR_OF_DAY, 1);
            default -> cursor.add(Calendar.MONTH, 1);
        }
    }

    private String intervalPattern(String timeUnit) {
        if (timeUnit == null) {
            return "yyyyMM";
        }
        return switch (timeUnit.toUpperCase()) {
            case "YEAR" -> "yyyy";
            case "QUARTER" -> "yyyy'Q'Q";
            case "WEEK" -> "yyyyww";
            case "DAY" -> "yyyyMMdd";
            case "HOUR" -> "yyyyMMddHH";
            default -> "yyyyMM";
        };
    }

    /**
     * 各分库中与逻辑表前缀匹配的现有物理表。
     *
     * @return db 名 -> 物理表清单
     */
    public Map<String, List<String>> listPhysicalTables(DsShardingRule rule) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (DbTarget target : resolveTargets(rule)) {
            List<String> tables = new ArrayList<>();
            try (Connection conn = target.open()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()");
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString(1);
                        if (name != null && name.toLowerCase().startsWith(rule.getLogicTable().toLowerCase())) {
                            tables.add(name);
                        }
                    }
                }
            } catch (Exception e) {
                result.put(target.name + " (查询失败: " + e.getMessage() + ")", tables);
                continue;
            }
            result.put(target.name, tables);
        }
        return result;
    }

    /**
     * 生成补建 DDL 预览（不执行）。
     *
     * @param sampleDdl 样板建表 SQL，其中表名须为逻辑表名；服务将其替换为各物理表名并补 IF NOT EXISTS
     */
    public List<Map<String, Object>> previewCreate(DsShardingRule rule, String sampleDdl) {
        String template = normalizeCreate(sampleDdl, rule.getLogicTable());
        List<Map<String, Object>> plan = new ArrayList<>();
        for (DbTarget target : resolveTargets(rule)) {
            for (String physical : expectedPhysicalTables(rule)) {
                plan.add(planItem(target.name, physical, template.replaceFirst(Pattern.quote(firstCreateTable(template)),
                        Matcher.quoteReplacement(physical)), "CREATE"));
            }
        }
        // 上面的 replaceFirst 基于模板首表名替换；直接给出最终 SQL 更直观
        plan.clear();
        for (DbTarget target : resolveTargets(rule)) {
            for (String physical : expectedPhysicalTables(rule)) {
                plan.add(planItem(target.name, physical, rewriteCreateTable(template, physical), "CREATE"));
            }
        }
        return plan;
    }

    /**
     * 执行补建（幂等 CREATE TABLE IF NOT EXISTS）。
     */
    public List<Map<String, Object>> applyCreate(DsShardingRule rule, String sampleDdl) {
        List<Map<String, Object>> plan = previewCreate(rule, sampleDdl);
        return execute(plan);
    }

    /**
     * 生成追加列预览（不执行；已存在同名列的库/表会被标记 skipped）。
     *
     * @param columnDdl 列定义片段，如 remark VARCHAR(255) DEFAULT NULL
     */
    public List<Map<String, Object>> previewAddColumn(DsShardingRule rule, String columnDdl) {
        String column = firstIdentifier(columnDdl);
        if (column == null) {
            throw new IllegalArgumentException("无法从列定义中解析列名");
        }
        List<Map<String, Object>> plan = new ArrayList<>();
        for (DbTarget target : resolveTargets(rule)) {
            for (String physical : existingOrExpectedTables(target, rule)) {
                boolean exists = columnExists(target, physical, column);
                String sql = "ALTER TABLE " + physical + " ADD COLUMN " + columnDdl.trim();
                Map<String, Object> item = planItem(target.name, physical, sql, "ADD_COLUMN");
                item.put("skipped", exists);
                item.put("skipReason", exists ? "列已存在" : null);
                plan.add(item);
            }
        }
        return plan;
    }

    /**
     * 执行追加列（已存在同名列的表自动跳过）。
     *
     * <p>跳过项不进入 {@link #execute}，按原计划顺序并入结果并保留
     * {@code skipped}/{@code skipReason} 标记，前端据此区分「执行成功」与
     * 「列已存在自动跳过」；全部跳过时也返回非空清单，避免被误判为无事发生。</p>
     */
    public List<Map<String, Object>> applyAddColumn(DsShardingRule rule, String columnDdl) {
        List<Map<String, Object>> plan = previewAddColumn(rule, columnDdl);
        List<Map<String, Object>> toRun = new ArrayList<>(plan.size());
        for (Map<String, Object> item : plan) {
            if (!Boolean.TRUE.equals(item.get("skipped"))) {
                toRun.add(item);
            }
        }
        Iterator<Map<String, Object>> executed = execute(toRun).iterator();
        List<Map<String, Object>> merged = new ArrayList<>(plan.size());
        for (Map<String, Object> item : plan) {
            merged.add(Boolean.TRUE.equals(item.get("skipped")) ? item : executed.next());
        }
        return merged;
    }

    /**
     * 逐条执行计划中的 SQL。
     */
    private List<Map<String, Object>> execute(List<Map<String, Object>> plan) {
        Map<String, List<String>> byDb = new LinkedHashMap<>();
        for (Map<String, Object> item : plan) {
            byDb.computeIfAbsent((String) item.get("db"), k -> new ArrayList<>()).add((String) item.get("sql"));
        }
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> item : plan) {
            Map<String, Object> result = new LinkedHashMap<>(item);
            result.remove("skipped");
            result.remove("skipReason");
            String db = (String) item.get("db");
            try (Connection conn = openByName(db)) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute((String) item.get("sql"));
                }
                result.put("success", true);
                result.put("message", "OK");
            } catch (Exception e) {
                result.put("success", false);
                result.put("message", e.getMessage());
            }
            results.add(result);
        }
        log.info("[ds-runtime] 物理 DDL 执行完成，共 {} 条", results.size());
        return results;
    }

    private Connection openByName(String db) throws Exception {
        for (DbTarget target : currentTargets()) {
            if (target.name.equals(db)) {
                return target.open();
            }
        }
        throw new IllegalStateException("未找到分库: " + db);
    }

    private List<DbTarget> currentTargets() {
        List<DsShardingRule> rules = ruleManager.activeRules();
        List<DbTarget> targets = new ArrayList<>();
        for (DsShardingRule rule : rules) {
            targets.addAll(resolveTargets(rule));
        }
        return targets;
    }

    /**
     * 已存在的物理表；一个都没有时回退为按规则推导的期望清单（首次补建场景）。
     */
    private List<String> existingOrExpectedTables(DbTarget target, DsShardingRule rule) {
        List<String> existing = new ArrayList<>();
        try (Connection conn = target.open();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString(1);
                if (name != null && name.toLowerCase().startsWith(rule.getLogicTable().toLowerCase())) {
                    existing.add(name);
                }
            }
        } catch (Exception ignored) {
            // 查询失败时按期望清单处理
        }
        return existing.isEmpty() ? expectedPhysicalTables(rule) : existing;
    }

    private boolean columnExists(DbTarget target, String table, String column) {
        try (Connection conn = target.open();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.columns "
                             + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?")) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            log.warn("[ds-runtime] 列存在性检查失败（按不存在处理）: {}.{}: {}", table, column, e.getMessage());
            return false;
        }
    }

    /**
     * 解析规则的分库目标（url 型直连；引用型从注册表取连接）。
     */
    private List<DbTarget> resolveTargets(DsShardingRule rule) {
        List<DbTarget> targets = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (ShardingDbMember m : rule.getDbMemberList()) {
            if (m.getName() == null || !seen.add(m.getName())) {
                continue;
            }
            if (m.getUrl() != null && !m.getUrl().isBlank()) {
                targets.add(new DbTarget(m.getName(), m.getUrl(), m.getUsername(), m.getPassword(),
                        m.getDriverClassName()));
            } else {
                DataSource ds = DataSourceContextSupport.getDatasource(m.getName());
                if (ds != null) {
                    targets.add(new DbTarget(m.getName(), ds));
                }
            }
        }
        if (targets.isEmpty()) {
            // 规则未配成员时回退到当前生效分片数据源的物理源
            for (DataSource ds : ruleManager.currentPhysical()) {
                targets.add(new DbTarget("ds_" + (targets.size()), ds));
            }
        }
        return targets;
    }

    private Map<String, Object> planItem(String db, String table, String sql, String type) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("db", db);
        item.put("table", table);
        item.put("sql", sql);
        item.put("type", type);
        return item;
    }

    /**
     * 规范化样板建表 SQL：去掉既有 IF NOT EXISTS 差异，统一为可替换表名的模板。
     */
    private String normalizeCreate(String sampleDdl, String logicTable) {
        if (sampleDdl == null || sampleDdl.isBlank()) {
            throw new IllegalArgumentException("样板建表 SQL 不能为空");
        }
        String trimmed = sampleDdl.trim();
        if (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        Matcher matcher = CREATE_TABLE_PATTERN.matcher(trimmed);
        if (!matcher.find()) {
            throw new IllegalArgumentException("样板 SQL 不是 CREATE TABLE 语句");
        }
        if (!matcher.group(2).equalsIgnoreCase(logicTable)) {
            throw new IllegalArgumentException("样板 SQL 表名 " + matcher.group(2)
                    + " 与规则逻辑表 " + logicTable + " 不一致");
        }
        return trimmed;
    }

    private String firstCreateTable(String template) {
        Matcher matcher = CREATE_TABLE_PATTERN.matcher(template);
        return matcher.find() ? matcher.group(2) : "";
    }

    /**
     * 将模板中的逻辑表名替换为目标物理表名，并统一补上 IF NOT EXISTS。
     */
    private String rewriteCreateTable(String template, String physicalTable) {
        Matcher matcher = CREATE_TABLE_PATTERN.matcher(template);
        if (!matcher.find()) {
            return template;
        }
        String head = "CREATE TABLE IF NOT EXISTS " + physicalTable + " ";
        return head + template.substring(matcher.end()).trim();
    }

    private String firstIdentifier(String ddl) {
        if (ddl == null) {
            return null;
        }
        String trimmed = ddl.trim();
        if (trimmed.startsWith("`")) {
            int end = trimmed.indexOf('`', 1);
            return end > 0 ? trimmed.substring(1, end) : null;
        }
        int space = trimmed.indexOf(' ');
        String token = space > 0 ? trimmed.substring(0, space) : trimmed;
        token = token.replace("(", "");
        return token.isBlank() ? null : token;
    }

    /**
     * 按首个分库中已存在的物理表结构生成建表模板 DDL（只读元数据，不执行任何 DDL）。
     *
     * <p>补建模板辅助生成：取规则首个分库目标，读取该库中与逻辑表名一致（或按前缀
     * 匹配到的第一个）物理表的列、主键与表选项，生成表名为逻辑表名的 CREATE TABLE
     * 模板，供用户粘贴为样板 DDL 后走补建 / 追加列流程。</p>
     */
    public Map<String, Object> generateTemplate(DsShardingRule rule) {
        List<DbTarget> targets = resolveTargets(rule);
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("规则没有可用的分库目标，请先配置分库成员或刷新规则");
        }
        DbTarget target = targets.get(0);
        String physical = firstExistingTable(target, rule.getLogicTable());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("db", target.name);
        result.put("sourceTable", physical);
        result.put("logicTable", rule.getLogicTable());
        try (Connection conn = target.open()) {
            List<Map<String, Object>> columns = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT column_name, column_type, is_nullable, column_default, extra, column_key, column_comment "
                            + "FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? "
                            + "ORDER BY ordinal_position")) {
                ps.setString(1, physical);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> col = new LinkedHashMap<>();
                        col.put("name", rs.getString("column_name"));
                        col.put("type", rs.getString("column_type"));
                        col.put("nullable", "YES".equalsIgnoreCase(rs.getString("is_nullable")));
                        col.put("defaultValue", rs.getString("column_default"));
                        col.put("extra", rs.getString("extra"));
                        col.put("primaryKey", "PRI".equalsIgnoreCase(rs.getString("column_key")));
                        col.put("comment", rs.getString("column_comment"));
                        columns.add(col);
                    }
                }
            }
            if (columns.isEmpty()) {
                throw new IllegalArgumentException("分库 " + target.name + " 中未找到物理表 " + physical
                        + " 的结构信息，无法生成模板；请先补建物理表或手填样板 DDL");
            }
            String engine = "InnoDB";
            String tableComment = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT engine, table_comment FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE() AND table_name = ?")) {
                ps.setString(1, physical);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String tableEngine = rs.getString("engine");
                        if (tableEngine != null && !tableEngine.isBlank()) {
                            engine = tableEngine;
                        }
                        tableComment = rs.getString("table_comment");
                    }
                }
            } catch (Exception ignored) {
                // 表选项读取失败不影响列模板生成
            }
            result.put("ddl", buildCreateTemplate(rule.getLogicTable(), columns, engine, tableComment));
            result.put("columnCount", columns.size());
            result.put("primaryKey", columns.stream()
                    .filter(c -> Boolean.TRUE.equals(c.get("primaryKey")))
                    .map(c -> String.valueOf(c.get("name")))
                    .collect(Collectors.toList()));
            result.put("engine", engine);
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("读取分库 " + target.name + " 元数据失败: " + e.getMessage(), e);
        }
    }

    /**
     * 首个分库中与逻辑表名一致（优先）或按前缀匹配的现有物理表；没有则回退逻辑表名本身。
     */
    private String firstExistingTable(DbTarget target, String logicTable) {
        try (Connection conn = target.open();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() ORDER BY table_name");
             ResultSet rs = ps.executeQuery()) {
            String fallback = null;
            while (rs.next()) {
                String name = rs.getString(1);
                if (name == null || name.isBlank()) {
                    continue;
                }
                if (name.equalsIgnoreCase(logicTable)) {
                    return name;
                }
                if (fallback == null && name.toLowerCase().startsWith(logicTable.toLowerCase())) {
                    fallback = name;
                }
            }
            return fallback != null ? fallback : logicTable;
        } catch (Exception e) {
            log.warn("[ds-runtime] 模板源表探测失败（按逻辑表名处理）: {}: {}", target.name, e.getMessage());
            return logicTable;
        }
    }

    /**
     * 按列元数据拼装 CREATE TABLE 模板（表名为逻辑表名，统一带 IF NOT EXISTS）。
     */
    private String buildCreateTemplate(String logicTable, List<Map<String, Object>> columns,
                                       String engine, String tableComment) {
        StringBuilder ddl = new StringBuilder("CREATE TABLE IF NOT EXISTS `").append(logicTable).append("` (\n");
        List<String> primaryKeys = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            Map<String, Object> col = columns.get(i);
            String name = String.valueOf(col.get("name"));
            String type = String.valueOf(col.get("type"));
            ddl.append("  `").append(name).append("` ").append(type);
            if (!Boolean.TRUE.equals(col.get("nullable"))) {
                ddl.append(" NOT NULL");
            }
            String extra = col.get("extra") == null ? "" : String.valueOf(col.get("extra")).toLowerCase();
            Object defaultValue = col.get("defaultValue");
            if (extra.contains("auto_increment")) {
                ddl.append(" AUTO_INCREMENT");
            } else if (defaultValue != null) {
                String text = String.valueOf(defaultValue);
                if (isNumericType(type) || "CURRENT_TIMESTAMP".equalsIgnoreCase(text)
                        || "NULL".equalsIgnoreCase(text)) {
                    ddl.append(" DEFAULT ").append(text);
                } else {
                    ddl.append(" DEFAULT '").append(text.replace("'", "''")).append("'");
                }
            } else if (Boolean.TRUE.equals(col.get("nullable"))) {
                ddl.append(" DEFAULT NULL");
            }
            String comment = col.get("comment") == null ? "" : String.valueOf(col.get("comment"));
            if (!comment.isBlank()) {
                ddl.append(" COMMENT '").append(comment.replace("'", "''")).append("'");
            }
            if (Boolean.TRUE.equals(col.get("primaryKey"))) {
                primaryKeys.add(name);
            }
            if (i < columns.size() - 1 || !primaryKeys.isEmpty()) {
                ddl.append(",");
            }
            ddl.append("\n");
        }
        if (!primaryKeys.isEmpty()) {
            ddl.append("  PRIMARY KEY (").append(primaryKeys.stream()
                    .map(k -> "`" + k + "`")
                    .collect(Collectors.joining(", "))).append(")\n");
        }
        ddl.append(") ENGINE=").append(engine).append(" DEFAULT CHARSET=utf8mb4");
        if (tableComment != null && !tableComment.isBlank()) {
            ddl.append(" COMMENT='").append(tableComment.replace("'", "''")).append("'");
        }
        ddl.append(";");
        return ddl.toString();
    }

    private boolean isNumericType(String type) {
        String lower = type == null ? "" : type.toLowerCase();
        return lower.contains("int") || lower.contains("decimal") || lower.contains("numeric")
                || lower.contains("float") || lower.contains("double") || lower.contains("bit");
    }

    /**
     * 分库目标：既支持 url 直连（DDL 低频，不建池），也支持已注册数据源。
     */
    private static final class DbTarget {

        private final String name;
        private final String url;
        private final String username;
        private final String password;
        private final String driverClassName;
        private final DataSource referenced;

        DbTarget(String name, String url, String username, String password, String driverClassName) {
            this.name = name;
            this.url = url;
            this.username = username;
            this.password = password;
            this.driverClassName = driverClassName;
            this.referenced = null;
        }

        DbTarget(String name, DataSource ds) {
            this.name = name;
            this.url = null;
            this.username = null;
            this.password = null;
            this.driverClassName = null;
            this.referenced = ds;
        }

        Connection open() throws Exception {
            if (referenced != null) {
                return referenced.getConnection();
            }
            return MemberDataSourcePool.openDirect(url, username, password, driverClassName);
        }
    }
}
