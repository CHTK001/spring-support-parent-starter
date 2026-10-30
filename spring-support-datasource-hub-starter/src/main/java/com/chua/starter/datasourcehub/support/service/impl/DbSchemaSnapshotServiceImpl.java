package com.chua.starter.datasourcehub.support.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.datasource.dialect.SqlName;
import com.chua.starter.datasourcehub.support.dbconsole.DbSchemaSnapshotRequest;
import com.chua.starter.datasourcehub.support.dbconsole.DbSchemaSnapshotVO;
import com.chua.starter.datasourcehub.support.dbconsole.DbSnapshotDetailVO;
import com.chua.starter.datasourcehub.support.dbconsole.DbSchemaRollbackResultVO;
import com.chua.starter.datasourcehub.support.dbconsole.DbTableSpec;
import com.chua.starter.datasourcehub.support.dbconsole.SqlScriptSplitter;
import com.chua.starter.datasourcehub.support.entity.MsSchemaSnapshot;
import com.chua.starter.datasourcehub.support.mapper.MsSchemaSnapshotMapper;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import com.chua.starter.datasourcehub.support.service.DbSchemaSnapshotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 表结构版本控制（DDL 快照 + 回滚）实现。
 *
 * <p>拍快照：从库中取出表定义（SHOW CREATE TABLE / 失败 fallback 到 JDBC 元数据拼装）→
 * 落盘 <code>ms_schema_snapshot</code>；回滚：删当前表 → 原样执行 DDL → 重建索引/外键
 * （SHOW_CREATE 来源的 DDL 已自包含约束，无需重建）。只影响被回滚的表，不碰同库的其他表。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DbSchemaSnapshotServiceImpl implements DbSchemaSnapshotService {

    /**
     * 快照名称的时间格式（ snapshots 盖好章的那一秒）
     */
    private static final DateTimeFormatter SNAPSHOT_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 快照正文 JSON 的序列化器
     */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final DatasourceService datasourceService;
    private final MsSchemaSnapshotMapper mapper;

    @Override
    public ReturnResult<Long> takeSnapshot(DbSchemaSnapshotRequest request) {
        if (request == null || request.tableName() == null || request.tableName().isBlank()) {
            return ReturnResult.error("表名不能为空");
        }
        if (request.datasourceId() == null) {
            return ReturnResult.error("缺少数据源 ID");
        }
        return callDb("结构拍快照", () -> {
            String table = SqlName.checkSimple(request.tableName(), "表名");
            // userDb 是拍快照时刻的目标库（回滚时表去哪 / 去那的确切地址都靠它）
            String userDb = request.database();
            try (Connection conn = datasourceService.connection(request.datasourceId());
                 SchemaScope scope = useSchema(conn, userDb)) {
                String db = (userDb == null || userDb.isBlank()) ? scope.database() : userDb;
                DbTableSpec spec = buildTableSpec(conn, db, table);
                String snapshot = JSON.writeValueAsString(spec);
                String name = request.name();
                if (name == null || name.isBlank()) {
                    name = "保存于 " + LocalDateTime.now().format(SNAPSHOT_TIME_FMT);
                }
                MsSchemaSnapshot row = new MsSchemaSnapshot();
                row.setDatasourceId(request.datasourceId());
                row.setDatabase(db);
                row.setTableName(table);
                row.setName(name);
                row.setCreator(request.creator() == null ? "SYSTEM" : request.creator());
                row.setCreateTime(LocalDateTime.now());
                row.setSnapshot(snapshot);
                mapper.insert(row);
                return row.getId();
            } catch (Exception e) {
                throw new IllegalStateException("拍快照失败: " + describeSqlError(e.getMessage()), e);
            }
        });
    }

    @Override
    public ReturnResult<Page<DbSchemaSnapshotVO>> listSnapshots(DbSchemaSnapshotRequest.ListRequest request) {
        if (request == null || request.datasourceId() == null) {
            return ReturnResult.error("缺少数据源 ID");
        }
        long current = request.page() == null || request.page() < 1 ? 1L : request.page();
        long perPage = request.size() == null || request.size() < 1 ? 20L : Math.min(request.size(), 200);

        LambdaQueryWrapper<MsSchemaSnapshot> wrapper = new LambdaQueryWrapper<MsSchemaSnapshot>()
                .eq(MsSchemaSnapshot::getDatasourceId, request.datasourceId())
                .eq(request.tableName() != null && !request.tableName().isBlank(),
                        MsSchemaSnapshot::getTableName, request.tableName())
                .eq(request.database() != null && !request.database().isBlank(),
                        MsSchemaSnapshot::getDatabase, request.database())
                .like(request.name() != null && !request.name().isBlank(),
                        MsSchemaSnapshot::getName, request.name())
                // 最新在前：用户要的是时间线，不是机柜
                .orderByDesc(MsSchemaSnapshot::getCreateTime)
                .orderByDesc(MsSchemaSnapshot::getId);

        Page<MsSchemaSnapshot> rows = mapper.selectPage(new Page<>(current, perPage), wrapper);
        List<DbSchemaSnapshotVO> items = new ArrayList<>();
        for (MsSchemaSnapshot row : rows.getRecords()) {
            DbTableSpec spec = parseSpec(row.getSnapshot());
            items.add(DbSchemaSnapshotVO.of(row, spec == null ? null : spec.ddl(),
                    spec == null ? 0L : (long) spec.columns().size(),
                    spec == null ? 0L : (long) spec.indexes().size(),
                    spec == null ? 0L : (long) spec.foreignKeys().size()));
        }
        Page<DbSchemaSnapshotVO> page = new Page<>(rows.getCurrent(), rows.getSize(), rows.getTotal());
        page.setRecords(items);
        return ReturnResult.success(page);
    }

    @Override
    public ReturnResult<DbSnapshotDetailVO> getSnapshotDetail(Long snapshotId) {
        if (snapshotId == null) {
            return ReturnResult.error("缺少快照 ID");
        }
        try {
            MsSchemaSnapshot row = mapper.selectById(snapshotId);
            if (row == null) {
                return ReturnResult.error("快照不存在");
            }
            DbTableSpec spec = parseSpec(row.getSnapshot());
            if (spec == null) {
                return ReturnResult.error("快照正文已损坏");
            }
            Map<String, Object> options = spec.options() == null ? new LinkedHashMap<String, Object>() : spec.options();
            List<Map<String, Object>> indexes = new ArrayList<>();
            if (spec.indexes() != null) {
                for (String idx : spec.indexes()) {
                    Map<String, Object> one = new LinkedHashMap<>();
                    one.put("ddl", idx);
                    indexes.add(one);
                }
            }
            List<Map<String, Object>> foreignKeys = new ArrayList<>();
            if (spec.foreignKeys() != null) {
                for (String fk : spec.foreignKeys()) {
                    Map<String, Object> one = new LinkedHashMap<>();
                    one.put("ddl", fk);
                    foreignKeys.add(one);
                }
            }
            return ReturnResult.success(new DbSnapshotDetailVO(
                    row.getId(), row.getDatabase(), row.getTableName(), row.getName(), row.getCreator(),
                    row.getCreateTime(), spec.ddl(), spec.columns(), indexes, foreignKeys, options));
        } catch (Exception e) {
            return ReturnResult.error("获取快照详情失败: " + describeSqlError(e.getMessage()));
        }
    }

    @Override
    public ReturnResult<Boolean> deleteSnapshot(Long snapshotId) {
        if (snapshotId == null) {
            return ReturnResult.error("缺少快照 ID");
        }
        try {
            MsSchemaSnapshot row = mapper.selectById(snapshotId);
            if (row == null) {
                return ReturnResult.error("快照不存在");
            }
            mapper.deleteById(snapshotId);
            return ReturnResult.success(true);
        } catch (Exception e) {
            return ReturnResult.error("删除快照失败: " + describeSqlError(e.getMessage()));
        }
    }

    @Override
    public ReturnResult<DbSchemaRollbackResultVO> rollbackSnapshot(Long snapshotId) {
        if (snapshotId == null) {
            return ReturnResult.error("缺少快照 ID");
        }
        return callDb("结构回滚", () -> {
            MsSchemaSnapshot row = mapper.selectById(snapshotId);
            if (row == null) {
                throw new IllegalStateException("快照不存在");
            }
            DbTableSpec spec = parseSpec(row.getSnapshot());
            if (spec == null) {
                throw new IllegalStateException("快照正文已损坏");
            }
            List<String> errors = new ArrayList<>();
            int total = 0, ok = 0;
            long start = System.currentTimeMillis();
            String database = row.getDatabase();
            String table = spec.table();
            try (Connection conn = datasourceService.connection(row.getDatasourceId());
                 SchemaScope scope = useSchema(conn, database)) {
                boolean isMysql = isMysql(conn);
                // 记子句，最终按完整文本切语句再逐条执行
                List<String> script = new ArrayList<>();
                // 关外键开关（把当前表从被引用关系中摘出来，"删表"才不被拦截）
                if (isMysql) {
                    script.add("SET FOREIGN_KEY_CHECKS = 0;");
                }
                // 先清掉当前表：删表再建，把用户的修改干干净净回退到快照时代
                script.add("DROP TABLE IF EXISTS " + SqlName.quoteMysql(table, "表名"));
                if (isMysql) {
                    script.add("SET FOREIGN_KEY_CHECKS = 1;");
                }
                // DDL 正文（SHOW CREATE TABLE 原文 / 元数据拼装）
                script.add(spec.ddl() + ";");
                // METADATA 来源的 DDL 不自包含索引/外键，补上重建语句
                if (!spec.ddlContainsConstraints()) {
                    if (spec.indexes() != null) {
                        script.addAll(spec.indexes());
                    }
                    if (spec.foreignKeys() != null) {
                        script.addAll(spec.foreignKeys());
                    }
                }
                for (String sql : SqlScriptSplitter.split(String.join("\n", script))) {
                    total++;
                    try (Statement st = conn.createStatement()) {
                        st.execute(sql);
                        ok++;
                    } catch (SQLException e) {
                        errors.add(statementLabel(sql) + " -> " + describeSqlError(e.getMessage()));
                    }
                }
            } catch (Exception e) {
                // 连接已关（try-with-resources），把错误递到 callDb
                throw new IllegalStateException("回滚失败: " + describeSqlError(e.getMessage()), e);
            }
            return new DbSchemaRollbackResultVO(snapshotId, database, table, total, ok, total - ok,
                    System.currentTimeMillis() - start, errors);
        });
    }

    // ------------------------------- 私有工具 -------------------------------

    /**
     * 仿 DbConsoleController#callDb：把数据库操作包装成 ReturnResult
     */
    private <T> ReturnResult<T> callDb(String action, ThrowingSupplier<T> supplier) {
        try {
            return ReturnResult.success(supplier.get());
        } catch (RuntimeException e) {
            log.warn("[schema-snapshot] {} 失败: {}", action, e.getMessage());
            return ReturnResult.error("执行失败: " + describeSqlError(e.getMessage()));
        } catch (Exception e) {
            log.warn("[schema-snapshot] {} 失败: {}", action, e.getMessage());
            return ReturnResult.error("执行失败: " + describeSqlError(e.getMessage()));
        }
    }

    /**
     * 仿 DbConsoleController#useSchema：不改变连接池，只切当前 catalog/schema（用完还原）
     */
    private SchemaScope useSchema(Connection conn, String database) throws SQLException {
        String original = currentDatabase(conn);
        if (database == null || database.isBlank()) {
            return new SchemaScope(conn, null);
        }
        if (!SqlName.isSafe(database)) {
            throw new SQLException("非法的数据库名");
        }
        if (!original.isBlank() && database.equalsIgnoreCase(original)) {
            return new SchemaScope(conn, original);
        }
        // 有的驱动 setCatalog 不刷新 MySQL 的会话库，所以回来再测一次：没切过去就扔
        conn.setCatalog(database);
        String after = currentDatabase(conn);
        if (after.isBlank() || !database.equalsIgnoreCase(after)) {
            throw new SQLException("无法切换到数据库 " + database + " (原库=" + original + ")");
        }
        return new SchemaScope(conn, original);
    }

    /**
     * 当前库名：优先 SELECT DATABASE()（MySQL），H2/PostgreSQL/ClickHouse 回退到
     * getSchema()/getCatalog()。
     */
    private static String currentDatabase(Connection conn) throws SQLException {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT DATABASE()")) {
            if (rs.next()) {
                Object v = rs.getObject(1);
                if (v != null) {
                    String from = String.valueOf(v).strip();
                    if (!from.isBlank()) {
                        return from;
                    }
                }
            }
        } catch (SQLException ignored) {
            // 非 MySQL 内核（H2/PostgreSQL/ClickHouse 不背 SELECT DATABASE()）
        }
        String schema = conn.getSchema();
        String catalog = conn.getCatalog();
        return schema == null || schema.isBlank() ? (catalog == null ? "" : catalog) : schema;
    }

    private static boolean isMysql(Connection conn) {
        try {
            String product = conn.getMetaData().getDatabaseProductName();
            return product != null && (product.toLowerCase().contains("mysql")
                    || product.toLowerCase().contains("mariadb"));
        } catch (SQLException e) {
            return false;
        }
    }

    private static String describeSqlError(String raw) {
        if (raw == null) {
            return "未提供原因";
        }
        return raw.length() > 200 ? raw.substring(0, 200) : raw;
    }

    /**
     * 出错的 SQL 文本标题（日志/错误信息用，不含完整正文以免打满屏幕）
     */
    private static String statementLabel(String sql) {
        String s = sql == null ? "" : sql.strip();
        return s.length() > 120 ? s.substring(0, 120) + " ..." : s;
    }

    /**
     * 把建表 DDL、列、索引/外键、表选项拍成 DbTableSpec。
     *
     * <p>先 SHOW CREATE TABLE（它的原文已含索引与外键，回滚只需 DROP + CREATE）；
     * 不背 SHOW CREATE TABLE 的内核走 JDBC 元数据拼装（索引/外键要单独重建）。</p>
     *
     * <p>逻辑在 {@link DbTableSpec#showCreateTable(Connection, String, String)} 统一管理：
     * 拍快照源的 DDL 与显示源必须同出一处，否则回滚质量与页面显示会分叉</p>
     */
    private DbTableSpec buildTableSpec(Connection conn, String database, String table) throws Exception {
        return DbTableSpec.showCreateTable(conn, database, table);
    }

    /**
     * 从 JSON 正文中取出表结构快照：正文损坏时返回 null（调用方处理）
     */
    private DbTableSpec parseSpec(String snapshot) {
        if (snapshot == null || snapshot.isBlank()) {
            return null;
        }
        try {
            return JSON.readValue(snapshot, DbTableSpec.class);
        } catch (Exception e) {
            log.warn("[schema-snapshot] 解析快照正文失败: {}", e.getMessage());
            return null;
        }
    }

    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    /**
     * 作用域：仿 DbConsoleController#useSchema（切 catalog，close() 还原）
     */
    private static class SchemaScope implements AutoCloseable {
        private final Connection conn;
        private final String original;

        SchemaScope(Connection conn, String original) {
            this.conn = conn;
            this.original = original;
        }

        String database() {
            return original;
        }

        Connection conn() {
            return conn;
        }

        @Override
        public void close() throws SQLException {
            if (original != null && !original.isBlank()) {
                conn.setCatalog(original);
            }
        }
    }
}
