package com.chua.starter.datasourcehub.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.lang.datasource.dialect.SqlName;
import com.chua.starter.ai.support.configuration.client.EffectiveChatModelResolver;
import com.chua.starter.ai.support.model.AiChatRequest;
import com.chua.starter.ai.support.model.AiChatResult;
import com.chua.starter.ai.support.model.AiClientProbe;
// 控制台的写操作留痕：@SysLog 事件由 DatasourceOperLogListener 消费落 ms_datasource_oper_log
import com.chua.starter.common.support.annotations.SysLog;
import com.chua.starter.datasourcehub.support.dbconsole.DbAiStatusVO;
import com.chua.starter.datasourcehub.support.dbconsole.DbColumnDef;
import com.chua.starter.datasourcehub.support.dbconsole.DbColumnVO;
import com.chua.starter.datasourcehub.support.dbconsole.DbDialectSupport;
import com.chua.starter.datasourcehub.support.dbconsole.DbErDiagram;
import com.chua.starter.datasourcehub.support.dbconsole.DbFilter;
import com.chua.starter.datasourcehub.support.dbconsole.DbObjectDialect;
import com.chua.starter.datasourcehub.support.dbconsole.DbProcessKillRequest;
import com.chua.starter.datasourcehub.support.dbconsole.DbProcessVO;
import com.chua.starter.datasourcehub.support.dbconsole.DbSlowQueryVO;
import com.chua.starter.datasourcehub.support.dbconsole.DbSqlBatchVO;
import com.chua.starter.datasourcehub.support.dbconsole.DbUserDeleteDTO;
import com.chua.starter.datasourcehub.support.dbconsole.DbUserSaveDTO;
import com.chua.starter.datasourcehub.support.dbconsole.DbUserVO;
import com.chua.starter.datasourcehub.support.dbconsole.DbSqlExecuteDTO;
import com.chua.starter.datasourcehub.support.dbconsole.DbSqlResultVO;
import com.chua.starter.datasourcehub.support.dbconsole.DbStructureDiff;
import com.chua.starter.datasourcehub.support.dbconsole.DbText2SqlDTO;
import com.chua.starter.datasourcehub.support.dbconsole.DbText2SqlVO;
import com.chua.starter.datasourcehub.support.dbconsole.DbTableDialect;
import com.chua.starter.datasourcehub.support.dbconsole.DbTableDataVO;
import com.chua.starter.datasourcehub.support.dbconsole.DbTableVO;
import com.chua.starter.datasourcehub.support.dbconsole.DbTxRegistry;
import com.chua.starter.datasourcehub.support.dbconsole.DbTxSession;
import com.chua.starter.datasourcehub.support.dbconsole.DbTxStatus;
import com.chua.starter.datasourcehub.support.dbconsole.DbTransferCodec;
import com.chua.starter.datasourcehub.support.dbconsole.SqlParameterBinder;
import com.chua.starter.datasourcehub.support.dbconsole.SqlScriptSplitter;
import com.chua.starter.datasourcehub.support.config.DatasourceHubProperties;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import com.chua.starter.datasourcehub.support.service.InfluxdbManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据库 web 管理器（navicat 风格）REST 接口。
 *
 * <p>原属 spring-support-monitor-starter 的 {@code MonitorController}（{@code /ms/monitor/db/**}）。
 * 为让采集调度等同样挂载数据源中心的应用也能使用本控制台，db 控制器与全部 Db* 支撑类
 * 一并迁至本模块：URL 保持 {@code /ms/monitor/db/**} 不变，既不改既有前端与调用方，
 * 也让依赖本模块的后端（监控 8081、采集 18190）开箱即得同一套控制台。</p>
 *
 * <p>只依赖本模块的 {@code DatasourceService} 与事务注册表 {@code DbTxRegistry}，
 * 不引用监控侧任何 bean。</p>
 *
 * @since 2026/10/6
 * @author CH
 */
@Tag(name = "数据库管理", description = "库表浏览/SQL 控制台/结构维护/导入导出/ER 图")
@RestController
@RequestMapping("/ms/monitor/db")
@RequiredArgsConstructor
@Slf4j
public class DbConsoleController {

    /**
     * 数据源服务（数据源配置落库与 JDBC 连接构建）。
     */
    private final DatasourceService datasourceService;

    /**
     * InfluxDB 管理服务（可选）：数据源为 influxdb 类型时，
     * databases/tables/columns 端点改走 Bucket/度量/字段 映射。
     */
    private final ObjectProvider<InfluxdbManageService> influxdbManageServiceProvider;

    /**
     * 数据源中心配置（语句执行超时等）。
     */
    private final DatasourceHubProperties properties;

    /**
     * 显式事务会话注册表（查询控制台的「事务」开关用）。
     *
     * <p>为空时事务端点不可用：不要给个默认实现，
     * 那会让「事务没生效」变成一个静默的功能缺失。</p>
     */
    private final ObjectProvider<DbTxRegistry> dbTxRegistryProvider;

    /**
     * AI 对话门面（可选）：宿主应用未集成 ai-starter 时取不到实现，
     * text2sql 能力整体上报为不可用（前端据此隐藏入口），不阻断控制台其余功能。
     */
    private final ObjectProvider<com.chua.starter.ai.support.service.AiChatFacadeService> aiChatFacadeProvider;

    /**
     * 生效对话模型解析器（可选）：全项目统一的「该用哪个模型对话」解析入口
     * （bot/default_model → runtime/default_model → 旧键兜底，并校验模型已启用）。
     *
     * <p>text2sql 若不显式带模型，AiChatFacade 会回退到注册中心默认客户端，
     * 在「未配置 YAML 密钥」的宿主里那是个必然失败的空聚合壳——表现为按钮可用、
     * 点下去报「未生成出 SQL」。这里与聊天入口同一条解析链，把请求落到具体模型上，
     * 由 facade 走 DB 提供商配置（sys_ai_provider*）构建真实客户端。</p>
     */
    private final ObjectProvider<EffectiveChatModelResolver> effectiveChatModelResolverProvider;

    /**
     * 取事务注册表。
     *
     * @return 注册表
     * @throws IllegalStateException 未装配事务支持
     */
    private DbTxRegistry dbTxRegistry() {
        DbTxRegistry registry = dbTxRegistryProvider.getIfAvailable();
        if (registry == null) {
            throw new IllegalStateException("当前实例未启用显式事务支持");
        }
        return registry;
    }
    // ==================== 数据库 web 管理器（支持按数据源 ID 连接，navicat 风格） ====================

    /**
     * 系统内置 schema，不作为可浏览的数据库展示。
     */
    /**
     * 系统库黑名单：不出现在库列表里，也不允许建/删。
     *
     * <p>{@code INFORMATION_SCHEMA} 在 MariaDB 上同时是 catalog 与 schema，
     * {@code SYS} 在 MySQL 上是 catalog，{@code PG_CATALOG} 是 PostgreSQL 的系统 schema。
     * {@code MYSQL} / {@code PERFORMANCE_SCHEMA} / {@code PG_TOAST} 同理属内核自有库，
     * 在上面建对象会被拒或产生难以理解的报错。</p>
     */
private static final java.util.Set<String> SYSTEM_SCHEMAS = java.util.Set.of(
            "INFORMATION_SCHEMA", "PG_CATALOG", "SYS", "MYSQL",
            "PERFORMANCE_SCHEMA", "PG_TOAST");

    /**
     * SQL 控制台单次查询返回的最大行数（超出置 truncated=true）
     */
    private static final int SQL_MAX_ROWS = 1000;

    // ==================== 数据库对象（存储过程 / 函数 / 触发器 / 事件）与外键 ====================

    /**
     * 各类对象的数量（树上的分类节点角标）。
 *
     * <p>一次返回四类计数，前端展开一个库时只多打一个请求，而不是四个。</p>
 *
 * @param datasourceId 数据源 ID
 * @param database 库名
 * @return 各类计数与错误信息
 */
@Operation(summary = "数据库对象分类计数")
    @GetMapping("objects/summary")
    public ReturnResult<Map<String, Object>> dbObjectSummary(
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        try (java.sql.Connection conn = datasourceService.connection(datasourceId);
             SchemaScope scope = useSchema(conn, database)) {
            String schema = requireSchema(conn, database);
            Map<String, Object> counts = new java.util.LinkedHashMap<>();
            Map<String, String> errors = new java.util.LinkedHashMap<>();
            for (var type : DbObjectDialect.Category.values()) {
                try {
                    counts.put(type.name().toLowerCase(java.util.Locale.ROOT),
                            DbObjectDialect
                                    .list(conn, schema, type).size());
                } catch (Exception e) {
                    // 某一类读不到（权限不足 / 内核无此概念）不该拖垮整棵树：
                    // 记 0 并把原因带上，前端在节点上显示「不可用」
                    counts.put(type.name().toLowerCase(java.util.Locale.ROOT), 0);
                    errors.put(type.name().toLowerCase(java.util.Locale.ROOT), e.getMessage());
                }
            }
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("counts", counts);
            payload.put("errors", errors);
            return ReturnResult.ok(payload);
        } catch (Exception e) {
            return ReturnResult.error("获取对象计数失败: " + e.getMessage());
        }
    }

    // ==================== 表属性 / 索引增强 / 主键（Navicat 表属性三页） ====================

    /**
     * 读取列的属性维度（UNSIGNED / ZEROFILL / AUTO_INCREMENT / 字符集 / 排序规则 / ENUM 取值）。
     *
     * <p>JDBC 的 {@code getColumns} 不提供这些，必须查 {@code information_schema.COLUMNS}。
     * 键统一大写，便于与内核返回的列名对齐。</p>
     *
     * @param conn  数据库连接
     * @param table 表名
     * @return 列名（大写）→ 属性
     */
    private static Map<String, Map<String, Object>> readColumnAttributes(
            java.sql.Connection conn, String table) {
        Map<String, Map<String, Object>> result = new java.util.LinkedHashMap<>();
        String sql = "SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, EXTRA, CHARACTER_SET_NAME,"
                + " COLLATION_NAME, COLUMN_DEFAULT, CHARACTER_MAXIMUM_LENGTH, NUMERIC_SCALE"
                + " FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?"
                + " ORDER BY ORDINAL_POSITION";
        try (java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, table);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    if (columnName == null) {
                        continue;
                    }
                    int length = rs.getInt("CHARACTER_MAXIMUM_LENGTH");
                    String extra = rs.getString("EXTRA");
                    var parsed = DbColumnDef.parseAttributes(
                            rs.getString("COLUMN_TYPE"),
                            rs.wasNull() ? 0 : length,
                            !"NO".equalsIgnoreCase(rs.getString("IS_NULLABLE")),
                            DbColumnDef.normalizeDefault(rs.getString("COLUMN_DEFAULT")),
                            extra != null && extra.toUpperCase(java.util.Locale.ROOT).contains("AUTO_INCREMENT"));
                    parsed.put("charset", rs.getString("CHARACTER_SET_NAME"));
                    parsed.put("collation", rs.getString("COLLATION_NAME"));
                    int scale = rs.getInt("NUMERIC_SCALE");
                    parsed.put("scale", rs.wasNull() ? 0 : scale);
                    result.put(columnName.toUpperCase(java.util.Locale.ROOT), parsed);
                }
            }
        } catch (java.sql.SQLException e) {
            // 降级为「只给基础字段」是对的（非 MySQL 系内核没有 information_schema.COLUMNS），
            // 但**必须留下痕迹**：本方法早前引用了一个没写进 SELECT 的列
            // （CHARACTER_MAXIMUM_LENGTH），异常被这里静默吞掉，整张属性表退化成空 Map，
            // 表现为「所有列的无符号 / 字符集 / 枚举取值全是空」，前端据此回填弹窗就会把
            // 属性清空后再保存 —— 一路走到内核才报 400，根因完全不可见。
            // 所以：先打日志，别让这类「静默降级」再发生。
            log.warn("读取列属性维度失败，本次降级为只返回基础字段。table={} cause={}",
                    table, e.getMessage());
        }
        return result;
    }

    /**
     * 读取表属性（引擎 / 字符集 / 排序规则 / 注释 / 自增起始值 / 行数）。
     *
     * @param tableName 表名
     * @param datasourceId 数据源 ID
     * @param database 库名
     * @return 表属性
     */
    @Operation(summary = "读取表属性")
    @GetMapping("table/{tableName}/options")
    public ReturnResult<Map<String, Object>> getTableOptions(
            @PathVariable String tableName,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        String t = safeIdent(tableName, "表名");
        Long dsId = queryOrBodyDatasourceId(datasourceId, null);
        String db = queryOrBodyDatabase(database, null);
        return callDb("读取表属性", () -> {
            try (java.sql.Connection conn = datasourceService.connection(dsId);
                 SchemaScope scope = useSchema(conn, db)) {
                requireTable(conn, t);
                return DbTableDialect.readOptions(conn, t);
            }
        });
    }

    /**
     * 修改表属性。
     *
     * @param tableName 表名
     * @param body      属性请求体
     * @return 是否成功
     */
    @Operation(summary = "修改表属性")
    @SysLog(name = "修改表显示设置")
    @PutMapping("table/{tableName}/options")
    public ReturnResult<Boolean> updateTableOptions(
            @PathVariable String tableName,
            @RequestBody Map<String, Object> body) {
        String t = safeIdent(tableName, "表名");
        Long autoIncrement = intOrNull(body.get("autoIncrement")) == null
                ? null : intOrNull(body.get("autoIncrement")).longValue();
        var options = new DbTableDialect.TableOptions(
                body.get("engine") == null ? null : String.valueOf(body.get("engine")),
                body.get("charset") == null ? null : String.valueOf(body.get("charset")),
                body.get("collation") == null ? null : String.valueOf(body.get("collation")),
                body.get("comment") == null ? null : String.valueOf(body.get("comment")),
                autoIncrement);
        Long dsId = queryOrBodyDatasourceId(null, body);
        String db = queryOrBodyDatabase(null, body);
        return callDb("修改表属性", () -> {
            try (java.sql.Connection conn = datasourceService.connection(dsId);
                 SchemaScope scope = useSchema(conn, db)) {
                requireTable(conn, t);
                DbTableDialect.alterOptions(conn, t, options);
                return Boolean.TRUE;
            }
        });
    }

    /**
     * 列出可用的存储引擎、字符集与排序规则（前端下拉的数据源）。
     *
     * <p>刻意由内核下发而不是前端硬编码：不同内核、不同版本的可用值不同，
     * 前端写死会让用户选到一个内核不认的值。</p>
     *
     * @param charset 限定字符集，可为空
     * @param datasourceId 数据源 ID
     * @param database 库名
     * @return 引擎列表与排序规则列表
     */
    @Operation(summary = "列出可用引擎/字符集/排序规则")
    @GetMapping("table-options/available")
    public ReturnResult<Map<String, Object>> listTableOptionValues(
            @RequestParam(required = false) String charset,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        return callDb("读取可用表属性值", () -> {
            try (java.sql.Connection conn = datasourceService.connection(datasourceId);
                 SchemaScope scope = useSchema(conn, database)) {
                Map<String, Object> payload = new java.util.LinkedHashMap<>();
                payload.put("engines", DbTableDialect.listEngines(conn));
                payload.put("collations",
                        DbTableDialect.listCollations(conn, charset));
                return payload;
            }
        });
    }

    /**
     * 设置主键（可改可加可清空）。
     *
     * @param tableName 表名
     * @param body      columns 为主键列（顺序即复合主键列序），传空数组表示清除
     * @return 是否成功
     */
    @Operation(summary = "设置主键")
    @SysLog(name = "设置表主键")
    @PutMapping("table/{tableName}/primary-key")
    public ReturnResult<Boolean> setTablePrimaryKey(
            @PathVariable String tableName,
            @RequestBody Map<String, Object> body) {
        String t = safeIdent(tableName, "表名");
        @SuppressWarnings("unchecked")
        List<String> columns = (List<String>) body.get("columns");
        Long dsId = queryOrBodyDatasourceId(null, body);
        String db = queryOrBodyDatabase(null, body);
        return callDb("设置主键", () -> {
            try (java.sql.Connection conn = datasourceService.connection(dsId);
                 SchemaScope scope = useSchema(conn, db)) {
                requireTable(conn, t);
                for (String column : columns == null ? List.<String>of() : columns) {
                    requireColumn(conn, t, safeIdent(column, "主键列"));
                }
                DbTableDialect.setPrimaryKey(conn, t, columns);
                return Boolean.TRUE;
            }
        });
    }

    /**
     * 数据库对象类别。
     *
     * @param category 类别名（procedure / function / trigger / event）
     * @return 类别
     */
    private static DbObjectDialect.Category objectCategory(String category) {
        return DbObjectDialect.Category.of(category);
    }

    /**
     * 列出数据库对象。
     *
     * <p>树上的「存储过程 / 函数 / 触发器 / 事件」四个分类节点都由它驱动。</p>
     *
     * @param category 类别名
     * @param datasourceId 数据源 ID
     * @param database 库名
     * @return 对象清单
     */
    @Operation(summary = "列出数据库对象（存储过程/函数/触发器/事件）")
    @GetMapping("objects/{category}")
    public ReturnResult<java.util.List<Map<String, Object>>> listDbObjects(
            @PathVariable String category,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        try {
            var type = objectCategory(category);
            try (java.sql.Connection conn = datasourceService.connection(datasourceId);
                 SchemaScope scope = useSchema(conn, database)) {
                return ReturnResult.ok(DbObjectDialect
                        .list(conn, requireSchema(conn, database), type));
            }
        } catch (IllegalArgumentException e) {
            return ReturnResult.error(e.getMessage());
        } catch (Exception e) {
            return ReturnResult.error("获取" + category + "列表失败: " + e.getMessage());
        }
    }

    /**
     * 读取单个对象的定义。
     *
     * @param category 类别名
     * @param name     对象名
     * @param datasourceId 数据源 ID
     * @param database 库名
     * @return 定义文本
     */
    @Operation(summary = "读取数据库对象定义")
    @GetMapping("objects/{category}/{name}/definition")
    public ReturnResult<String> getDbObjectDefinition(
            @PathVariable String category,
            @PathVariable String name,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        return callDb("读取对象定义", () -> {
            var type = objectCategory(category);
            try (java.sql.Connection conn = datasourceService.connection(datasourceId);
                 SchemaScope scope = useSchema(conn, database)) {
                return DbObjectDialect
                        .definition(conn, requireSchema(conn, database), name, type);
            }
        });
    }

    /**
     * 新建或整体替换一个对象。
     *
     * <p>传 {@code existOk=true} 时先删后建，实现「改定义」。</p>
     *
     * @param category 类别名
     * @param body     请求体（definition / existOk）
     * @param datasourceId 数据源 ID
     * @param database 库名
     * @return 是否成功
     */
    @Operation(summary = "新建或替换数据库对象")
    @SysLog(name = "新建/修改数据库对象")
    @PostMapping("objects/{category}")
    public ReturnResult<Boolean> upsertDbObject(
            @PathVariable String category,
            @RequestBody Map<String, Object> body,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        String definition = body.get("definition") == null ? "" : String.valueOf(body.get("definition")).trim();
        boolean existOk = Boolean.TRUE.equals(body.get("existOk"));
        // datasourceId / database 允许放在 body 或 query：与外键端点保持一致，
        // 否则只传 query 时会被静默当成「默认主数据源的默认库」——
        // 实测曾把存储过程建到 ngxacc 而不是请求的 jdbc_acc，且只在后续调用时才报错。
        Long dsId = bodyDatasourceId(body) != null ? bodyDatasourceId(body) : datasourceId;
        String db = bodyDatabase(body) != null ? bodyDatabase(body) : database;
        return callDb("保存" + category, () -> {
            var type = objectCategory(category);
            try (java.sql.Connection conn = datasourceService.connection(dsId);
                 SchemaScope scope = useSchema(conn, db)) {
                String schema = requireSchema(conn, db);
                if (existOk) {
                    // 「改定义」= 先删后建；对象不存在就跳过删除
                    try {
                        DbObjectDialect.drop(conn, schema, nameFrom(definition), type, true);
                    } catch (Exception ignored) {
                        // 对象本来就不存在
                    }
                }
                DbObjectDialect.executeDefinition(conn, definition);
                return Boolean.TRUE;
            }
        });
    }

    /**
     * 删除对象。
     *
     * @param category 类别名
     * @param name     对象名
     * @param ifExists 对象不存在时是否静默
     * @param datasourceId 数据源 ID
     * @param database 库名
     * @return 是否成功
     */
    @Operation(summary = "删除数据库对象")
    @SysLog(name = "删除数据库对象")
    @DeleteMapping("objects/{category}/{name}")
    public ReturnResult<Boolean> dropDbObject(
            @PathVariable String category,
            @PathVariable String name,
            @RequestParam(required = false, defaultValue = "true") boolean ifExists,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        return callDb("删除" + category, () -> {
            var type = objectCategory(category);
            try (java.sql.Connection conn = datasourceService.connection(datasourceId);
                 SchemaScope scope = useSchema(conn, database)) {
                DbObjectDialect
                        .drop(conn, requireSchema(conn, database), name, type, ifExists);
                return Boolean.TRUE;
            }
        });
    }

    /**
     * 从 DDL 文本里取对象名（用于 existOk 的先删后建）。
     *
     * <p>形如 {@code CREATE [DEFINER=…] PROCEDURE/FUNCTION/TRIGGER/EVENT 名(…)}，
     * 取该关键字<strong>之后</strong>的第一个标识符。</p>
     *
     * <p><b>必须从「关键字结尾」开始匹配，不能从 {@code lastIndexOf} 命中位置 +1 开始。</b>
     * {@code "CREATE PROCEDURE"} 里 {@code lastIndexOf("PROCEDURE")} 命中的是 {@code CREATE}
     * 之后那个单词的首字母，+1 会落进单词内部，先匹配到 {@code ROCEDURE} ——
     * 实测因此发出 {@code DROP PROCEDURE ROCEDURE}（IF EXISTS 静默通过），
     * 紧接着 CREATE 就报 "already exists"。</p>
     *
     * @param definition DDL 文本
     * @return 对象名，取不到返回空串（此时 existOk 分支跳过删除，不会误删）
     */
    private static String nameFrom(String definition) {
        String text = definition == null ? "" : definition;
        String upper = text.toUpperCase(java.util.Locale.ROOT);
        for (String keyword : new String[]{"PROCEDURE", "FUNCTION", "TRIGGER", "EVENT"}) {
            int at = upper.lastIndexOf(keyword);
            if (at < 0) {
                continue;
            }
            var matcher = java.util.regex.Pattern.compile("[A-Za-z0-9_]+").matcher(text);
            // +keyword.length() 跳过关键字本身，避免匹配到关键字的残片
            if (matcher.find(at + keyword.length())) {
                return matcher.group();
            }
        }
        return "";
    }

    /**
     * 取目标库名；为空时用连接当前所在的库。
     *
     * <p><b>必须走 {@link #currentDatabase} 而不是 {@code getCatalog()}／{@code getSchema()}。</b>
     * MySQL Connector/J 的 {@code getCatalog()} 只反映驱动内部记录的 URL 里的库，
     * {@code USE} 切库后它**不会变**。因此这里若用 {@code getCatalog()}，会出现
     * 「对象已建在 jdbc_acc、查询却打到 ngxacc」的错位：建的过程不报错，
     * 紧接着的列表/读定义/删除全部查不到，像是对象没建上。
     * {@link #currentDatabase} 用 {@code SELECT DATABASE()} 取服务端会话库，才是权威值。</p>
     *
     * @param conn     数据库连接
     * @param database 目标库名，可为空
     * @return 库名
     * @throws java.sql.SQLException 读取失败
     */
    private static String requireSchema(java.sql.Connection conn, String database) throws java.sql.SQLException {
        if (database != null && !database.isBlank()) {
            return database;
        }
        return currentDatabase(conn);
    }

    /**
     * 新增外键。
     *
     * @param tableName 表名
     * @param body      外键定义（name / columns / refTable / refColumns / onUpdate / onDelete）
     * @return 是否成功
     */
    @Operation(summary = "新增外键")
    @SysLog(name = "新增外键")
    @PostMapping("table/{tableName}/foreign-key")
    public ReturnResult<Boolean> addForeignKey(
            @PathVariable String tableName,
            @RequestBody Map<String, Object> body,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        String t = safeIdent(tableName, "表名");
        String fkName = safeIdent(String.valueOf(body.get("name")), "约束名");
        String refTable = safeIdent(String.valueOf(body.get("refTable")), "引用表");
        String onUpdate = body.get("onUpdate") == null ? null : String.valueOf(body.get("onUpdate"));
        String onDelete = body.get("onDelete") == null ? null : String.valueOf(body.get("onDelete"));
        @SuppressWarnings("unchecked")
        List<String> columns = (List<String>) body.get("columns");
        @SuppressWarnings("unchecked")
        List<String> refColumns = (List<String>) body.get("refColumns");
        Long dsId = queryOrBodyDatasourceId(datasourceId, body);
        String db = queryOrBodyDatabase(database, body);
        return callDb("新增外键", () -> {
            try (java.sql.Connection conn = datasourceService.connection(dsId);
                 SchemaScope scope = useSchema(conn, db)) {
                requireTable(conn, t);
                requireTable(conn, refTable);
                var fk = new DbObjectDialect.ForeignKeyDefinition(
                        fkName, columns, refTable, refColumns, onUpdate, onDelete);
                DbObjectDialect.addForeignKey(conn, t, fk);
                return Boolean.TRUE;
            }
        });
    }

    /**
     * 删除外键。
     *
     * @param tableName 表名
     * @param fkName    约束名
     * @param datasourceId 数据源 ID
     * @param database 库名
     * @return 是否成功
     */
    @Operation(summary = "删除外键")
    @SysLog(name = "删除外键")
    @DeleteMapping("table/{tableName}/foreign-key/{fkName}")
    public ReturnResult<Boolean> dropForeignKey(
            @PathVariable String tableName,
            @PathVariable String fkName,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        String t = safeIdent(tableName, "表名");
        String name = safeIdent(fkName, "约束名");
        return callDb("删除外键", () -> {
            try (java.sql.Connection conn = datasourceService.connection(datasourceId);
                 SchemaScope scope = useSchema(conn, database)) {
                requireTable(conn, t);
                DbObjectDialect.dropForeignKey(conn, t, name);
                return Boolean.TRUE;
            }
        });
    }

    /**
     * 把连接切到指定库，并在返回的作用域关闭时还原。
     *
     * <p>MySQL 用 catalog 表达库、PostgreSQL/H2 用 schema，两侧都尝试一次；
     * 都没切过去时抛错，避免语句静默跑在默认库上。</p>
     *
     * <p>连接取自连接池，会话级 schema 不会随归还而复位，调用方必须把返回值放进
     * try-with-resources，否则切库会污染后续取到同一物理连接的语句。</p>
     *
     * <p>安全豁免：schema 名经 {@link SqlName#checkSimple(String, String)} 严格校验，
     * MySQL 的底层方言 {@code USE} 语句只拼接已校验标识符。</p>
     *
     * @param conn     数据库连接
     * @param database 目标库名，为空表示不切换
     * @return 用完需关闭的切库作用域
     * @throws java.sql.SQLException 目标库无法选中时抛出
     */
    private SchemaScope useSchema(java.sql.Connection conn, String database) throws java.sql.SQLException {
        // 目标库为空：不切换（默认主数据源/沿用连接默认库）
        if (database == null || database.isBlank()) {
            return new SchemaScope(conn, null);
        }
        if (!DbDialectSupport.isSafeIdentifier(database)) {
            throw new java.sql.SQLException("数据库名不合法");
        }
        String original = currentDatabase(conn);
        if (!original.isBlank() && database.equalsIgnoreCase(original)) {
            return new SchemaScope(conn, original);
        }
        // original 为空 = 读不到当前会话库（未知）。此时**一律走切换**：
        // USE 是幂等的，多发一次没有代价；而按「未知 == 目标」早退会漏掉真正的切换。
        // 这里**不能**再用 conn.getCatalog() 判断「本就在该库上」。
        // 该值对 MySQL 系只是 JDBC URL 里记录的库名，与服务端会话库无关：连接从池里
        // 取出后若没发过 USE，会话可能停在别的库（实测数据源 20 的 URL 是 jdbc_acc，
        // 而会话实际在 ngxacc）。用它早退会导致**不发 USE**，于是整段逻辑静默地
        // 在错误的库里执行 —— 表现为「指定 database=jdbc_acc 却查不到 jdbc_acc 的对象」，
        // 甚至在 information_schema 上建对象时报一条无关的权限错误。
        // currentDatabase 走 SELECT DATABASE()，对 MySQL 系是权威值，
        // 对 H2/PostgreSQL 走 getSchema() 再退回 getCatalog()，已覆盖原分支的全部场景。
        if (!databaseExists(conn, database)) {
            throw new java.sql.SQLException("数据库 " + database + " 不存在");
        }
        switchDatabase(conn, database);
        String after = currentDatabase(conn);
        // 校验必须是**权威**读数：读不到（空串）说明无法确认，就报错。
        // 绝不能因为"读不到"就放行 —— 那等于在不确定的库里执行用户 SQL，
        // 且用户看到的仍是成功响应。宁可报「无法确认」，也不能静默跑错库。
        if (after.isBlank() || !database.equalsIgnoreCase(after)) {
            throw new java.sql.SQLException("无法确认已切换到数据库 " + database
                    + "（切换前=" + (original.isBlank() ? "未知" : original) + "）");
        }
        return new SchemaScope(conn, original);
    }

    /**
     * 读取连接当前所在库（权威值）。
     *
     * <p>MySQL 系用 {@code SELECT DATABASE()}：Connector/J 的 {@code getCatalog()} 只反映
     * 驱动内部字段、{@code setCatalog()} 不会真正切换服务端会话库，必须以服务端查询为准；
     * 其余内核用 {@code getSchema()}（H2/PostgreSQL）再退回 {@code getCatalog()}。</p>
     *
     * @param conn 数据库连接
     * @return 当前库名，读取失败返回 空
     */
    private static String currentDatabase(java.sql.Connection conn) {
        if (isMysqlFamily(conn)) {
            try (java.sql.Statement st = conn.createStatement();
                 java.sql.ResultSet rs = st.executeQuery("SELECT DATABASE()")) {
                if (rs.next()) {
                    String value = rs.getString(1);
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            } catch (java.sql.SQLException ignored) {
                // **不要**在这里回落到 getSchema()/getCatalog()：MySQL Connector/J 的这两个
                // 方法返回的是 JDBC URL 里记录的库名，与服务端会话库无关（元数据读取偶发失败
                // 时就会读到它）。一旦把「URL 里的库名」当成「当前会话库」，
                // useSchema 会误判「已经在目标库上」而**跳过 USE**，语句就静默跑在别的库上。
                // 读不到就返回空串（= 未知），由调用方按「未知」处理。
                return "";
            }
        }
        try {
            String schema = conn.getSchema();
            if (schema != null && !schema.isBlank()) {
                return schema;
            }
        } catch (java.sql.SQLException ignored) {
            // 驱动不支持 schema，退回 catalog
        }
        try {
            return conn.getCatalog();
        } catch (java.sql.SQLException ignored) {
            return null;
        }
    }

    /**
     * 切换连接所在库（跨方言）。
     *
     * <p>MySQL 系执行 {@code USE `db`}（唯一可靠方式）；H2/PostgreSQL 用
     * {@code setSchema}（失败退回 {@code setCatalog}）。</p>
     *
     * @param conn 数据库连接
     * @param db   目标库名
     * @throws java.sql.SQLException 切换失败
     */
    private static void switchDatabase(java.sql.Connection conn, String db) throws java.sql.SQLException {
        // 判内核：元数据读取可能偶发失败（连接池里的连接被回收/超时），
        // 此时 isMysqlFamily 会返回 false。若就此改走 setSchema/setCatalog 路径，
        // 对 Connector/J 而言**服务端会话库根本没变**（那两个方法只更新驱动内部字段），
        // 后续校验又用同一个不可靠的元数据读回，于是「静默在错误的库里执行」。
        // 因此：判定为 MySQL 系**或判定不出来**时，都先试 USE（USE 对非 MySQL 系会报语法错，
        // 那时再落到 setSchema 路径），不把内核判定的抖动放大成静默跑错库。
        boolean knownMysql = isMysqlFamily(conn);
        if (knownMysql || isMysqlFamilyUnknown(conn)) {
            // setCatalog 只更新驱动内部字段（供 getTables/getColumns 等元数据定位库），
            // 不真正切换服务端会话库；USE 才切会话。两者都要，缺一会导致「数据查对库、元数据查错库」。
            try {
                conn.setCatalog(db);
            } catch (java.sql.SQLException ignored) {
                // 驱动不支持 setCatalog，元数据退化为按会话默认库
            }
            try (java.sql.Statement st = conn.createStatement()) {
                st.executeUpdate("USE " + SqlName.quoteMysql(db, "数据库名"));
                return;
            } catch (java.sql.SQLException useFailed) {
                if (knownMysql) {
                    throw useFailed;
                }
                // 判定不出来且 USE 被拒（如 H2）→ 落到下面的 setSchema/setCatalog 路径
            }
        }
        java.sql.SQLException first = null;
        try {
            conn.setSchema(db);
            return;
        } catch (java.sql.SQLException e) {
            first = e;
        }
        try {
            conn.setCatalog(db);
        } catch (java.sql.SQLException e) {
            throw first;
        }
    }

    /**
     * 是否 MySQL 系内核（元数据读取失败时按非 MySQL 处理）。
     *
     * @param conn 数据库连接
     * @return true=MySQL/MariaDB 一类
     */
    private static boolean isMysqlFamily(java.sql.Connection conn) {
        try {
            String product = conn.getMetaData().getDatabaseProductName();
            if (product == null) {
                return false;
            }
            String lower = product.toLowerCase(java.util.Locale.ROOT);
            return lower.contains("mysql") || lower.contains("mariadb") || lower.contains("oceanbase");
        } catch (java.sql.SQLException e) {
            return false;
        }
    }

    /**
     * 内核判定是否**读不出来**（与「确认不是 MySQL 系」区分开）。
     *
     * <p>{@link #isMysqlFamily} 把「元数据读取抛异常」和「确实不是 MySQL 系」
     * 都返回 false，而这两者必须区别对待：前者不能据此断定要改走 setSchema 路径，
     * 否则会在 MySQL 上执行一个不切会话库的「切换」。</p>
     *
     * @param conn 数据库连接
     * @return true=读不出内核信息
     */
    private static boolean isMysqlFamilyUnknown(java.sql.Connection conn) {
        try {
            return conn.getMetaData().getDatabaseProductName() == null;
        } catch (java.sql.SQLException e) {
            return true;
        }
    }

    /**
     * 判断库是否存在。
     *
     * <p>必须显式校验：部分驱动（如 H2）接受任意 catalog 值而不报错，
     * 只比对切换结果会让写错的库名静默落在当前库上执行。</p>
     *
     * @param conn     数据库连接
     * @param database 库名
     * @return 存在返回 true
     * @throws java.sql.SQLException 读取元数据失败时抛出
     */
    private static boolean databaseExists(java.sql.Connection conn, String database) throws java.sql.SQLException {
        try (java.sql.ResultSet rs = conn.getMetaData().getSchemas()) {
            while (rs.next()) {
                if (database.equalsIgnoreCase(rs.getString(1))) {
                    return true;
                }
            }
        } catch (java.sql.SQLException ignored) {
            // 驱动不支持 schema 视图，继续用 catalog 判断
        }
        try (java.sql.ResultSet rs = conn.getMetaData().getCatalogs()) {
            while (rs.next()) {
                if (database.equalsIgnoreCase(rs.getString(1))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 切库作用域的还原句柄，关闭时把连接切回原库，避免会话状态泄漏给连接池的下一个使用者。
     *
     * <p>原先还带 {@code catalog}/{@code schema} 两个快照字段，重构后合并成单库的
     * {@code database}，注释没跟着改——读代码的人会以为还能拿到 schema 信息。</p>
     *
     * @param conn     被切换的连接，close 时负责切回；取自连接池，恒非 null
     *                  （紧凑构造器已做非空校验）
     * @param database 切库前所在的库名，close 时按方言还原到这个库。取值来自
     *                  {@code useSchema} 里的 {@code currentDatabase(conn)}：MySQL 系读
     *                  {@code SELECT DATABASE()}，其余内核先读 {@code getSchema()} 再退回
     *                  {@code getCatalog()}，三者都失败时为 null。因此它究竟是 catalog 还是
     *                  schema 取决于数据库内核，H2 等把库名暴露为 catalog 时即为 catalog。
     *                  允许为 null 或空白串：那表示本次根本没有发生切库（目标库名本身为空），
     *                  或没能读到原库名，此时 {@code close()} 直接返回、不做还原，
     *                  连接将以当前库状态被归还给连接池
     */
    private record SchemaScope(java.sql.Connection conn, String database) implements AutoCloseable {

        /**
         * 规范构造器：被切换的连接非空校验。
         *
         * <p>value class 前置条件——语义必填的引用组件必须空值敌对。连接取自数据源服务，
         * 不可能为 null；目标库名则显式允许为 null（为空表示不切换）。</p>
         *
         * @param conn     被切换的连接
         * @param database 切换前的 catalog，可为空
         */
        SchemaScope {
            conn = Objects.requireNonNull(conn, "conn 不能为 null");
        }

        @Override
        public void close() {
            if (database == null || database.isBlank()) {
                return;
            }
            try {
                // 按方言还原到进入前的库（MySQL 用 USE；H2/PG 用 setSchema）
                switchDatabase(conn, database);
            } catch (java.sql.SQLException ignored) {
                // 还原失败只能随连接一起被池回收；连接缓存下下次访问会按目标库重切
            }
        }
    }

    /**
     * 获取数据库列表。
     * @param datasourceId datasourceID，不允许为 null
     * @return Return结果 对象
     */
    @Operation(summary = "获取数据库列表")
    @GetMapping("databases")
    public ReturnResult<java.util.List<String>> getDbDatabases(@RequestParam(required = false) Long datasourceId) {
        // InfluxDB 没有 JDBC 连接：Bucket → 数据库 映射
        InfluxdbManageService influxSvc = influxdbManageServiceProvider.getIfAvailable();
        if (influxSvc != null && isInfluxdbType(datasourceId)) {
            try {
                return ReturnResult.ok(influxSvc.buckets(datasourceId).stream()
                        .map(com.chua.starter.datasourcehub.support.model.InfluxdbBucketVO::name)
                        .filter(n -> n != null && !n.isEmpty())
                        .toList());
            } catch (Exception e) {
                return ReturnResult.error("获取 InfluxDB 数据库列表失败: " + e.getMessage());
            }
        }
        try (java.sql.Connection conn = datasourceService.connection(datasourceId)) {
            java.util.List<String> catalogs = new java.util.ArrayList<>();
            java.util.List<String> schemas = new java.util.ArrayList<>();
            java.util.Set<String> seenCatalogs = new java.util.HashSet<>();
            java.util.Set<String> seenSchemas = new java.util.HashSet<>();
            try (java.sql.ResultSet rs = conn.getMetaData().getCatalogs()) {
                while (rs.next()) {
                    String catalog = rs.getString(1);
                    // 系统库必须一并过滤：MariaDB 的 information_schema 既是 catalog 也是
                    // schema，只过滤 schema 分支的话它会作为「可浏览的数据库」出现在树上，
                    // 而在它下面建对象会被内核拒绝（Access denied … to database
                    // 'information_schema'），用户看到的是一条与操作上下文无关的权限错误。
                    if (catalog != null
                            && !SYSTEM_SCHEMAS.contains(catalog.toUpperCase())
                            && seenCatalogs.add(catalog.toUpperCase())) {
                        catalogs.add(catalog);
                    }
                }
            }
            // MySQL 用 catalog 表达库，H2/PostgreSQL 用 schema，两侧都取一次
            try (java.sql.ResultSet rs = conn.getMetaData().getSchemas()) {
                while (rs.next()) {
                    String schema = rs.getString(1);
                    if (schema == null
                            || SYSTEM_SCHEMAS.contains(schema.toUpperCase())
                            || !seenSchemas.add(schema.toUpperCase())) {
                        continue;
                    }
                    schemas.add(schema);
                }
            }
            // 可切 schema 优先：H2 文件库的 catalog 是**文件库名**（如 PR4082F9_H2），
            // 不是可切的 schema —— useSchema 对它切不动，报「无法确认已切换到数据库」，
            // 前端拿 databases[0] 建树首节点必失败。MySQL 系 schema 过滤后要么与
            // catalogs 同集（行为不变）、要么为空（回退 catalogs，等于原逻辑）；
            // SQLite 两侧皆空返回 []，由前端空树兜底。
            return ReturnResult.ok(schemas.isEmpty() ? catalogs : schemas);
        } catch (Exception e) {
            return ReturnResult.error("获取数据库列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取数据库表列表。
     */
    @Operation(summary = "获取数据库表列表")
    @GetMapping("tables")
    public ReturnResult<java.util.List<DbTableVO>> getDbTables(
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        // InfluxDB：度量 → 表 映射
        InfluxdbManageService influxSvc = influxdbManageServiceProvider.getIfAvailable();
        if (influxSvc != null && isInfluxdbType(datasourceId)) {
            try {
                java.util.List<DbTableVO> result = new java.util.ArrayList<>();
                for (var m : influxSvc.measurements(datasourceId, database)) {
                    result.add(new DbTableVO(m.name(), "TABLE", null));
                }
                return ReturnResult.ok(result);
            } catch (Exception e) {
                return ReturnResult.error("获取 InfluxDB 表列表失败: " + e.getMessage());
            }
        }
        try (java.sql.Connection conn = datasourceService.connection(datasourceId);
             SchemaScope scope = useSchema(conn, database)) {
            java.util.List<DbTableVO> tables = new java.util.ArrayList<>();
            try (java.sql.ResultSet rs = conn.getMetaData().getTables(conn.getCatalog(), conn.getSchema(), "%", new String[]{"TABLE", "VIEW"})) {
                while (rs.next()) {
                    tables.add(new DbTableVO(rs.getString("TABLE_NAME"), rs.getString("TABLE_TYPE"), rs.getString("REMARKS")));
                }
            }
            return ReturnResult.ok(tables);
        } catch (Exception e) {
            return ReturnResult.error("获取表列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取表结构。
     */
    @Operation(summary = "获取表结构")
    @GetMapping("table/{tableName}/columns")
    public ReturnResult<java.util.List<DbColumnVO>> getTableColumns(
            @PathVariable String tableName,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        // InfluxDB：度量字段 → 列 映射
        InfluxdbManageService influxSvc = influxdbManageServiceProvider.getIfAvailable();
        if (influxSvc != null && isInfluxdbType(datasourceId)) {
            try {
                java.util.List<DbColumnVO> result = new java.util.ArrayList<>();
                int pos = 0;
                for (var f : influxSvc.measurementFields(datasourceId, database, tableName)) {
                    pos++;
                    result.add(DbColumnVO.basic(f.name(), f.type(), 0, true,
                            null, null, pos, false));
                }
                return ReturnResult.ok(result);
            } catch (Exception e) {
                return ReturnResult.error("获取 InfluxDB 表结构失败: " + e.getMessage());
            }
        }
        try (java.sql.Connection conn = datasourceService.connection(datasourceId);
             SchemaScope scope = useSchema(conn, database)) {
            String table = safeIdent(tableName, "表名");
            requireTable(conn, table);
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();
            java.util.Set<String> pkCols = new java.util.HashSet<>();
            try (java.sql.ResultSet pkRs = conn.getMetaData().getPrimaryKeys(catalog, schema, table)) {
                while (pkRs.next()) {
                    pkCols.add(pkRs.getString("COLUMN_NAME"));
                }
            }
            java.util.List<DbColumnVO> columns = new java.util.ArrayList<>();
            // 属性维度（UNSIGNED/ZEROFILL/AUTO_INCREMENT/字符集/排序规则/ENUM 取值）
            // JDBC 的 getColumns 不给，只能查 information_schema.COLUMNS；查不到时降级为
            // 「只给基础字段」，而不是让整个接口失败 —— 基础信息仍然可用。
            Map<String, Map<String, Object>> attrs = readColumnAttributes(conn, table);
            try (java.sql.ResultSet rs = conn.getMetaData().getColumns(catalog, schema, table, "%")) {
                while (rs.next()) {
                    String name = rs.getString("COLUMN_NAME");
                    String typeName = rs.getString("TYPE_NAME");
                    Map<String, Object> attr = attrs.get(name == null ? "" : name.toUpperCase(java.util.Locale.ROOT));
                    if (attr == null) {
                        columns.add(DbColumnVO.basic(name, typeName, rs.getInt("COLUMN_SIZE"),
                                rs.getInt("NULLABLE") == 1, rs.getString("REMARKS"),
                                DbColumnDef.normalizeDefault(rs.getString("COLUMN_DEF")),
                                rs.getInt("ORDINAL_POSITION"), pkCols.contains(name)));
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    List<String> enumValues = (List<String>) attr.getOrDefault("enumValues", List.of());
                    columns.add(new DbColumnVO(
                            name,
                            typeName,
                            rs.getInt("COLUMN_SIZE"),
                            rs.getInt("NULLABLE") == 1,
                            rs.getString("REMARKS"),
                            // 用归一化后的默认值，而不是 JDBC 的 COLUMN_DEF 原文：
                            // 后者对「可空且无显式默认值」的列是字面文本 NULL，
                            // 原样回填会让用户改个注释也撞上 Invalid default value。
                            String.valueOf(attr.getOrDefault("defaultValue", "")),
                            rs.getInt("ORDINAL_POSITION"),
                            pkCols.contains(name),
                            String.valueOf(attr.getOrDefault("baseType", typeName)),
                            Boolean.TRUE.equals(attr.get("unsigned")),
                            Boolean.TRUE.equals(attr.get("zerofill")),
                            // parseAttributes 放进来的是 Boolean，不是内核 EXTRA 的 "YES"/"no" 文本；
                            // 之前按 "YES" 文本比较，结果自增列恒为 false（且不报错，很难发现）。
                            Boolean.TRUE.equals(attr.get("autoIncrement")),
                            String.valueOf(attr.getOrDefault("charset", "")),
                            String.valueOf(attr.getOrDefault("collation", "")),
                            enumValues,
                            attr.get("scale") instanceof Integer i ? i : 0));
                }
            }
            return ReturnResult.ok(columns);
        } catch (Exception e) {
            return ReturnResult.error("获取表结构失败: " + e.getMessage());
        }
    }

    /**
     * 获取表数据（分页）。
     *
     * <p>安全豁免：表名、排序列先经标识符白名单与 JDBC 元数据校验；分页值使用
     * PreparedStatement 绑定。</p>
     */
    @Operation(summary = "获取表数据")
    @GetMapping("table/{tableName}/data")
    public ReturnResult<DbTableDataVO> getTableData(
            @PathVariable String tableName,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String orderBy,
            @RequestParam(required = false) String orderDir,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database,
            @RequestParam(defaultValue = "true") boolean count,
            @RequestParam(required = false) String where,
            @RequestParam(required = false) String filters) {
        int normalizedPage = Math.max(1, page);
        int normalizedPageSize = Math.min(500, Math.max(1, pageSize));
        long offset = (long) (normalizedPage - 1) * normalizedPageSize;
        String table = safeIdent(tableName, "表名");
        String column = orderBy == null || orderBy.isBlank() ? null : safeIdent(orderBy, "排序列");
        String direction = "DESC".equalsIgnoreCase(orderDir) ? "DESC" : "ASC";
        // WHERE 过滤：允许 Navicat 式自由条件，但拒绝语句分隔符/注释，避免堆叠多条语句
        String whereClause = sanitizeWhere(where);
        // 结构化筛选（高级筛选）：列名走白名单、值走 PreparedStatement 绑定，
        // 与上面那条「自由文本 + 黑名单」路径是**两套独立机制**，不互相兜底。
        // 两者同时给出时按 AND 组合（用户在文本框里写的条件仍生效）。
        DbFilter.Compiled compiled;
        try {
            compiled = DbFilter.compile(parseFilters(filters));
        } catch (IllegalArgumentException e) {
            return ReturnResult.error("筛选条件非法: " + e.getMessage());
        }
        List<String> both = new ArrayList<>(2);
        if (whereClause != null) {
            both.add(whereClause);
        }
        if (!compiled.isEmpty()) {
            both.add(compiled.whereSql());
        }
        String whereSql = both.isEmpty() ? "" : " WHERE " + String.join(" AND ", both);
        try (java.sql.Connection conn = datasourceService.connection(datasourceId);
             SchemaScope scope = useSchema(conn, database)) {
            requireTable(conn, table);
            if (column != null) {
                requireColumn(conn, table, column);
            }
            String quotedTable = DbDialectSupport.quoteIdent(conn, table, "表名");
            String orderClause = column == null ? ""
                    : " ORDER BY " + DbDialectSupport.quoteIdent(conn, column, "排序列") + " " + direction;
            String dataSql = "SELECT * FROM " + quotedTable + whereSql + orderClause + " LIMIT ? OFFSET ?";
            String countSql = "SELECT COUNT(*) FROM " + quotedTable + whereSql;
            java.util.List<String> columns;
            java.util.List<Map<String, Object>> rows;
            try (java.sql.PreparedStatement stmt = conn.prepareStatement(dataSql)) {
                applyQueryTimeout(stmt);
                // 绑定顺序必须是「筛选参数 → 分页参数」：whereSql 在 SQL 文本里
                // 位于 LIMIT/OFFSET 之前，顺序反了不会报错、只会查错行。
                int idx = 1;
                for (Object p : compiled.params()) {
                    stmt.setObject(idx++, p);
                }
                stmt.setInt(idx++, normalizedPageSize);
                stmt.setLong(idx, offset);
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    java.sql.ResultSetMetaData meta = rs.getMetaData();
                    int colCount = meta.getColumnCount();
                    columns = new java.util.ArrayList<>();
                    for (int i = 1; i <= colCount; i++) {
                        columns.add(meta.getColumnLabel(i));
                    }
                    rows = new java.util.ArrayList<>();
                    while (rs.next()) {
                        Map<String, Object> row = new java.util.LinkedHashMap<>();
                        for (int i = 1; i <= colCount; i++) {
                            // 必须过 cellValue：直接把 java.sql.Date/Time/Timestamp 交给 Jackson
                            // 会变成 epoch 毫秒数，网格里显示成 1793376000000，且编辑保存时
                            // 会被原样拼进 UPDATE 写坏日期列
                            row.put(columns.get(i - 1), DbTransferCodec.cellValue(rs.getObject(i)));
                        }
                        rows.add(row);
                    }
                }
            }
            long total = -1;
            if (count) {
                // countSql 里没有 LIMIT/OFFSET，只绑筛选参数
                try (java.sql.PreparedStatement stmt = conn.prepareStatement(countSql)) {
                    applyQueryTimeout(stmt);
                    int idx = 1;
                    for (Object p : compiled.params()) {
                        stmt.setObject(idx++, p);
                    }
                    try (java.sql.ResultSet countRs = stmt.executeQuery()) {
                        if (countRs.next()) {
                            total = countRs.getLong(1);
                        }
                    }
                }
            }
            return ReturnResult.ok(new DbTableDataVO(columns, rows, total, normalizedPage, normalizedPageSize));
} catch (Exception e) {
            return ReturnResult.error("获取表数据失败: " + e.getMessage());
        }
    }

    /**
     * 校验 WHERE 过滤条件（Navicat 式自由条件）。
     *
     * <p>仅作为管理端的数据筛选用途，允许列名/运算符/函数；为防堆叠多条语句，
     * 拒绝语句分隔符与注释符。空白输入返回 null（不过滤）。</p>
     *
     * @param where 过滤条件
     * @return 规整后的条件；为空返回 null
     * @throws IllegalArgumentException 含禁止字符时抛出
     */
    private static String sanitizeWhere(String where) {
        if (where == null || where.isBlank()) {
            return null;
        }
        String value = where.trim();
        for (String forbidden : new String[]{";", "--", "/*", "*/", "#"}) {
            if (value.contains(forbidden)) {
                throw new IllegalArgumentException("过滤条件含非法字符: " + forbidden);
            }
        }
        return value;
    }

    /**
     * 解析结构化筛选条件（JSON 数组）。
     *
     * <p>刻意**用原始字符串手工解析**而不是引入 Jackson 树：
     * 这层已经有 {@code ReturnResult} 的 Jackson 配置，再引一份 ObjectMapper
     * 只会带来「两套配置不一致」的坑，而这里的结构极简
     * （列名/运算符/值1/值2 四个字符串字段）。</p>
     *
     * @param json JSON 数组文本；为空视为无条件
     * @return 条件列表
     * @throws IllegalArgumentException 结构不是数组或含非对象元素
     */
    private static List<DbFilter.Condition> parseFilters(String json) {
        List<DbFilter.Condition> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        String text = json.strip();
        if (!text.startsWith("[")) {
            throw new IllegalArgumentException("filters 必须是 JSON 数组");
        }
        int depth = 0;
        boolean inStr = false;
        StringBuilder cur = new StringBuilder();
        java.util.List<String> items = new java.util.ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inStr) {
                if (c == '\\' && i + 1 < text.length()) {
                    cur.append(c).append(text.charAt(++i));
                } else {
                    if (c == '"') {
                        inStr = false;
                    }
                    cur.append(c);
                }
                continue;
            }
            if (c == '"') {
                inStr = true;
                cur.append(c);
                continue;
            }
            if (c == '{') {
                depth++;
                cur.append(c);
                continue;
            }
            if (c == '}') {
                depth--;
                cur.append(c);
                continue;
            }
            if (c == ']' && depth == 0) {
                break;
            }
            if (c == '}' && depth == 0) {
                throw new IllegalArgumentException("filters 的对象没有正确闭合");
            }
            if (c == ',' && depth == 0) {
                if (!cur.isEmpty()) {
                    items.add(cur.toString());
                    cur.setLength(0);
                }
                continue;
            }
            cur.append(c);
        }
        if (!cur.isEmpty()) {
            items.add(cur.toString());
        }
        for (String item : items) {
            out.add(new DbFilter.Condition(
                    jsonField(item, "column"),
                    jsonField(item, "op"),
                    jsonField(item, "value"),
                    jsonField(item, "value2")));
        }
        return out;
    }

    /**
     * 从一个 JSON 对象文本里取字符串字段。
     *
     * <p>只支持字符串与数字两种取值 —— 够用且不必处理转义地狱；
     * 解析不出来返回空串，由 {@link DbFilter#compile} 按「空值跳过」处理。</p>
     *
     * @param obj   对象文本
     * @param field 字段名
     * @return 字段值；不存在或非标量返回空串
     */
    private static String jsonField(String obj, String field) {
        String needle = "\"" + field + "\"";
        int at = obj.indexOf(needle);
        if (at < 0) {
            return "";
        }
        int i = obj.indexOf(':', at + needle.length());
        if (i < 0) {
            return "";
        }
        i++;
        while (i < obj.length() && Character.isWhitespace(obj.charAt(i))) {
            i++;
        }
        if (i >= obj.length()) {
            return "";
        }
        char c = obj.charAt(i);
        if (c != '"') {
            // 数字 / 布尔 / null：读到下一个分隔符为止
            int end = i;
            while (end < obj.length() && ",}".indexOf(obj.charAt(end)) < 0) {
                end++;
            }
            return obj.substring(i, end).trim();
        }
        StringBuilder sb = new StringBuilder();
        for (i++; i < obj.length(); i++) {
            char ch = obj.charAt(i);
            if (ch == '\\' && i + 1 < obj.length()) {
                sb.append(obj.charAt(++i));
                continue;
            }
            if (ch == '"') {
                break;
            }
            sb.append(ch);
        }
        return sb.toString();
    }

    /**
     * 获取表的建表语句（DDL）。
     *
     * <p>优先 {@code SHOW CREATE TABLE}（MySQL/H2 兼容）；不支持时按列+主键拼装 CREATE TABLE，
     * 保证在 PostgreSQL 等内核也能给出可用 DDL。</p>
     *
     * @param tableName    表名
     * @param datasourceId 数据源 ID
     * @param database     库名
     * @return 建表语句
     */
    @Operation(summary = "获取建表语句")
    @GetMapping("table/{tableName}/ddl")
    public ReturnResult<String> getTableDdl(
            @PathVariable String tableName,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        String table = safeIdent(tableName, "表名");
        try (java.sql.Connection conn = datasourceService.connection(datasourceId);
             SchemaScope scope = useSchema(conn, database)) {
            requireTable(conn, table);
            String quoted = SqlName.quoteMysql(table, "表名");
            try (java.sql.Statement st = conn.createStatement();
                 java.sql.ResultSet rs = st.executeQuery("SHOW CREATE TABLE " + quoted)) {
                if (rs.next()) {
                    // SHOW CREATE TABLE 的列是「表名 / 建表语句」；判定必须用「以 CREATE 开头」，
                    // 用「包含 CREATE」会命中相邻的表名列（表名里带 create 字样时）
                    for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                        Object v = rs.getObject(i);
                        if (v instanceof String s && s.strip().toUpperCase().startsWith("CREATE")) {
                            return ReturnResult.ok(s);
                        }
                    }
                }
            } catch (java.sql.SQLException ignored) {
                // 非 SHOW CREATE 内核，走列结构拼装
            }
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();
            java.util.Set<String> pkCols = new java.util.LinkedHashSet<>();
            try (java.sql.ResultSet pkRs = conn.getMetaData().getPrimaryKeys(catalog, schema, table)) {
                while (pkRs.next()) {
                    pkCols.add(pkRs.getString("COLUMN_NAME"));
                }
            }
            StringBuilder ddl = new StringBuilder("CREATE TABLE ").append(quoted).append(" (\n");
            java.util.List<String> defs = new java.util.ArrayList<>();
            try (java.sql.ResultSet rs = conn.getMetaData().getColumns(catalog, schema, table, "%")) {
                while (rs.next()) {
                    String name = rs.getString("COLUMN_NAME");
                    String type = rs.getString("TYPE_NAME");
                    int size = rs.getInt("COLUMN_SIZE");
                    boolean nullable = rs.getInt("NULLABLE") == 1;
                    String def = rs.getString("COLUMN_DEF");
                    StringBuilder line = new StringBuilder("  ").append(SqlName.quoteMysql(name, "列名"))
                            .append(' ').append(type);
                    if (size > 0 && java.util.Set.of("VARCHAR", "CHAR", "DECIMAL", "NUMERIC").contains(type.toUpperCase())) {
                        line.append('(').append(size).append(')');
                    }
                    line.append(nullable ? " NULL" : " NOT NULL");
                    if (def != null && !def.isBlank()) {
                        line.append(" DEFAULT ").append(def);
                    }
                    if (pkCols.contains(name)) {
                        line.append(" PRIMARY KEY");
                    }
                    defs.add(line.toString());
                }
            }
            ddl.append(String.join(",\n", defs)).append("\n);");
            return ReturnResult.ok(ddl.toString());
        } catch (Exception e) {
            return ReturnResult.error("获取建表语句失败: " + e.getMessage());
        }
    }

    /**
     * 获取表的外键列表。
     *
     * @param tableName    表名
     * @param datasourceId 数据源 ID
     * @param database     库名
     * @return 外键列表（约束名 / 本表列 / 引用表 / 引用列）
     */
    @Operation(summary = "获取外键列表")
    @GetMapping("table/{tableName}/foreign-keys")
    public ReturnResult<java.util.List<Map<String, Object>>> getForeignKeys(
            @PathVariable String tableName,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        String table = safeIdent(tableName, "表名");
        try (java.sql.Connection conn = datasourceService.connection(datasourceId);
             SchemaScope scope = useSchema(conn, database)) {
            requireTable(conn, table);
            java.util.List<Map<String, Object>> list = new java.util.ArrayList<>();
            try (java.sql.ResultSet rs = conn.getMetaData().getImportedKeys(conn.getCatalog(), conn.getSchema(), table)) {
                while (rs.next()) {
                    Map<String, Object> row = new java.util.LinkedHashMap<>();
                    row.put("fkName", rs.getString("FK_NAME"));
                    row.put("columnName", rs.getString("FKCOLUMN_NAME"));
                    row.put("refTable", rs.getString("PKTABLE_NAME"));
                    row.put("refColumn", rs.getString("PKCOLUMN_NAME"));
                    row.put("updateRule", rs.getShort("UPDATE_RULE"));
                    row.put("deleteRule", rs.getShort("DELETE_RULE"));
                    list.add(row);
                }
            }
            return ReturnResult.ok(list);
        } catch (Exception e) {
            return ReturnResult.error("获取外键失败: " + e.getMessage());
        }
    }

    /**
     * 执行 SQL（支持 SELECT/SHOW/DESC/EXPLAIN 查询与 INSERT/UPDATE/DELETE/CREATE/ALTER/DROP 等更新）。
     *
     * <p>查询语句最多返回 {@value #SQL_MAX_ROWS} 行（超出置 truncated）；更新语句返回影响行数。
     * 数据库管理器的建表/改名/复制表/查看建表语句/导入数据/查询控制台均经此端点。</p>
     *
     * @param request SQL 执行请求（sql / datasourceId / database）
     * @return 执行结果
     */
    // ---------- 显式事务（Navicat 的「事务」模式） ----------

    /** 开启事务的请求体 */
    public record DbTxBeginRequest(Long datasourceId, String database) {
    }

    /** 提交 / 回滚的请求体 */
    public record DbTxActionRequest(String txId) {
    }

    /**
     * 开启一个显式事务会话。
     *
     * <p>开启后这条连接被注册表持有，后续带 {@code txId} 的
     * {@code /db/execute}、{@code /db/execute-batch}、单元格编辑、清空表
     * 都在**同一条连接**上执行，直到显式提交或回滚。
     * 不带 {@code txId} 的请求不受影响，仍各自独立自动提交。</p>
     *
     * @param request 数据源与库
     * @return 新建的事务标识
     */
    @Operation(summary = "开启显式事务")
    @PostMapping("tx/begin")
    public ReturnResult<Map<String, Object>> beginDbTx(@RequestBody DbTxBeginRequest request) {
        return callDb("开启事务", () -> {
            DbTxSession session = dbTxRegistry().begin(
                    request == null ? null : request.datasourceId(),
                    request == null ? null : request.database());
            return Map.<String, Object>of(
                    "txId", session.txId(),
                    "datasourceId", session.datasourceId() == null ? 0L : session.datasourceId(),
                    "database", session.database() == null ? "" : session.database());
        });
    }

    /**
     * 提交事务。
     *
     * @param request 事务标识
     * @return 是否成功
     */
    @Operation(summary = "提交显式事务")
    @SysLog(name = "提交数据库事务")
    @PostMapping("tx/commit")
    public ReturnResult<Boolean> commitDbTx(@RequestBody DbTxActionRequest request) {
        return callDb("提交事务", () -> {
            dbTxRegistry().commit(request == null ? null : request.txId());
            return Boolean.TRUE;
        });
    }

    /**
     * 回滚事务。
     *
     * @param request 事务标识
     * @return 是否成功
     */
    @Operation(summary = "回滚显式事务")
    @PostMapping("tx/rollback")
    public ReturnResult<Boolean> rollbackDbTx(@RequestBody DbTxActionRequest request) {
        return callDb("回滚事务", () -> {
            dbTxRegistry().rollback(request == null ? null : request.txId());
            return Boolean.TRUE;
        });
    }

    /**
     * 当前未结束的事务列表。
     *
     * @return 事务状态
     */
    @Operation(summary = "查询未结束的事务")
    @GetMapping("tx/status")
    public ReturnResult<List<DbTxStatus>> dbTxStatus() {
        return callDb("查询事务", () -> dbTxRegistry().status());
    }

    /**
     * 库级 ER 图元数据：表清单 + 全部外键关系。
     *
     * <p>一次连接读完整库，而不是让前端逐表调外键端点 ——
     * 40 张表就是 40 次往返，每次重新连库重新切 catalog，整体视图会明显卡住。</p>
     *
     * @param datasourceId 数据源 ID
     * @param database     目标库，为空用连接当前库
     * @param tables       表名过滤（LIKE 模式，可为空表示全部）
     * @return 表清单与外键关系
     */
    @Operation(summary = "读取 ER 图元数据")
    @GetMapping("er-diagram")
    public ReturnResult<Map<String, Object>> erDiagram(
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database,
            @RequestParam(required = false) String tables) {
        return callDb("读取 ER 图", () -> {
            try (java.sql.Connection conn = datasourceService.connection(datasourceId);
                 SchemaScope scope = useSchema(conn, database)) {
                return DbErDiagram.load(conn, database, tables);
            }
        });
    }

    /** 结构对比请求 */
    public record DbStructureDiffRequest(
            Long sourceDatasourceId,
            String sourceDatabase,
            Long targetDatasourceId,
            String targetDatabase,
            String tables) {
    }

    /**
     * 比较两个库的结构并生成同步脚本（Navicat 的「比较 / 结构同步」）。
     *
     * <p>方向固定「源 → 目标」。结构同步是单向操作 ——
     * 「以谁为准」必须由用户在界面上选，不该由实现替用户决定。</p>
     *
     * <p><b>只生成脚本，不代执行</b>：差异清单里含 {@code DROP INDEX}
     * 这类不可逆语句，是否执行由用户看过脚本后决定。</p>
     *
     * @param request 源/目标数据源与库
     * @return 差异清单
     */
    @Operation(summary = "比较两个库的结构")
    @PostMapping("structure-diff")
    public ReturnResult<List<DbStructureDiff.DiffItem>> structureDiff(
            @RequestBody DbStructureDiffRequest request) {
        return callDb("比较库结构", () -> diffItems(request));
    }

    /**
     * 两个库的结构对比（源 → 目标，单向）。
     *
     * <p>{@code structure-diff}（普通查询）与 {@code structure-sync-run}（SSE 流式执行）
     * 共用这一份对比逻辑：两边拿到的差异清单 <b>必须完全一致</b>，否则会出现
     * 「预览脚本 5 条、实际执行 4 条」这类只有在执行时才暴露的口径漂移。</p>
     *
     * @param request 源/目标数据源与库
     * @return 差异清单
     * @throws SQLException 读元数据/切库失败
     */
    private List<DbStructureDiff.DiffItem> diffItems(DbStructureDiffRequest request)
            throws Exception {
        Long srcDs = request.sourceDatasourceId();
        String srcDb = request.sourceDatabase();
        Long dstDs = request.targetDatasourceId() == null ? srcDs : request.targetDatasourceId();
        String dstDb = request.targetDatabase();
        // 源与目标同库时没有任何差异，直接返回空而不是白开两条连接比一遍
        if (Objects.equals(srcDs, dstDs) && Objects.equals(srcDb, dstDb)) {
            return List.of();
        }
        // 源与目标**同一个数据源**时只能共用一条连接并切 catalog：
        // datasourceService.connection(id) 返回的是缓存共享的同一条物理连接，
        // 两次拿到同一个对象，第二次 USE 会覆盖第一次，
        // 于是变成「拿目标库跟自己比」并恒返回空列表（2026-10-05 实测）。
        if (Objects.equals(srcDs, dstDs)) {
            try (java.sql.Connection conn = datasourceService.connection(srcDs)) {
                return DbStructureDiff.diffSameDatasource(
                        conn, srcDb, dstDb, request.tables());
            }
        }
        try (java.sql.Connection src = datasourceService.connection(srcDs);
             SchemaScope srcScope = useSchema(src, srcDb);
             java.sql.Connection dst = datasourceService.connection(dstDs);
             SchemaScope dstScope = useSchema(dst, dstDb)) {
            return DbStructureDiff.diffAcrossDatasources(src, dst, request.tables());
        }
    }

    /**
     * 结构同步（SSE 流式执行）请求：在 diff 请求之上加执行选项。
     */
    public record StructureSyncRunRequest(
            Long sourceDatasourceId,
            String sourceDatabase,
            Long targetDatasourceId,
            String targetDatabase,
            String tables,
            /** 遇错即停（默认 true）；false 时失败语句记日志、继续执行后续 */
            Boolean stopOnError,
            /** 前端勾选/编辑后的部署脚本；非空时直接执行它，缺省时才在服务端重算 diff */
            String script
    ) {
        boolean stopOnErrorOrDefault() {
            return stopOnError == null || stopOnError;
        }
    }

    /**
     * 结构同步：比较后把生成的脚本在目标库**逐条执行**，并以 SSE 实时推送每步日志。
     *
     * <p>三次握手的第三步（Navicat 的「结构同步 → 执行同步」）。与
     * {@code structure-diff} 同一条 diff 链路，保证「预览的就是执行的」；
     * 语句在目标库上逐条执行，每一条立即通过 SSE 推送结果，前端据此渲染
     * 实时日志而不是整批执行完一次性返回。</p>
     *
     * <p>事件协议（data 均为 JSON 字符串，由 ObjectMapper 序列化）：</p>
     * <ul>
     *   <li>{@code log}：{ no, sql, ok, affectedRows, error }，一条语句的执行结果</li>
     *   <li>{@code done}：{ total, ok, fail, elapsedMs }，全部执行结束</li>
     *   <li>{@code error}：{ message }，链路早期失败（连不上/切库失败/无可执行语句时由 log 表达）</li>
     * </ul>
     *
     * <p>执行路径与 {@code execute-db-batch} 保持一致：同一目标库连接上
     * {@code USE} + {@code Statement} 逐条跑，不引入独立事务 ——
     * 中途失败时已执行的语句保持生效，前端日志能标出「错在哪条」。</p>
     *
     * @param request 源/目标数据源与库 + 执行选项
     * @return SSE 流
     */
    @Operation(summary = "结构同步（SSE 流式执行）")
    @PostMapping(value = "structure-sync-run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody structureSyncRun(
            @RequestBody StructureSyncRunRequest request,
            jakarta.servlet.http.HttpServletResponse httpResponse) {
        // 用 StreamingResponseBody（Spring 专为异步原始流设计）：无 converter 中间层，
        // 直接把字节写进响应流，SseEmitter 在此 Spring 版本会静默丢弃 data 内容
        // （Boot 4.1 / Spring 7 实测 2026-10-23 多轮：event 行在、data: 恒空）。
        //
        // 必须显式写 Content-Type：StreamingResponseBody 的 ReturnValueHandler 不会
        // 像 SseEmitter 那样自动把 @PostMapping(produces=...) 落到响应头（实测
        // 2026-10-23，content-type 恒为空）。前端 sse 客户端的 onopen 校验
        // 「Content-Type 含 text/event-stream」，缺失即报「SSE 连接失败: 200」并
        // 无限重连，日志一条都收不到。
        httpResponse.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        httpResponse.setCharacterEncoding("UTF-8");
        return outputStream -> {
            java.io.PrintWriter out = new java.io.PrintWriter(
                    new java.io.OutputStreamWriter(outputStream, java.nio.charset.StandardCharsets.UTF_8));
            out.flush();
            runStructureSync(out, request);
            out.flush();
        };
    }

    /** 结构同步日志事件里的单条语句 */
    public record StructureSyncLogVO(int no, String sql, boolean ok,
                                     Integer affectedRows, String error) {
    }

    /** 结构同步结束事件 */
    public record StructureSyncDoneVO(int total, int ok, int fail, long elapsedMs) {
    }

    /** 结构同步早期失败事件 */
    public record StructureSyncErrorVO(String message) {
    }

    /** 发送一条 SSE 事件帧：手写帧字节（event 行 + data 行 + 空行）写进 PrintWriter。
     *
     *  <p>为什么手写帧而不依赖 SseEmitter.data()：本项目在此 Spring 版本
     *  （Boot 4.1 / Spring 7）下 SseEmitter 的 data 内容会被静默丢弃（event 行
     *  正常、data: 恒空，2026-10-23 实测多轮，含 SELECT 1 最小用例）；
     *  record 的 JSON 序列化（Json.toJson / ObjectMapper）同样静默输出空串。
     *  三个 VO 字段极少，手写帧 + 手工转义即确定性最强。</p>
     */
    private void writeSseEvent(java.io.PrintWriter out, String name, Object payload) {
        try {
            out.write("event:" + name + "\n");
            out.write("data:" + structureSyncPayloadJson(payload) + "\n\n");
            out.flush();
        } catch (Exception e) {
            // 客户端断开：写流失败时静默退出线程
            log.warn("[db] SSE 写入失败: {}", e.getMessage());
        }
    }

    /** 手拼结构同步事件 payload 的 JSON（仅三个 VO，字段已知） */
    private static String structureSyncPayloadJson(Object payload) {
        if (payload instanceof StructureSyncLogVO v) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"no\":").append(v.no())
                    .append(",\"sql\":").append(jsonStr(v.sql()))
                    .append(",\"ok\":").append(v.ok())
                    .append(",\"affectedRows\":");
            if (v.affectedRows() == null) {
                sb.append("null");
            } else {
                sb.append(v.affectedRows());
            }
            sb.append(",\"error\":").append(jsonStr(v.error())).append('}');
            return sb.toString();
        }
        if (payload instanceof StructureSyncDoneVO v) {
            return "{\"total\":" + v.total()
                    + ",\"ok\":" + v.ok()
                    + ",\"fail\":" + v.fail()
                    + ",\"elapsedMs\":" + v.elapsedMs() + "}";
        }
        if (payload instanceof StructureSyncErrorVO v) {
            return "{\"message\":" + jsonStr(v.message()) + "}";
        }
        // 导入向导的事件负载是 Map / List / 标量。这里必须真序列化：
        // 以前一律落到 "{}"，前端收到的每个事件都是空对象，日志与进度全瞎。
        return jsonValue(payload);
    }

    /**
     * 通用 JSON 值序列化：覆盖 Map / Iterable / 数组 / 字符串 / 数字 / 布尔 / null。
     *
     * <p>只用于本控制器手写 SSE 帧的负载。刻意不引 ObjectMapper：本项目在该
     * Spring 版本下 record 的 JSON 序列化会静默产出空串（见 {@link #writeSseEvent}）。</p>
     *
     * @param value 任意值
     * @return JSON 文本
     */
    private static String jsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof CharSequence cs) {
            return jsonStr(cs.toString());
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof java.util.Map<?, ?> map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(jsonStr(String.valueOf(entry.getKey())))
                        .append(':')
                        .append(jsonValue(entry.getValue()));
            }
            return sb.append('}').toString();
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : iterable) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append(jsonValue(item));
            }
            return sb.append(']').toString();
        }
        if (value.getClass().isArray()) {
            StringBuilder sb = new StringBuilder("[");
            int length = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(jsonValue(java.lang.reflect.Array.get(value, i)));
            }
            return sb.append(']').toString();
        }
        if (value instanceof Enum<?> e) {
            return jsonStr(e.name());
        }
        return jsonStr(String.valueOf(value));
    }

    /** JSON 字符串字面量：转义引号、反斜杠与控制字符（含换行，SQL 里常见） */
    private static String jsonStr(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(s.length() + 8);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    /** 在目标库逐条执行结构同步脚本，逐条推送日志事件，结尾推送 done */
    private void runStructureSync(java.io.PrintWriter out, StructureSyncRunRequest request) {
        long start = System.currentTimeMillis();
        int ok = 0;
        int fail = 0;
        boolean stopOnError = request.stopOnErrorOrDefault();
        // 前端勾选/编辑后的脚本优先；缺省时服务端重算 diff 生成
        List<String> statements = new ArrayList<>();
        String givenScript = request.script();
        if (givenScript != null && !givenScript.isBlank()) {
            try {
                statements.addAll(SqlScriptSplitter.split(givenScript));
            } catch (IllegalArgumentException e) {
                writeSseEvent(out, "error", new StructureSyncErrorVO("脚本无法切分: " + e.getMessage()));
                return;
            }
        } else {
            List<DbStructureDiff.DiffItem> items = List.of();
            try {
                items = diffItems(new DbStructureDiffRequest(request.sourceDatasourceId(),
                        request.sourceDatabase(), request.targetDatasourceId(),
                        request.targetDatabase(), request.tables()));
            } catch (Exception e) {
                writeSseEvent(out, "error", new StructureSyncErrorVO("比较失败: " + describeSqlError(e.getMessage())));
                return;
            }
            // 只执行有 DDL 的差异（EXTRA_* 只提示，ddl 为空）
            for (DbStructureDiff.DiffItem it : items) {
                if (it.ddl() != null && !it.ddl().isBlank()) {
                    statements.addAll(SqlScriptSplitter.split(it.ddl()));
                }
            }
        }
        if (statements.isEmpty()) {
            writeSseEvent(out, "done", new StructureSyncDoneVO(0, 0, 0,
                    System.currentTimeMillis() - start));
            return;
        }
        Long dstDs = request.targetDatasourceId() == null
                ? request.sourceDatasourceId() : request.targetDatasourceId();
        int total = statements.size();
        try (java.sql.Connection conn = datasourceService.connection(dstDs);
             SchemaScope scope = useSchema(conn, request.targetDatabase());
             java.sql.Statement stmt = conn.createStatement()) {
            for (int i = 0; i < total; i++) {
                String sql = statements.get(i);
                try {
                    boolean hasResult = stmt.execute(sql);
                    int affected = (hasResult || stmt.getUpdateCount() < 0)
                            ? 0 : stmt.getUpdateCount();
                    ok++;
                    writeSseEvent(out, "log", new StructureSyncLogVO(i + 1, sql, true, affected, null));
                } catch (java.sql.SQLException e) {
                    fail++;
                    writeSseEvent(out, "log", new StructureSyncLogVO(i + 1, sql, false,
                            null, describeSqlError(e.getMessage())));
                    if (stopOnError) {
                        break;
                    }
                }
            }
            writeSseEvent(out, "done", new StructureSyncDoneVO(total, ok, fail,
                    System.currentTimeMillis() - start));
        } catch (Exception e) {
            writeSseEvent(out, "error", new StructureSyncErrorVO("执行失败: " + describeSqlError(e.getMessage())));
        }
    }

    @Operation(summary = "AI 文本生成 SQL 可用状态")
    @GetMapping("ai/status")
    public ReturnResult<DbAiStatusVO> aiText2SqlStatus() {
        com.chua.starter.ai.support.service.AiChatFacadeService facade = aiChatFacadeProvider.getIfAvailable();
        if (facade == null) {
            return ReturnResult.ok(new DbAiStatusVO(false, null,
                    "应用未集成 AI 对话能力（spring-support-ai-starter 未引入）"));
        }
        // 与真实对话同一条解析链路：「状态可用」即「点下去真能生成」，避免两套判据。
        // 显式带上「默认模型」解析结果：不带模型时 facade 会落到注册中心默认客户端，
        // 未配置 YAML 密钥的宿主里那是空聚合壳，会给出假阳性的「可用」。
        AiClientProbe probe = facade.probeChatClient(resolveDefaultChatModel());
        return ReturnResult.ok(new DbAiStatusVO(probe.available(), probe.model(), probe.reason()));
    }

    /**
     * 解析当前生效的默认对话模型（全项目统一解析链），解析失败或无可用模型时返回 空。
     *
     * <p>宿主未集成 ai-starter 时解析器不存在，返回 空，由调用方维持原有兜底行为。</p>
     *
     * @return 生效模型名；不可用时为 空
     */
    private String resolveDefaultChatModel() {
        EffectiveChatModelResolver resolver = effectiveChatModelResolverProvider.getIfAvailable();
        if (resolver == null) {
            return null;
        }
        try {
            return resolver.resolve(null);
        } catch (Exception e) {
            log.warn("[DbConsole] 解析默认对话模型失败: {}", e.getMessage());
            return null;
        }
    }

    @Operation(summary = "AI 文本生成 SQL")
    @PostMapping("ai/text2sql")
    public ReturnResult<DbText2SqlVO> text2sql(@RequestBody DbText2SqlDTO request) {
        String text = request.text() == null ? "" : request.text().trim();
        if (text.isEmpty()) {
            return ReturnResult.error("请先输入查询需求");
        }
        com.chua.starter.ai.support.service.AiChatFacadeService facade = aiChatFacadeProvider.getIfAvailable();
        if (facade == null) {
            return ReturnResult.error("应用未集成 AI 对话能力，无法使用 text2sql");
        }
        StringBuilder system = new StringBuilder(TEXT2SQL_SYSTEM_PROMPT);
        if (request.context() != null && !request.context().isBlank()) {
            system.append("\n\n目标数据库结构（仅按此结构生成，不要虚构不存在的表或列）：\n")
                    .append(request.context().trim());
        }
        AiChatRequest chat = new AiChatRequest();
        chat.setSystemPrompt(system.toString());
        chat.setPrompt(text);
        chat.setCallType("db_text2sql");
        chat.setStream(Boolean.FALSE);
        // 显式落到「默认模型」解析结果上：不带模型时 facade 会回退到注册中心默认客户端，
        // 未配置 YAML 密钥的宿主里那是空聚合壳，会让生成必然失败。
        String model = resolveDefaultChatModel();
        if (model != null && !model.isBlank()) {
            chat.setModel(model);
        }
        AiChatResult result = facade.callForResult(chat);
        if (!result.success()) {
            return ReturnResult.error("AI 生成失败: " + result.errorMessage());
        }
        String sql = stripMarkdownFence(result.text());
        if (sql.isBlank()) {
            return ReturnResult.error("AI 未生成出 SQL，请补充表结构或换种说法");
        }
        return ReturnResult.ok(new DbText2SqlVO(sql, result.model()));
    }

    /**
     * text2sql 的系统提示词：约束「只出 SQL、不虚构结构、不擅自执行」。
     */
    private static final String TEXT2SQL_SYSTEM_PROMPT = """
            你是 SQL 生成助手。根据用户的查询需求与提供的数据库结构，生成一条可直接执行的 SQL。
            规则：
            1. 只输出 SQL 语句本身，不要任何解释性文字；
            2. 使用 MySQL/MariaDB 方言；
            3. 只使用结构信息里真实存在的表与列，不得虚构；
            4. 信息不足时生成最合理的 SQL，并在语句尾部用 -- 注释说明假设；
            5. 涉及删除/更新时默认加上 LIMIT 或限定条件，避免全表误操作。
            """;

    /**
     * 剥掉模型输出的 markdown 围栏（```sql ... ``` 或 ``` ... ```），无围栏则原样返回。
     *
     * @param raw 模型原始输出
     * @return 纯 SQL 文本
     */
    private static String stripMarkdownFence(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.strip();
        if (!text.startsWith("```")) {
            return text;
        }
        int firstLineBreak = text.indexOf('\n');
        if (firstLineBreak < 0) {
            return "";
        }
        int closing = text.lastIndexOf("```");
        if (closing <= firstLineBreak) {
            return text.substring(firstLineBreak + 1).strip();
        }
        return text.substring(firstLineBreak + 1, closing).strip();
    }

    @Operation(summary = "执行 SQL")
    @SysLog(name = "执行 SQL")
    @PostMapping("execute")
    public ReturnResult<DbSqlResultVO> executeDbSql(@RequestBody DbSqlExecuteDTO request) {
        String sql = request.sql();
        if (sql == null || sql.trim().isEmpty()) {
            return ReturnResult.error("SQL 不能为空");
        }
        String bound;
        try {
            bound = bindParams(sql, request.params());
        } catch (IllegalArgumentException e) {
            return ReturnResult.error("SQL 参数错误: " + e.getMessage());
        }
        Long datasourceId = request.datasourceId();
        // 事务里跑：复用注册表持有的那条连接，不新建。
        // 语句失败**不自动回滚整段事务** —— 「一条失败就整体回滚」是用户显式
        // 点回滚时的语义；批量执行更是要「继续跑完并逐条记账」。
        if (request.inTransaction()) {
            try {
                DbTxSession session = dbTxRegistry().require(request.txId());
                DbSqlResultVO result = session.run(conn -> {
                    try (java.sql.Statement stmt = conn.createStatement()) {
                        return runOneStatement(stmt, bound);
                    } catch (java.sql.SQLException e) {
                        throw new IllegalStateException(e.getMessage(), e);
                    }
                });
                return ReturnResult.ok(result);
            } catch (IllegalStateException e) {
                return ReturnResult.error("SQL 执行失败: " + describeSqlError(e.getMessage()));
            } catch (IllegalArgumentException e) {
                return ReturnResult.error("SQL 执行失败: " + describeSqlError(e.getMessage()));
            }
        }
        // 顺序要求：先切库、再建 Statement。
        // Connector/J 的 Statement 会在执行时按**创建时**捕获的 catalog 还原会话库，
        // 若在 USE 之前创建 Statement，USE 会被它覆盖 —— 实测 `database=sync_src` 的查询
        // 跑在 jdbc_acc 上（返回 jdbc_acc 的表数），而同样带 database 参数的 /db/tables
        // 因为没有提前建 Statement，切库是正确的。两者行为不一致正是这个顺序问题。
        try (java.sql.Connection conn = datasourceService.connection(datasourceId);
             SchemaScope scope = useSchema(conn, request.database());
             java.sql.Statement stmt = conn.createStatement()) {
            return ReturnResult.ok(runOneStatement(stmt, bound));
        } catch (Exception e) {
            return ReturnResult.error("SQL 执行失败: " + describeSqlError(e.getMessage()));
        }
    }

    /**
     * 把 {@code ${name}} 占位符替换成字面量。
     *
     * <p>参数错误**在连库之前**就抛出来：拿到「参数 x 未提供」比先建立连接、
     * 再等内核报一个语法错误要快得多，也更容易看懂。</p>
     *
     * @param sql    原始 SQL
     * @param params 变量表
     * @return 替换后的 SQL
     * @throws IllegalArgumentException 变量名不合法或引用了未提供的变量
     */
    private static String bindParams(String sql, Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            if (SqlParameterBinder.hasPlaceholder(sql)) {
                throw new IllegalArgumentException("SQL 里有 " + SqlParameterBinder.requiredNames(sql)
                        + " 占位符，但没有提供参数");
            }
            return sql;
        }
        return SqlParameterBinder.substitute(sql, SqlParameterBinder.normalize(params));
    }

    /**
     * 扫描 SQL 里的 {@code ${name}} 占位符，供 UI 提示「这个脚本需要哪些变量」。
     *
     * <p>不连库、也不执行，纯文本扫描 —— 编辑器每次改动都问一次后端的话，
     * 成本会体现在每次按键的延迟上。</p>
     *
     * @param sql 待扫描 SQL
     * @return 占位符名列表
     */
    @Operation(summary = "扫描 SQL 参数占位符")
    @PostMapping("scan-params")
    public ReturnResult<List<String>> scanSqlParams(@RequestBody DbSqlExecuteDTO request) {
        String sql = request.sql();
        if (sql == null || sql.isBlank()) {
            return ReturnResult.ok(List.of());
        }
        return ReturnResult.ok(SqlParameterBinder.requiredNames(sql));
    }

    /**
     * 执行单条语句并把结果集读成 {@link DbSqlResultVO}。
     *
     * <p>单条端点与批量端点**必须**共用这一份实现，否则同一条 SQL 在两条路径下
     * 会出现「一条当查询、一条当更新」的行为差异（表现为同一句 {@code WITH ...}
     * 在批量里被当成更新语句、影响行数为 0）。</p>
     *
     * <p>执行前统一挂上语句级超时（{@code plugin.datasource.hub.query-timeout-seconds}）：
     * 单条 / 事务 / 批量三条路径都汇到这里，挂一处即全部生效 —— 失控 SQL 从
     * 「无限期挂死到网关超时」变成有界的 {@code SQLTimeoutException}，接口正常失败。</p>
     *
     * @param stmt 已建好的语句（调用方保证已完成切库）
     * @param sql  单条 SQL
     * @return 执行结果
     * @throws java.sql.SQLException 内核报错或超时设置失败
     */
    private DbSqlResultVO runOneStatement(java.sql.Statement stmt, String sql) throws java.sql.SQLException {
        applyQueryTimeout(stmt);
        if (SqlScriptSplitter.isQuery(sql)) {
            try (java.sql.ResultSet rs = stmt.executeQuery(sql)) {
                java.sql.ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                java.util.List<String> columns = new java.util.ArrayList<>(colCount);
                for (int i = 1; i <= colCount; i++) {
                    columns.add(meta.getColumnLabel(i));
                }
                java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
                int rowCount = 0;
                while (rs.next() && rowCount < SQL_MAX_ROWS) {
                    Map<String, Object> row = new java.util.LinkedHashMap<>(colCount);
                    for (int i = 1; i <= colCount; i++) {
                        // 同 runTableData：日期时间列必须转文本，否则下发 epoch 毫秒数
                        row.put(columns.get(i - 1), DbTransferCodec.cellValue(rs.getObject(i)));
                    }
                    rows.add(row);
                    rowCount++;
                }
                return new DbSqlResultVO(columns, rows, rowCount, rowCount >= SQL_MAX_ROWS, null, null);
            }
        }
        int affectedRows = stmt.executeUpdate(sql);
        return new DbSqlResultVO(null, null, null, null, affectedRows,
                "执行成功，影响 " + affectedRows + " 行");
    }

    /**
     * 给语句挂上统一的执行超时（{@code plugin.datasource.hub.query-timeout-seconds}，0 = 不设上限）。
     *
     * <p>覆盖不了 {@link #runOneStatement} 的直连语句点（分页/计数、DDL、清表）
     * 也走这里：大表分页与 {@code TRUNCATE}/{@code DELETE} 在锁等待下同样是
     * 「无限期挂死」的高发点。</p>
     *
     * @param stmt 语句
     * @throws java.sql.SQLException 驱动不支持超时或参数非法
     */
    private void applyQueryTimeout(java.sql.Statement stmt) throws java.sql.SQLException {
        int seconds = properties.getQueryTimeoutSeconds();
        if (seconds > 0) {
            stmt.setQueryTimeout(seconds);
        }
    }

    /**
     * 批量执行 SQL 脚本，逐条返回结果（查询控制台的「执行脚本」）。
     *
     * <p><b>为什么不复用 {@code /db/execute}</b>：那个端点的响应形状被 5 个 UI 套件
     * 与 4 个接口套件按 {@code data.columns} / {@code data.rows} 读着，
     * 往里加多结果集字段会让既有断言全部失效。批量另开端点、形状自由。</p>
     *
     * <p>逐条执行、遇错即停（可由 {@code stopOnError=false} 改为继续），
     * 失败时仍返回已执行部分的结果与失败语句的下标，
     * 便于定位「第几条开始错」。</p>
     *
     * @param request SQL 执行请求（sql / datasourceId / database / stopOnError / params）
     * @return 批量执行结果
     */
    @Operation(summary = "批量执行 SQL 脚本")
    @SysLog(name = "批量执行 SQL")
    @PostMapping("execute-batch")
    public ReturnResult<DbSqlBatchVO> executeDbSqlBatch(@RequestBody DbSqlExecuteDTO request) {
        String sql = request.sql();
        if (sql == null || sql.trim().isEmpty()) {
            return ReturnResult.error("SQL 不能为空");
        }
        java.util.List<String> statements;
        try {
            statements = SqlScriptSplitter.split(sql);
        } catch (IllegalArgumentException e) {
            return ReturnResult.error("SQL 脚本无法切分: " + e.getMessage());
        }
        if (statements.isEmpty()) {
            return ReturnResult.ok(DbSqlBatchVO.allOk(java.util.List.of(), 0, "脚本里没有可执行的语句"));
        }
        // 顺序要求：**先切分、后替换参数**。
        // 反过来做的话，参数值里只要带 `;`（'a;b' 这种完全合法的值）
        // 就会被当成语句分隔符，把一条语句拦腰截断 —— 现象是「脚本莫名其妙多切了几条」，
        // 根因（替换早于切分）却完全不在报错信息里。
        boolean stopOnError = request.stopOnErrorOrDefault();
        java.util.List<DbSqlResultVO> results = new java.util.ArrayList<>(statements.size());
        // 事务里跑：整段脚本共用注册表持有的那条连接，
        // 这样「BEGIN 在这条连接上、COMMIT 也在同一条连接上」才成立。
        if (request.inTransaction()) {
            try {
                DbTxSession session = dbTxRegistry().require(request.txId());
                return ReturnResult.ok(session.run(conn -> {
                    try (java.sql.Statement stmt = conn.createStatement()) {
                        DbSqlBatchVO early = runStatementList(statements, request.params(),
                                stopOnError, results, stmt);
                        if (early != null) {
                            return early;
                        }
                        return summarizeBatch(statements.size(), results);
                    } catch (java.sql.SQLException e) {
                        throw new IllegalStateException("批量执行失败: " + e.getMessage(), e);
                    }
                }));
            } catch (IllegalArgumentException | IllegalStateException e) {
                return ReturnResult.error(describeSqlError(e.getMessage()));
            }
        }
        try (java.sql.Connection conn = datasourceService.connection(request.datasourceId());
             SchemaScope scope = useSchema(conn, request.database());
             java.sql.Statement stmt = conn.createStatement()) {
            DbSqlBatchVO early = runStatementList(statements, request.params(), stopOnError, results, stmt);
            if (early != null) {
                return ReturnResult.ok(early);
            }
        } catch (Exception e) {
            return ReturnResult.error("批量执行失败: " + describeSqlError(e.getMessage()));
        }
        return ReturnResult.ok(summarizeBatch(statements.size(), results));
    }

    /**
     * 逐条执行脚本，把结果累加进 {@code results}。
     *
     * <p>事务与非事务两条路径共用这一段：两条路径的**逐条语义必须完全一致**
     * （同样替换参数、同样记账失败、同样按 stopOnError 提前收尾），
     * 各写一份的话，事务模式下就会出现「同样的脚本，非事务能跑完、事务跑到一半就停」
     * 这类只在开了事务才复现的偏差。</p>
     *
     * @param statements  已切分的语句
     * @param params      {@code ${name}} 取值表
     * @param stopOnError 遇错是否停止
     * @param results     结果累加容器
     * @param stmt        已创建的 Statement
     * @return 遇错停止时的结果；正常跑完返回 null
     */
    private DbSqlBatchVO runStatementList(java.util.List<String> statements,
                                         Map<String, Object> params,
                                         boolean stopOnError,
                                         java.util.List<DbSqlResultVO> results,
                                         java.sql.Statement stmt) {
        for (int i = 0; i < statements.size(); i++) {
            String one;
            try {
                one = bindParams(statements.get(i), params);
            } catch (IllegalArgumentException e) {
                // 参数缺失是「脚本问题」，不是「执行失败」，但要按同一条的失败口径报，
                // 否则 UI 上会显示成执行期错误，看不出是没传参数
                String summary = "第 " + (i + 1) + "/" + statements.size() + " 条语句参数错误: "
                        + e.getMessage() + "；语句=" + abbreviate(statements.get(i));
                if (stopOnError) {
                    return new DbSqlBatchVO(results, statements.size(), i, statements.get(i), summary);
                }
                results.add(new DbSqlResultVO(null, java.util.List.of(), 0, false, null, summary));
                continue;
            }
            try {
                results.add(runOneStatement(stmt, one));
            } catch (Exception e) {
                String reason = describeSqlError(e.getMessage());
                String summary = "第 " + (i + 1) + "/" + statements.size() + " 条语句失败: " + reason
                        + "；语句=" + abbreviate(one);
                if (stopOnError) {
                    return new DbSqlBatchVO(results, statements.size(), i, statements.get(i), summary);
                }
                // 继续执行：把失败也记成一条结果，rowCount=0 且带上原因
                results.add(new DbSqlResultVO(null, java.util.List.of(), 0, false, null, summary));
            }
        }
        return null;
    }

    /**
     * 汇总批量执行结果。
     *
     * @param total   语句总数
     * @param results 逐条结果
     * @return 汇总视图
     */
    private DbSqlBatchVO summarizeBatch(int total, java.util.List<DbSqlResultVO> results) {
        int queryCount = 0;
        int updateCount = 0;
        for (DbSqlResultVO r : results) {
            if (r.affectedRows() != null) {
                updateCount++;
            } else if (r.columns() != null) {
                queryCount++;
            }
        }
        return DbSqlBatchVO.allOk(results, total,
                "共 " + total + " 条语句执行完成（查询 " + queryCount + " 条，更新 " + updateCount + " 条）");
    }

    /**
     * 把语句缩到可放进提示的长度。
     *
     * @param sql 语句原文
     * @return 截断后的文本
     */
    private static String abbreviate(String sql) {
        String flat = sql.replaceAll("\\s+", " ").strip();
        return flat.length() <= 120 ? flat : flat.substring(0, 120) + "…";
    }

    /**
     * 删除表。
     *
     * <p>安全豁免：表名经标识符白名单与 JDBC 元数据校验后才进入 MySQL 底层方言 DDL。</p>
     */
    @Operation(summary = "删除表")
    @SysLog(name = "删除表")
    @DeleteMapping("table/{tableName}")
    public ReturnResult<Boolean> dropTable(
            @PathVariable String tableName,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        String table = safeIdent(tableName, "表名");
        return callDb("删除表", () -> {
            try (java.sql.Connection conn = datasourceService.connection(datasourceId);
                 SchemaScope scope = useSchema(conn, database)) {
                requireTable(conn, table);
                try (java.sql.PreparedStatement stmt = conn.prepareStatement("DROP TABLE " + DbDialectSupport.quoteIdent(conn, table, "表名"))) {
                    applyQueryTimeout(stmt);
                    stmt.executeUpdate();
                }
                return Boolean.TRUE;
            }
        });
    }

    /**
     * 删除视图。
     *
     * <p>视图原先<b>没有任何删除入口</b>：{@link #dropTable} 对视图会一路落到
     * {@code DROP TABLE}，被 MySQL 内核拒绝成 {@code 'x' is a view}；而界面左侧树的
     * 视图节点与表节点共用同一个右键菜单，于是「删除表」点在视图上必然失败
     * （实测 2026-10-12：{@code 删除表失败: 'jdbc_acc.erab097_view' is a view}）。</p>
     *
     * <p>这里补 {@code DROP VIEW}，让视图与表一样可删。安全豁免与
     * {@code dropTable} 同口径：对象名经标识符白名单与元数据存在性校验后才进底层方言。</p>
     */
    @Operation(summary = "删除视图")
    @SysLog(name = "删除视图")
    @DeleteMapping("view/{viewName}")
    public ReturnResult<Boolean> dropView(
            @PathVariable String viewName,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        String view = safeIdent(viewName, "视图名");
        return callDb("删除视图", () -> {
            try (java.sql.Connection conn = datasourceService.connection(datasourceId);
                 SchemaScope scope = useSchema(conn, database)) {
                // 沿用 requireTable 的存在性校验：它查的是元数据，表与视图都能命中
                requireTable(conn, view);
                try (java.sql.PreparedStatement stmt = conn.prepareStatement("DROP VIEW " + DbDialectSupport.quoteIdent(conn, view, "视图名"))) {
                    applyQueryTimeout(stmt);
                    stmt.executeUpdate();
                }
                return Boolean.TRUE;
            }
        });
    }

    /**
     * 新建表（跨方言）。
     *
     * <p>此前前端自己拼 {@code CREATE TABLE `x` (...) COMMENT='..'} 发给通用 SQL 执行端点，
     * 反引号与内联 COMMENT 都只对 MySQL 成立，H2/PostgreSQL 上直接语法错。这里改由
     * {@link DbDialectSupport} 按内核选引号与注释写法。</p>
     *
     * <p><b>两条入参路径</b>：{@code columns} 非空时是「设计表」的结构化列，
     * 逐列过 {@link #columnDef}（与新增列端点同一套键与校验）后由方言层拼 DDL；
     * 否则回退到 {@code fields} 原文 —— 那是用户自己写的 SQL，不在这里二次解析。</p>
     *
     * <p>安全豁免：表名经标识符白名单、列名/类型/长度/默认值经 {@link DbColumnDef}
     * 白名单后才进入 DDL；原文路径同上，不做二次解析。</p>
     *
     * @param request 请求，不允许为 null
     * @return 是否成功
     */
    @Operation(summary = "新建表")
    @SysLog(name = "新建表")
    @PostMapping("table")
    public ReturnResult<Boolean> createTable(@RequestBody com.chua.starter.datasourcehub.support.dbconsole.DbCreateTableDTO request) {
        String table = safeIdent(request.table(), "表名");
        boolean structured = request.columns() != null && !request.columns().isEmpty();
        String fields = request.fields() == null ? "" : request.fields().trim();
        if (!structured && fields.isEmpty()) {
            return ReturnResult.error("请填写列定义");
        }
        return callDb("新建表", () -> {
            try (java.sql.Connection conn = datasourceService.connection(request.datasourceId());
                 SchemaScope scope = useSchema(conn, request.database())) {
                if (structured) {
                    List<DbColumnDef> columns = new ArrayList<>(request.columns().size());
                    for (Map<String, Object> body : request.columns()) {
                        columns.add(columnDef(body));
                    }
                    DbDialectSupport.createTable(conn, table, columns, request.primaryKey(), request.comment());
                } else {
                    DbDialectSupport.createTable(conn, table, fields, request.comment());
                }
                return Boolean.TRUE;
            }
        });
    }

    /**
     * 重命名表（跨方言）。
     *
     * <p>此前前端拼 {@code ALTER TABLE `a` RENAME TO `b` 送通用 SQL 端点，反引号只对 MySQL 成立。</p>
     *
     * @param tableName    原表名
     * @param newName      新表名
     * @param datasourceId 数据源 ID（可选）
     * @param database     目标库名（可选）
     * @return 是否成功
     */
    @Operation(summary = "重命名表")
    @SysLog(name = "重命名表")
    @PutMapping("table/{tableName}/name")
    public ReturnResult<Boolean> renameTable(
            @PathVariable String tableName,
            @RequestParam String newName,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        String table = safeIdent(tableName, "表名");
        String target = safeIdent(newName, "新表名");
        return callDb("重命名表", () -> {
            try (java.sql.Connection conn = datasourceService.connection(datasourceId);
                 SchemaScope scope = useSchema(conn, database)) {
                com.chua.starter.datasourcehub.support.dbconsole.DbDialectSupport.renameTable(conn, table, target);
                return Boolean.TRUE;
            }
        });
    }

    /**
     * 新建数据库。
     *
     * <p>库的 DDL 随内核不同（MySQL 用 DATABASE、H2/PostgreSQL 用 SCHEMA），
     * 交由 {@link DbDialectSupport} 选型。</p>
     *
     * @param request 请求，不允许为 null
     * @return Return结果 对象
     */
    @Operation(summary = "新建数据库")
    @SysLog(name = "新建数据库")
    @PostMapping("database")
    public ReturnResult<Boolean> createDatabase(@RequestBody com.chua.starter.datasourcehub.support.dbconsole.DbDatabaseDTO request) {
        String name = request.name() == null ? "" : request.name().trim();
        if (!DbDialectSupport.isSafeIdentifier(name)
                || SYSTEM_SCHEMAS.contains(name.toUpperCase())) {
            return ReturnResult.error("数据库名只能由字母、数字和下划线组成，且不能以数字开头");
        }
        try (java.sql.Connection conn = datasourceService.connection(request.datasourceId())) {
            DbDialectSupport.createDatabase(
                    conn, name, request.charset(), request.collation());
            return ReturnResult.ok(true);
        } catch (Exception e) {
            return ReturnResult.error("创建数据库失败: " + e.getMessage());
        }
    }

    /**
     * 删除数据库。
     *
     * @param datasourceId 数据源 ID，为空或 0 表示默认主数据源
     * @param name         数据库名
     * @return Return结果 对象
     */
    @Operation(summary = "删除数据库")
    @SysLog(name = "删除数据库")
    @DeleteMapping("database")
    public ReturnResult<Boolean> dropDatabase(
            @RequestParam(required = false) Long datasourceId,
            @RequestParam String name) {
        String database = name == null ? "" : name.trim();
        if (!DbDialectSupport.isSafeIdentifier(database)
                || SYSTEM_SCHEMAS.contains(database.toUpperCase())) {
            return ReturnResult.error("数据库名只能由字母、数字和下划线组成，且不能以数字开头");
        }
        try (java.sql.Connection conn = datasourceService.connection(datasourceId)) {
            DbDialectSupport.dropDatabase(conn, database);
            return ReturnResult.ok(true);
        } catch (Exception e) {
            return ReturnResult.error("删除数据库失败: " + e.getMessage());
        }
    }

    // ==================== 数据库进程管理（显示进程 / 关闭 / 批量关闭） ====================

    /**
     * 查询目标库当前进程（会话）列表。
     *
     * <p><b>能力与方言</b>：进程列表是内核特有能力，不是所有 JDBC 数据源都有 ——
     * MySQL 系走 {@code SHOW PROCESSLIST}，PostgreSQL 走 {@code pg_stat_activity}，
     * 其余方言（Oracle / SQLServer / 文件库）返回 {@code supported=false}，前端据此
     * 不显示「显示进程」右键入口（能力声明与类型判断都在这一层做）。</p>
     *
     * <p><b>为什么不用游标分页</b>：进程列表通常只有几百行，一次拉回即可；
     * 内核返回什么列就透传什么，前端按本端点的统一行结构渲染。</p>
     *
     * @param datasourceId 数据源 ID，为空或 0 表示默认主数据源
     * @return 进程列表（supported=false 表示该内核不支持）
     */
    @Operation(summary = "数据库进程列表")
    @GetMapping("processes")
    public ReturnResult<java.util.Map<String, Object>> processes(
            @RequestParam(required = false) Long datasourceId) {
        return callDb("查询数据库进程", () -> {
            String url = "";
            try (java.sql.Connection conn = datasourceService.connection(datasourceId)) {
                url = jdbcUrlOf(datasourceId);
                String lower = url == null ? "" : url.toLowerCase(java.util.Locale.ROOT);
                java.util.List<DbProcessVO> rows;
                if (lower.startsWith("jdbc:mysql:") || lower.startsWith("jdbc:mariadb:")) {
                    rows = listMySqlProcesses(conn);
                } else if (lower.startsWith("jdbc:postgresql:")) {
                    rows = listPostgresProcesses(conn);
                } else {
                    java.util.Map<String, Object> unsupported = new java.util.LinkedHashMap<>();
                    unsupported.put("supported", false);
                    unsupported.put("processes", java.util.List.of());
                    unsupported.put("message", "该数据库内核不支持进程列表（仅 MySQL/MariaDB/PostgreSQL 支持）");
                    return unsupported;
                }
                java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
                result.put("supported", true);
                result.put("processes", rows);
                return result;
            }
        });
    }

    /**
     * 关闭单个数据库进程（会话）。
     *
     * @param request 请求：datasourceId + pid
     * @return 是否成功
     */
    @Operation(summary = "关闭数据库进程")
    @SysLog(name = "关闭数据库进程")
    @PostMapping("process/kill")
    public ReturnResult<Boolean> killProcess(@RequestBody DbProcessKillRequest request) {
        if (request == null || isBlank(request.pid())) {
            return ReturnResult.error("进程 ID 不能为空");
        }
        return killProcessesInternal(request.datasourceId(), java.util.List.of(request.pid()));
    }

    /**
     * 批量关闭数据库进程（会话）。
     *
     * @param request 请求：datasourceId + pids
     * @return 关闭结果（成功数 / 失败数）
     */
    @Operation(summary = "批量关闭数据库进程")
    @SysLog(name = "批量关闭数据库进程")
    @PostMapping("processes/kill")
    public ReturnResult<java.util.Map<String, Object>> killProcesses(@RequestBody DbProcessKillRequest request) {
        if (request == null || request.pids() == null || request.pids().isEmpty()) {
            return ReturnResult.error("进程 ID 列表不能为空");
        }
        return killProcessesInternal(request.datasourceId(), request.pids());
    }

    /**
     * 关闭进程的公共实现：MySQL 系 {@code KILL <id>}，PostgreSQL {@code pg_terminate_backend(<id>)}。
     *
     * @param datasourceId 数据源 ID
     * @param pids         进程 ID 列表
     * @param <T>          返回类型
     * @param <T>          返回类型
     * @return 关闭结果
     */
    private <T> ReturnResult<T> killProcessesInternal(Long datasourceId, java.util.List<String> pids) {
        return callDb("关闭数据库进程", () -> {
            java.util.Set<String> safePids = new java.util.LinkedHashSet<>();
            for (String pid : pids) {
                String trimmed = isBlank(pid) ? "" : pid.trim();
                if (trimmed.matches("\\d{1,15}")) {
                    safePids.add(trimmed);
                }
            }
            if (safePids.isEmpty()) {
                return (T) new java.util.LinkedHashMap<String, Object>() {{
                    put("successCount", 0);
                    put("failCount", pids.size());
                    put("message", "没有有效的进程 ID");
                }};
            }
            try (java.sql.Connection conn = datasourceService.connection(datasourceId)) {
                String url = jdbcUrlOf(datasourceId);
                String lower = url == null ? "" : url.toLowerCase(java.util.Locale.ROOT);
                String killSql;
                if (lower.startsWith("jdbc:mysql:") || lower.startsWith("jdbc:mariadb:")) {
                    killSql = "KILL ";
                } else if (lower.startsWith("jdbc:postgresql:")) {
                    killSql = "SELECT pg_terminate_backend(?)";
                } else {
                    throw new IllegalStateException("该数据库内核不支持进程关闭");
                }
                int success = 0;
                java.util.List<String> failures = new java.util.ArrayList<>();
                for (String pid : safePids) {
                    try {
                        if (killSql.endsWith("?")) {
                            try (java.sql.PreparedStatement stmt = conn.prepareStatement(killSql)) {
                                stmt.setLong(1, Long.parseLong(pid));
                                stmt.execute();
                            }
                        } else {
                            try (java.sql.Statement stmt = conn.createStatement()) {
                                stmt.execute(killSql + pid);
                            }
                        }
                        success++;
                    } catch (Exception e) {
                        failures.add(pid);
                    }
                }
                java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
                result.put("successCount", success);
                result.put("failCount", failures.size());
                result.put("message", failures.isEmpty()
                        ? "已关闭 " + success + " 个进程"
                        : "成功 " + success + " 个，失败 " + failures.size() + " 个（失败 PID: " + failures + "）");
                return (T) result;
            }
        });
    }

    // ==================== 慢查询（需求 1.7：SPI 能力判断 + 各内核查询） ====================

    /**
     * 慢查询列表（按平均耗时降序）。
     *
     * <p><b>能力与方言</b>：慢查询同样是内核特有能力 —— MySQL / MariaDB 读
     * {@code performance_schema.events_statements_summary_by_digest}，
     * PostgreSQL 读 {@code pg_stat_statements}（需先 {@code CREATE EXTENSION
     * pg_stat_statements} 并加到 shared_preload_libraries）。两处都读不到时
     * （未开启采样 / 权限不足 / 表不存在），返回 {@code supported=false}，
     * 前端据此不显示「慢查询」右键入口，或在弹窗内给出原因。</p>
     *
     * <p><b>阈值口径</b>：不依赖各库的 {@code long_query_time} 配置，
     * 直接取平均耗时最高的前 50 条 —— 对「谁在拖慢库」的定位语义一致，
     * 且不读用户变量（老账号常没有 SHOW VARIABLES 权限）。</p>
     *
     * @param datasourceId 数据源 ID，为空或 0 表示默认主数据源
     * @return 慢查询列表（supported=false 表示该内核/实例不支持）
     */
    @Operation(summary = "数据库慢查询列表")
    @GetMapping("slow-queries")
    public ReturnResult<java.util.Map<String, Object>> slowQueries(
            @RequestParam(required = false) Long datasourceId) {
        return callDb("查询数据库慢查询", () -> {
            try (java.sql.Connection conn = datasourceService.connection(datasourceId)) {
                String url = jdbcUrlOf(datasourceId);
                String lower = url == null ? "" : url.toLowerCase(java.util.Locale.ROOT);
                java.util.List<DbSlowQueryVO> rows;
                if (lower.startsWith("jdbc:mysql:") || lower.startsWith("jdbc:mariadb:")) {
                    rows = listMySqlSlowQueries(conn);
                } else if (lower.startsWith("jdbc:postgresql:")) {
                    rows = listPostgresSlowQueries(conn);
                } else {
                    java.util.Map<String, Object> unsupported = new java.util.LinkedHashMap<>();
                    unsupported.put("supported", false);
                    unsupported.put("rows", java.util.List.of());
                    unsupported.put("message", "该数据库内核不支持慢查询（仅 MySQL/MariaDB/PostgreSQL 支持）");
                    return unsupported;
                }
                java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
                result.put("supported", true);
                result.put("rows", rows);
                return result;
            }
        });
    }

    // ==================== 用户管理（需求 1：SPI 能力判断 + 各内核查询） ====================

    /**
     * 数据库用户列表（按用户名 + 主机排序）。
     *
     * <p><b>能力与方言</b>：用户管理是 MySQL/MariaDB 内核特有能力 —— 用户定义落在
     * {@code mysql.user} 表，可直接读取并执行 {@code CREATE / ALTER / DROP USER}。
     * 其余内核（含 PostgreSQL 的 {@code pg_roles}，结构差异大、没有 SSL/资源限额列）
     * 返回 {@code supported=false}，前端据此不显示「用户管理」右键入口。</p>
     *
     * @param datasourceId 数据源 ID，为空或 0 表示默认主数据源
     * @return 用户列表（supported=false 表示该内核不支持用户管理）
     */
    @Operation(summary = "数据库用户列表")
    @GetMapping("users")
    public ReturnResult<java.util.Map<String, Object>> users(
            @RequestParam(required = false) Long datasourceId) {
        return callDb("查询数据库用户", () -> {
            try (java.sql.Connection conn = datasourceService.connection(datasourceId)) {
                String url = jdbcUrlOf(datasourceId);
                String lower = url == null ? "" : url.toLowerCase(java.util.Locale.ROOT);
                if (!lower.startsWith("jdbc:mysql:") && !lower.startsWith("jdbc:mariadb:")) {
                    java.util.Map<String, Object> unsupported = new java.util.LinkedHashMap<>();
                    unsupported.put("supported", false);
                    unsupported.put("users", java.util.List.of());
                    unsupported.put("message", "该数据库内核不支持用户管理（仅 MySQL/MariaDB 支持）");
                    return unsupported;
                }
                java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
                result.put("supported", true);
                result.put("users", listMySqlUsers(conn));
                return result;
            }
        });
    }

    /**
     * 新建数据库用户：{@code CREATE USER} + 可选 {@code GRANT}。
     *
     * @param request 请求：datasourceId + username + host + password + grants
     * @return 是否成功
     */
    @Operation(summary = "新建数据库用户")
    @SysLog(name = "新建数据库用户")
    @PostMapping("users")
    public ReturnResult<Boolean> createUser(@RequestBody DbUserSaveDTO request) {
        return saveUserInternal(request, false);
    }

    /**
     * 修改数据库用户：{@code ALTER USER}（改密码 / 资源限额 / 超级权限）+ 增量 GRANT/REVOKE。
     *
     * @param request 请求：datasourceId + username + host + password + grants + superUser
     * @return 是否成功
     */
    @Operation(summary = "修改数据库用户")
    @SysLog(name = "修改数据库用户")
    @PutMapping("users")
    public ReturnResult<Boolean> updateUser(@RequestBody DbUserSaveDTO request) {
        return saveUserInternal(request, true);
    }

    /**
     * 删除数据库用户：{@code DROP USER 'x'@'h'}。
     *
     * @param request 请求：datasourceId + username + host
     * @return 是否成功
     */
    @Operation(summary = "删除数据库用户")
    @SysLog(name = "删除数据库用户")
    @DeleteMapping("users")
    public ReturnResult<Boolean> deleteUser(@RequestBody DbUserDeleteDTO request) {
        if (request == null) {
            return ReturnResult.error("请求不能为空");
        }
        String user = safeUserIdent(request.username(), "用户名");
        String host = safeUserHost(request.host());
        if (user.isEmpty()) {
            return ReturnResult.error("用户名不能为空");
        }
        String sql = "DROP USER '" + user + "'@'" + host + "'";
        return callDb("删除数据库用户", () -> {
            try (java.sql.Connection conn = datasourceService.connection(request.datasourceId());
                 java.sql.Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
                return true;
            }
        });
    }

    /**
     * 查询用户权限：{@code SHOW GRANTS FOR 'x'@'h'}。
     *
     * @param request 请求：datasourceId + username + host
     * @return 权限语句清单（supported=false 表示该内核不支持）
     */
    @Operation(summary = "查询数据库用户权限")
    @PostMapping("users/grants")
    public ReturnResult<java.util.Map<String, Object>> userGrants(@RequestBody DbUserDeleteDTO request) {
        if (request == null) {
            return ReturnResult.error("请求不能为空");
        }
        String user = safeUserIdent(request.username(), "用户名");
        String host = safeUserHost(request.host());
        if (user.isEmpty()) {
            return ReturnResult.error("用户名不能为空");
        }
        String sql = "SHOW GRANTS FOR '" + user + "'@'" + host + "'";
        return callDb("查询数据库用户权限", () -> {
            try (java.sql.Connection conn = datasourceService.connection(request.datasourceId());
                 java.sql.Statement stmt = conn.createStatement();
                 java.sql.ResultSet rs = stmt.executeQuery(sql)) {
                java.util.List<String> grants = new java.util.ArrayList<>();
                while (rs.next()) {
                    String line = rs.getString(1);
                    if (line != null) {
                        grants.add(line.trim());
                    }
                }
                java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
                result.put("supported", true);
                result.put("grants", grants);
                return result;
            }
        });
    }

    /**
     * 新建/修改用户的公共实现：MySQL 系 {@code CREATE/ALTER USER} + GRANT/REVOKE。
     *
     * <p>用户名经单引号转义、主机经白名单校验后才拼进 SQL（防注入）；
     * 密码只在非空时写 {@code IDENTIFIED BY}（空=不设/不改）。</p>
     *
     * @param request 请求
     * @param update  true 修改、false 新建
     * @return 是否成功
     */
    private ReturnResult<Boolean> saveUserInternal(DbUserSaveDTO request, boolean update) {
        if (request == null) {
            return ReturnResult.error("请求不能为空");
        }
        String user = safeUserIdent(request.username(), "用户名");
        String host = safeUserHost(request.host());
        if (user.isEmpty()) {
            return ReturnResult.error("用户名不能为空");
        }
        String pass = request.password() == null ? "" : request.password();
        return callDb("保存数据库用户", () -> {
            try (java.sql.Connection conn = datasourceService.connection(request.datasourceId());
                 java.sql.Statement stmt = conn.createStatement()) {
                java.util.List<String> statements = new java.util.ArrayList<>();
                String quoted = "'" + user + "'@'" + host + "'";
                if (update) {
                    // 修改：只重建要发的语句。密码非空才 ALTER 密码，避免空密码覆盖原有认证
                    StringBuilder alter = new StringBuilder("ALTER USER " + quoted);
                    if (!pass.isEmpty()) {
                        alter.append(" IDENTIFIED BY '").append(escapeSingle(pass)).append("'");
                    }
                    statements.add(alter.toString());
                } else {
                    StringBuilder create = new StringBuilder("CREATE USER " + quoted);
                    if (!pass.isEmpty()) {
                        create.append(" IDENTIFIED BY '").append(escapeSingle(pass)).append("'");
                    }
                    statements.add(create.toString());
                }
                // 权限：全量走 GRANT（GRANT 幂等 —— 重复授予同一权限不报错）。
                java.util.List<String> grants = request.grants() == null
                        ? java.util.List.of() : request.grants();
                if (!grants.isEmpty()) {
                    statements.add("GRANT " + String.join(", ", grants) + " ON *.* TO " + quoted);
                }
                // 超级权限是独立维度：MySQL 里 GRANT SUPER ON *.* 单独一条
                if (Boolean.TRUE.equals(request.superUser())) {
                    statements.add("GRANT SUPER ON *.* TO " + quoted);
                }
                for (String statement : statements) {
                    try (java.sql.Statement s = conn.createStatement()) {
                        s.execute(statement);
                    }
                }
                return true;
            }
        });
    }

    /**
     * MySQL/MariaDB：{@code mysql.user} 用户清单。
     */
    private java.util.List<DbUserVO> listMySqlUsers(java.sql.Connection conn) throws java.sql.SQLException {
        java.util.List<DbUserVO> out = new java.util.ArrayList<>();
        String sql = "SELECT User, Host, ssl_type, "
                + "max_questions, max_updates, max_connections, max_user_connections, "
                + "Super_priv FROM mysql.user ORDER BY User, Host";
        try (java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String name = rs.getString("User");
                String host = rs.getString("Host");
                if (name == null) {
                    continue;
                }
                String superv = rs.getString("Super_priv");
                out.add(new DbUserVO(
                        name.trim(),
                        host == null ? "" : host.trim(),
                        rs.getString("ssl_type"),
                        longOrNull(rs, "max_questions"),
                        longOrNull(rs, "max_updates"),
                        longOrNull(rs, "max_connections"),
                        longOrNull(rs, "max_user_connections"),
                        superv != null && "Y".equalsIgnoreCase(superv.trim())));
            }
        }
        return out;
    }

    /**
     * 用户名白名单校验：字母/数字/下划线/$/点，最长 64 字符。
     */
    private static String safeUserIdent(String name, String label) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim();
        if (!trimmed.matches("[A-Za-z0-9_$.]{1,64}")) {
            throw new IllegalArgumentException(label + "只能由字母、数字、下划线、$ 和点组成，且最长 64 字符");
        }
        return trimmed;
    }

    /**
     * 主机白名单校验：字母/数字/点/冒号/下划线/百分号/横杠，最长 255 字符。
     */
    private static String safeUserHost(String host) {
        if (host == null || host.isBlank()) {
            return "%";
        }
        String trimmed = host.trim();
        if (!trimmed.matches("[A-Za-z0-9.:_%-]{1,255}")) {
            throw new IllegalArgumentException("主机名不合法");
        }
        return trimmed;
    }

    /**
     * SQL 单引号转义（MySQL 用两个单引号表示一个）。
     */
    private static String escapeSingle(String text) {
        return text.replace("'", "''");
    }

    /**
     * MySQL/MariaDB：{@code performance_schema.events_statements_summary_by_digest}。
     *
     * <p>取平均耗时前 50 条；performance_schema 表在绝大多数发行版默认开启且对所有
     * 会话可读。读不到（5.5 及以下、被显式关闭、权限收紧）抛 SQL 异常，
     * 由 callDb 转成可读错误。</p>
     */
    private java.util.List<DbSlowQueryVO> listMySqlSlowQueries(java.sql.Connection conn) throws java.sql.SQLException {
        java.util.List<DbSlowQueryVO> out = new java.util.ArrayList<>();
        String sql = "SELECT digest_text, "
                + "ROUND(avg_timer_wait / 1000000000) AS avg_ms, "
                + "ROUND(max_timer_wait / 1000000000) AS max_ms, "
                + "count_star AS calls, last_seen "
                + "FROM performance_schema.events_statements_summary_by_digest "
                + "WHERE digest_text IS NOT NULL AND avg_timer_wait > 0 "
                + "ORDER BY avg_timer_wait DESC LIMIT 50";
        try (java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String lastSeen = rs.getTimestamp("last_seen") == null
                        ? null : rs.getTimestamp("last_seen").toLocalDateTime().toString();
                out.add(new DbSlowQueryVO(
                        rs.getString("digest_text"),
                        longOrNull(rs, "avg_ms"),
                        longOrNull(rs, "max_ms"),
                        rs.getLong("calls"),
                        lastSeen));
            }
        }
        return out;
    }

    /**
     * PostgreSQL：{@code pg_stat_statements}。
     *
     * <p>依赖扩展已加载；未加载（或用不了 read 权限）抛异常由 callDb 转错。</p>
     */
    private java.util.List<DbSlowQueryVO> listPostgresSlowQueries(java.sql.Connection conn) throws java.sql.SQLException {
        java.util.List<DbSlowQueryVO> out = new java.util.ArrayList<>();
        String sql = "SELECT query, ROUND(mean_exec_time)::bigint AS avg_ms, "
                + "ROUND(max_exec_time)::bigint AS max_ms, calls, last_planning_time AS last_seen "
                + "FROM pg_stat_statements "
                + "WHERE mean_exec_time > 0 ORDER BY mean_exec_time DESC LIMIT 50";
        try (java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                out.add(new DbSlowQueryVO(
                        rs.getString("query"),
                        rs.getLong("avg_ms"),
                        rs.getLong("max_ms"),
                        rs.getLong("calls"),
                        null));
            }
        }
        return out;
    }

    /**
     * MySQL/MariaDB：{@code SHOW PROCESSLIST}。
     */
    private java.util.List<DbProcessVO> listMySqlProcesses(java.sql.Connection conn) throws java.sql.SQLException {
        java.util.List<DbProcessVO> out = new java.util.ArrayList<>();
        try (java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery("SHOW PROCESSLIST")) {
            java.sql.ResultSetMetaData meta = rs.getMetaData();
            while (rs.next()) {
                out.add(new DbProcessVO(
                        rs.getString("Id"),
                        rs.getString("User"),
                        rs.getString("Host"),
                        rs.getString("db"),
                        rs.getString("Command"),
                        longOrNull(rs, "Time"),
                        rs.getString("Info"),
                        null));
            }
        }
        return out;
    }

    /**
     * PostgreSQL：{@code pg_stat_activity}。
     */
    private java.util.List<DbProcessVO> listPostgresProcesses(java.sql.Connection conn) throws java.sql.SQLException {
        java.util.List<DbProcessVO> out = new java.util.ArrayList<>();
        String sql = "SELECT pid, usename, coalesce(application_name,''), "
                + "coalesce(client_addr::text,''), state, extract(epoch from now()-query_start)::bigint, query "
                + "FROM pg_stat_activity";
        try (java.sql.Statement stmt = conn.createStatement();
             java.sql.ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                out.add(new DbProcessVO(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        rs.getString(5),
                        rs.getLong(6),
                        rs.getString(7),
                        null));
            }
        }
        return out;
    }

    /**
     * 读取数据源 JDBC URL（用于内核判断）。
     */
    private String jdbcUrlOf(Long datasourceId) {
        return datasourceService.jdbcInfo(datasourceId).url();
    }

    private static Long longOrNull(java.sql.ResultSet rs, String column) {
        try {
            long v = rs.getLong(column);
            return rs.wasNull() ? null : v;
        } catch (java.sql.SQLException e) {
            return null;
        }
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }


    // ==================== 数据库表级编辑（单元格 / 索引 / 清空表 / 列） ====================

    /**
     * 允许通过 DDL 拼接的列类型白名单。
     *
     * @deprecated 类型白名单已移到 {@link DbColumnDef#SUPPORTED_TYPES}，
     *     并且扩展到 40 余种（含 ENUM / SET / JSON / 日期时间 / 二进制等）。本常量不再使用。
     */
    @Deprecated
    private static final java.util.Set<String> SAFE_COLUMN_TYPES =
            java.util.Set.of("VARCHAR", "INT", "BIGINT", "TEXT", "DATETIME", "DECIMAL", "FLOAT", "DOUBLE", "BOOLEAN", "BLOB");

    /** 可抛受检异常的 Supplier，配合 callDb 把 SQLException 转成可读错误 */
    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    /**
     * 把 SQL 底层报错翻译成面向用户的业务说明。
     *
     * <p>{@code SQLException.getMessage()} 返回的是 MySQL 原文，例如
     * "Field 'id' doesn't have a default value" 或 "Duplicate entry 'x' for key 'PRIMARY'"。
     * 直接拼进信封返回，用户看不懂也不知道怎么改。这里按已知模式映射成
     * 一句能看懂的中文提示，映射不到的原样保留。</p>
     */
    private static String describeSqlError(String raw) {
        if (raw == null || raw.isEmpty()) return "操作失败";
        String msg = raw.trim();
        // Field 'xxx' doesn't have a default value
        Matcher m = Pattern.compile("Field '([^']+)' doesn't have a default value", Pattern.CASE_INSENSITIVE).matcher(msg);
        if (m.find()) return "列「" + m.group(1) + "」没有默认值，请填写该列的值或确认该列支持自增";
        // Column 'xxx' cannot be null
        m = Pattern.compile("Column '([^']+)' cannot be null", Pattern.CASE_INSENSITIVE).matcher(msg);
        if (m.find()) return "列「" + m.group(1) + "」不能为空，请填写后再保存";
        // Data too long for column 'xxx'
        m = Pattern.compile("Data too long for column '([^']+)'", Pattern.CASE_INSENSITIVE).matcher(msg);
        if (m.find()) return "列「" + m.group(1) + "」的值超出长度限制，请缩短后重试";
        // Unknown column 'xxx' in 'field list'
        m = Pattern.compile("Unknown column '([^']+)' in 'field list'", Pattern.CASE_INSENSITIVE).matcher(msg);
        if (m.find()) return "表中不存在列「" + m.group(1) + "」，可能已被删除或表结构已变更";
        // Duplicate entry 'xxx' for key 'xxx'
        m = Pattern.compile("Duplicate entry '([^']+)' for key '([^']+)'", Pattern.CASE_INSENSITIVE).matcher(msg);
        if (m.find()) return "「" + m.group(2) + "」已存在值为「" + m.group(1) + "」的记录，不能重复";
        // Table 'xxx' doesn't exist
        m = Pattern.compile("Table '([^']+)' doesn't exist", Pattern.CASE_INSENSITIVE).matcher(msg);
        if (m.find()) return "表「" + m.group(1) + "」不存在，可能已被删除或库名有误";
        return msg;
    }

    /**
     * 执行一次表级 DML/DDL 操作，把底层异常换成可读原因而不是 500 堆栈。
     */
    private <T> ReturnResult<T> callDb(String action, ThrowingSupplier<T> supplier) {
        try {
            return ReturnResult.ok(supplier.get());
        } catch (Exception e) {
            log.error("[db] {}失败", action, e);
            return ReturnResult.error(action + "失败: " + describeSqlError(e.getMessage()));
        }
    }

    /** 校验并规整 SQL 标识符，非法直接抛异常，由 callDb 转成可读错误 */
    private static String safeIdent(String name, String label) {
        try {
            return SqlName.checkSimple(name == null ? null : name.trim(), label);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(label + "只能由字母、数字和下划线组成，且最长 64 字符: " + name, ex);
        }
    }

    /** 判断数据源是否为 InfluxDB 类型 */
    private boolean isInfluxdbType(Long datasourceId) {
        try {
            var vo = datasourceService.get(datasourceId);
            return vo != null && vo.getData() != null
                    && "influxdb".equalsIgnoreCase(vo.getData().type());
        } catch (Exception e) {
            return false;
        }
    }

    private static void requireTable(java.sql.Connection conn, String table) throws java.sql.SQLException {
        java.sql.DatabaseMetaData metadata = conn.getMetaData();
        // 依次尝试 (catalog,schema) / (catalog,null) / (null,schema) / (null,null)：
        // MySQL 的 getSchema() 常为空串，配 useInformationSchema 时只传 catalog 才能命中
        if (!hasTable(metadata, conn.getCatalog(), conn.getSchema(), table)
                && !hasTable(metadata, conn.getCatalog(), null, table)
                && !hasTable(metadata, null, conn.getSchema(), table)
                && !hasTable(metadata, null, null, table)) {
            throw new java.sql.SQLException("表不存在: " + table);
        }
    }

    private static boolean hasTable(java.sql.DatabaseMetaData metadata, String catalog,
                                   String schema, String table) throws java.sql.SQLException {
        try (java.sql.ResultSet rs = metadata.getTables(catalog, schema, table, new String[]{"TABLE", "VIEW"})) {
            while (rs.next()) {
                if (table.equalsIgnoreCase(rs.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void requireColumn(java.sql.Connection conn, String table, String column) throws java.sql.SQLException {
        requireTable(conn, table);
        java.sql.DatabaseMetaData metadata = conn.getMetaData();
        if (!hasColumn(metadata, conn.getCatalog(), conn.getSchema(), table, column)
                && !hasColumn(metadata, conn.getCatalog(), null, table, column)
                && !hasColumn(metadata, null, conn.getSchema(), table, column)
                && !hasColumn(metadata, null, null, table, column)) {
            throw new java.sql.SQLException("列不存在: " + table + "." + column);
        }
    }

    private static boolean hasColumn(java.sql.DatabaseMetaData metadata, String catalog,
                                     String schema, String table, String column) throws java.sql.SQLException {
        try (java.sql.ResultSet rs = metadata.getColumns(catalog, schema, table, column)) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void requireMissingColumn(java.sql.Connection conn, String table, String column)
            throws java.sql.SQLException {
        requireTable(conn, table);
        java.sql.DatabaseMetaData metadata = conn.getMetaData();
        if (hasColumn(metadata, conn.getCatalog(), conn.getSchema(), table, column)
                || hasColumn(metadata, null, null, table, column)) {
            throw new java.sql.SQLException("列已存在: " + table + "." + column);
        }
    }

    private static void requireIndex(java.sql.Connection conn, String table, String index) throws java.sql.SQLException {
        requireTable(conn, table);
        try (java.sql.ResultSet rs = conn.getMetaData().getIndexInfo(conn.getCatalog(), conn.getSchema(), table, false, true)) {
            while (rs.next()) {
                if (index.equalsIgnoreCase(rs.getString("INDEX_NAME"))) {
                    return;
                }
            }
        }
        throw new java.sql.SQLException("索引不存在: " + index);
    }


    /** 从请求体里取可选的 datasourceId，空串/0 统一归一为 null（走默认主数据源） */
    private static Long bodyDatasourceId(Map<String, Object> body) {
        Object v = body.get("datasourceId");
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        long id = Long.parseLong(String.valueOf(v));
        return id == 0 ? null : id;
    }

    /** 从请求体里取可选 database */
    private static String bodyDatabase(Map<String, Object> body) {
        Object v = body.get("database");
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        return String.valueOf(v);
    }

    /**
     * 更新单元格：按主键等值条件定位，PreparedStatement 绑定值防注入。
     *
     * <p>安全豁免：表名、目标列和主键列均经标识符白名单与 JDBC 元数据双重校验。</p>
     */
    @Operation(summary = "更新单元格")
    @SysLog(name = "编辑单元格")
    @PutMapping("table/{tableName}/cell")
    public ReturnResult<Integer> updateCell(@PathVariable String tableName,
                                         @RequestBody Map<String, Object> body,
                                         @RequestParam(required = false) Long datasourceId,
                                         @RequestParam(required = false) String database) {
        String t = safeIdent(tableName, "表名");
        String column = safeIdent(String.valueOf(body.get("column")), "列名");
        Object newValue = body.get("newValue");
        @SuppressWarnings("unchecked")
        Map<String, Object> primaryKey = (Map<String, Object>) body.getOrDefault("primaryKey", java.util.Collections.emptyMap());
        if (primaryKey == null || primaryKey.isEmpty()) {
            return ReturnResult.error("缺少主键定位条件 primaryKey");
        }
        // 标识符引用要按方言拼（MySQL 反引号 / 其余双引号），而方言只有拿到连接才知道，
        // 所以这里只收集列名，SQL 留到 update 闭包里拼 —— 早拼死会让 DuckDB /
        // PostgreSQL / SQLite 上的「改单元格」直接语法错误。
        List<Object> params = new ArrayList<>();
        params.add(newValue);
        List<String> pkColumns = new ArrayList<>(primaryKey.size());
        for (Map.Entry<String, Object> e : primaryKey.entrySet()) {
            pkColumns.add(safeIdent(e.getKey(), "主键列"));
            params.add(e.getValue());
        }
        Long dsId = queryOrBodyDatasourceId(datasourceId, body);
        String db = queryOrBodyDatabase(database, body);
        Object txIdRaw = body.get("txId");
        String txId = txIdRaw == null ? null : String.valueOf(txIdRaw);
        boolean inTx = txId != null && !txId.isBlank();
        // 「事务内更新」的共用实现：连接由调用方给（要么是新建的，要么是事务持有的），
        // 所以校验、base64 解码、绑定三段逻辑只能写一次。
        java.util.function.Function<java.sql.Connection, Integer> update = conn -> {
            try {
                requireTable(conn, t);
                requireColumn(conn, t, column);
                for (Map.Entry<String, Object> entry : primaryKey.entrySet()) {
                    requireColumn(conn, t, safeIdent(entry.getKey(), "主键列"));
                }
                // 二进制列必须把 base64 **解成字节**再绑定。
                // 不解的话内核会把 base64 的 ASCII 文本原样存进去：
                // 界面看着「保存成功」，实际数据已经被改坏，
                // 而且再次读出来是一段「base64 文本的 base64」，越改越离谱。
                if (newValue instanceof String text && isBinaryColumn(conn, t, column)) {
                    params.set(0, decodeBase64(text));
                }
                // ClickHouse 没有 UPDATE ... SET ... WHERE：改行必须写成
                // ALTER TABLE ... UPDATE ... WHERE（mutation），且默认异步，
                // 要带 SETTINGS mutations_sync = 2 才会等数据真正落盘，
                // 否则 executeUpdate 立刻返回、界面刷新还是旧值。
                boolean clickhouse = DbDialectSupport.isClickHouse(conn);
                StringBuilder sql = new StringBuilder(clickhouse ? "ALTER TABLE " : "UPDATE ")
                        .append(DbDialectSupport.quoteIdent(conn, t, "表名"))
                        .append(clickhouse ? " UPDATE " : " SET ")
                        .append(DbDialectSupport.quoteIdent(conn, column, "列名"))
                        .append(" = ? WHERE ");
                for (int i = 0; i < pkColumns.size(); i++) {
                    if (i > 0) {
                        sql.append(" AND ");
                    }
                    sql.append(DbDialectSupport.quoteIdent(conn, pkColumns.get(i), "主键列")).append(" = ?");
                }
                if (clickhouse) {
                    sql.append(" SETTINGS mutations_sync = 2");
                }
                try (java.sql.PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                    for (int i = 0; i < params.size(); i++) {
                        ps.setObject(i + 1, params.get(i));
                    }
                    int affected = ps.executeUpdate();
                    // mutation 不上报影响行数（恒为 0），但语句成功即代表改动已同步落盘；
                    // 返回 0 会让前端显示「影响 0 行」像是没改，这里按 1 报。
                    return clickhouse && affected == 0 ? 1 : affected;
                }
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        };
        if (inTx) {
            return callDb("更新单元格", () -> dbTxRegistry().require(txId).run(update));
        }
        return callDb("更新单元格", () -> {
            try (java.sql.Connection conn = datasourceService.connection(dsId);
                 SchemaScope scope = useSchema(conn, db)) {
                return update.apply(conn);
            }
        });
    }

    /** 二进制列的类型名（小写比较） */
    private static final Set<String> BINARY_TYPES = Set.of(
            "binary", "varbinary", "blob", "tinyblob", "mediumblob", "longblob", "bytea");

    /**
     * 判断某列是不是二进制类型。
     *
     * <p>走 {@link java.sql.DatabaseMetaData#getColumns} 而不在 SQL 里查
     * {@code information_schema}：前者对各内核通用，
     * 且与 {@link #requireColumn} 的存在性校验走同一套元数据口径。</p>
     *
     * @param conn   连接
     * @param table  表名
     * @param column 列名
     * @return true=二进制列
     */
    private static boolean isBinaryColumn(java.sql.Connection conn, String table, String column) {
        java.sql.ResultSet rs = null;
        try {
            java.sql.DatabaseMetaData md = conn.getMetaData();
            String catalog = conn.getCatalog();
            String schema = conn.getSchema();
            // 与 hasTable 同样的四种 (catalog,schema) 组合，逐个试
            for (int[] shape : new int[][] {{0, 1}, {0, 0}, {1, 1}, {1, 0}}) {
                rs = md.getColumns(shape[0] == 0 ? catalog : null,
                        shape[1] == 1 ? schema : null, table, column);
                if (rs.next()) {
                    String type = String.valueOf(rs.getString("TYPE_NAME")).toLowerCase(java.util.Locale.ROOT);
                    return BINARY_TYPES.contains(type) || type.contains("blob") || type.contains("binary");
                }
                rs.close();
                rs = null;
            }
        } catch (java.sql.SQLException ignored) {
            // 读不到类型就当不是二进制列：走原有行为，不因元数据缺失而拒绝写入
        } finally {
            if (rs != null) {
                try {
                    rs.close();
                } catch (java.sql.SQLException ignored) {
                    // 关闭失败无需处理
                }
            }
        }
        return false;
    }

    /**
     * 解 base64 为字节。
     *
     * @param text base64 文本（允许含空白）
     * @return 字节
     * @throws IllegalArgumentException 不是合法 base64
     */
    private static byte[] decodeBase64(String text) {
        try {
            return java.util.Base64.getDecoder().decode(text.replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("内容不是合法的 base64（无法写入二进制列）: " + e.getMessage());
        }
    }

    /**
     * 读取表索引列表（经 DatabaseMetaData.getIndexInfo）。
     */
    @Operation(summary = "获取表索引")
    @GetMapping("table/{tableName}/indexes")
    public ReturnResult<java.util.List<Map<String, Object>>> getTableIndexes(
            @PathVariable String tableName,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        String t = safeIdent(tableName, "表名");
        return callDb("获取表索引", () -> {
            try (java.sql.Connection conn = datasourceService.connection(datasourceId);
                 SchemaScope scope = useSchema(conn, database)) {
                requireTable(conn, t);
                String catalog = conn.getCatalog();

                String schema = conn.getSchema();
                java.util.List<Map<String, Object>> list = new ArrayList<>();
                try (java.sql.ResultSet rs = conn.getMetaData().getIndexInfo(catalog, schema, t, false, false)) {
                    while (rs.next()) {
                        String idxName = rs.getString("INDEX_NAME");
                        if (idxName == null || "PRIMARY".equalsIgnoreCase(idxName)) {
                            continue;
                        }
                        Map<String, Object> row = new java.util.LinkedHashMap<>();
                        row.put("indexName", idxName);
                        row.put("columnName", rs.getString("COLUMN_NAME"));
                        row.put("nonUnique", rs.getBoolean("NON_UNIQUE"));
                        row.put("indexType", rs.getInt("TYPE"));
                        row.put("seqInIndex", rs.getShort("ORDINAL_POSITION"));
                        list.add(row);
                    }
                }
                return list;
            }
        });
    }

    /**
     * 新建索引（支持联合列与唯一索引）。
     *
     * <p>安全豁免：索引名、表名和索引列经标识符白名单及 JDBC 元数据校验后进入 MySQL 底层方言 DDL。</p>
     */
    @Operation(summary = "新建索引")
    @SysLog(name = "新建索引")
    @PostMapping("table/{tableName}/index")
    public ReturnResult<Boolean> createIndex(@PathVariable String tableName,
                                            @RequestBody Map<String, Object> body,
                                            @RequestParam(required = false) Long datasourceId,
                                            @RequestParam(required = false) String database) {
        String t = safeIdent(tableName, "表名");
        String indexName = safeIdent(String.valueOf(body.getOrDefault("indexName", "idx")), "索引名");
        @SuppressWarnings("unchecked")
        List<String> columns = (List<String>) body.get("columns");
        boolean unique = Boolean.TRUE.equals(body.get("unique"));
        boolean primary = Boolean.TRUE.equals(body.get("primary"));
        String indexType = body.get("indexType") == null ? null : String.valueOf(body.get("indexType"));
        List<Integer> prefixes = intListOrNull(body.get("prefixes"));
        if (columns == null || columns.isEmpty()) {
            return ReturnResult.error("请至少选择一列");
        }
        Long dsId = queryOrBodyDatasourceId(datasourceId, body);
        String db = queryOrBodyDatabase(database, body);
        return callDb("新建索引", () -> {
            try (java.sql.Connection conn = datasourceService.connection(dsId);
                 SchemaScope scope = useSchema(conn, db)) {
                requireTable(conn, t);
                for (String indexColumn : columns) {
                    requireColumn(conn, t, safeIdent(indexColumn, "索引列"));
                }
                // 列序即复合索引的列序，原样透传（不排序），否则索引前缀语义会变
                DbTableDialect
                        .createIndex(conn, t, indexName, columns, prefixes, unique, indexType, primary);
                return Boolean.TRUE;
            }
        });

    }

    /**
     * 把请求里的整数数组解析出来。
     *
     * @param value 原始值，可为空
     * @return 整数列表；输入为空返回 null
     */
    private static List<Integer> intListOrNull(Object value) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        List<Integer> result = new java.util.ArrayList<>(list.size());
        for (Object item : list) {
            result.add(intOrNull(item));
        }
        return result;
    }

    /**
     * 删除索引。
     *
     * <p>安全豁免：索引名和表名经标识符白名单及 JDBC 元数据校验。</p>
     */
    @Operation(summary = "删除索引")
    @SysLog(name = "删除索引")
    @DeleteMapping("table/{tableName}/index/{indexName}")
    public ReturnResult<Boolean> dropIndex(
            @PathVariable String tableName,
            @PathVariable String indexName,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        String t = safeIdent(tableName, "表名");
        String idx = safeIdent(indexName, "索引名");
        String sql = "DROP INDEX " + SqlName.quoteMysql(idx, "索引名")
                + " ON " + SqlName.quoteMysql(t, "表名");
        return callDb("删除索引", () -> {
            try (java.sql.Connection conn = datasourceService.connection(datasourceId);
                 SchemaScope scope = useSchema(conn, database)) {
                requireIndex(conn, t, idx);
                try (java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.executeUpdate();
                }
                return Boolean.TRUE;
            }
        });

    }

    // ==================== 数据导入 / 导出（对齐 Navicat 导入导出向导） ====================

    /**
     * 导入向导第 2 步：上传文件并立刻解析一次。
     *
     * <p>返回 {@code token}，后续第 3～7 步都带着它，不再重传文件。响应里同时带
     * 解析结果（列名 / 预览行 / Excel 工作表清单），因此前端选完文件就能直接显示
     * 「源文件内容」，用户改一次分隔符再调 {@code import/parse} 重新解析即可。</p>
     *
     * @param file     上传的数据文件
     * @param optionJson 向导选项 JSON（可只带 format / encoding）
     * @return 令牌 + 解析结果
     */
    @Operation(summary = "导入向导：上传文件并解析")
    @PostMapping("import/upload")
    public ReturnResult<Map<String, Object>> importUpload(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "option", required = false) String optionJson) {
        return callDb("上传导入文件", () -> {
            com.chua.starter.datasourcehub.support.dbconsole.DbImportDTO option = parseImportOption(optionJson);
            if (option == null) {
                option = emptyImportOption();
            }
            option = withFileName(option, file.getOriginalFilename());
            byte[] bytes = file.getBytes();
            var entry = com.chua.starter.datasourcehub.support.dbconsole.DbImportUploadStore
                    .save(file.getOriginalFilename(), bytes);
            var parsed = com.chua.starter.datasourcehub.support.dbconsole.DbImportSource.parse(bytes, option);
            return describeImport(entry.token(), parsed, option);
        });
    }

    /**
     * 导入向导第 3 步：带新选项重新解析已上传的文件。
     *
     * <p>用户每改一次「字段名称行 / 分隔符 / 编码」，前端就调一次本接口刷新预览。
     * 不重传文件，因此调再多次也不会把大文件反复搬运。</p>
     *
     * @param request 令牌 + 向导选项
     * @return 解析结果
     */
    @Operation(summary = "导入向导：重新解析")
    @PostMapping("import/parse")
    public ReturnResult<Map<String, Object>> importParse(@RequestBody Map<String, Object> request) {
        return callDb("解析导入文件", () -> {
            com.chua.starter.datasourcehub.support.dbconsole.DbImportDTO option = importOptionFromBody(request);
            String token = option.token() == null ? "" : option.token();
            byte[] bytes = com.chua.starter.datasourcehub.support.dbconsole.DbImportUploadStore.read(token);
            var entry = com.chua.starter.datasourcehub.support.dbconsole.DbImportUploadStore.get(token);
            if (entry != null && (option.fileName() == null || option.fileName().isBlank())) {
                option = withFileName(option, entry.fileName());
            }
            var parsed = com.chua.starter.datasourcehub.support.dbconsole.DbImportSource.parse(bytes, option);
            return describeImport(token, parsed, option);
        });
    }

    /**
     * 导入向导第 4 步：按源列建议目标表结构。
     *
     * <p>「新建表」时前端需要一个默认表名与列清单。表名取源文件名（去掉后缀、清洗成
     * 合法标识符），列名取源列；这里只<b>建议</b>，用户在界面上仍可改。</p>
     *
     * @param request 令牌（可选）+ 源列名
     * @return 建议表名与列定义
     */
    @Operation(summary = "导入向导：建议新建表结构")
    @PostMapping("import/table-plan")
    public ReturnResult<Map<String, Object>> importTablePlan(@RequestBody Map<String, Object> request) {
        return callDb("生成建表建议", () -> {
            List<String> sourceColumns = stringList(request.get("sourceColumns"));
            String suggested = com.chua.starter.datasourcehub.support.dbconsole.DbImportDdl
                    .sanitize(stringValue(request.get("fileName")));
            // 去掉扩展名残留的点：sanitize 会把 "." 变成 "_"，这里只处理常见的 _csv 尾巴
            suggested = suggested.replaceAll("_(?i:csv|xlsx?|xlsm|txt|json|sql)$", "");
            // 中文文件名清洗后只剩一串下划线（「订单 明细.csv」→「_____」）。
            // 这种表名语法上合法，但用户在树里根本认不出是哪张表，退回固定名更好认。
            if (suggested.replace("_", "").isBlank()) {
                suggested = "import_table";
            }
            // 表名不能以数字开头已被 sanitize 处理；再兜一层「不能是纯关键字」没有意义 ——
            // 所有标识符都会加反引号引用。
            List<Map<String, Object>> columns = new java.util.ArrayList<>();
            List<String> names = com.chua.starter.datasourcehub.support.dbconsole.DbImportDdl.sanitizeAll(sourceColumns);
            for (int i = 0; i < sourceColumns.size(); i++) {
                columns.add(Map.of(
                        "name", names.get(i),
                        "source", sourceColumns.get(i) == null ? "" : sourceColumns.get(i)));
            }
            return Map.of("tableName", suggested, "columns", columns);
        });
    }

    /**
     * 导入向导第 6～7 步：执行导入（SSE 流式日志）。
     *
     * <p>事件类型与结构同步一致：{@code log}（逐条语句）、{@code progress}（进度）、
     * {@code phase}（阶段提示）、{@code done}（汇总）、{@code error}（早期失败）。
     * 用 {@code StreamingResponseBody} 而不是 {@code SseEmitter}：本项目在
     * Boot 4.1 / Spring 7 下 SseEmitter 的 data 会被静默丢弃（详见结构同步端点注释）。</p>
     *
     * @param request      令牌 + 完整向导选项
     * @param httpResponse HTTP 响应（用于写 Content-Type）
     * @return SSE 流
     */
    @Operation(summary = "导入向导：执行导入（SSE 流式）")
    @SysLog(name = "导入数据")
    @PostMapping(value = "import/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody importRun(
            @RequestBody Map<String, Object> request,
            jakarta.servlet.http.HttpServletResponse httpResponse) {
        httpResponse.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        httpResponse.setCharacterEncoding("UTF-8");
        return outputStream -> {
            java.io.PrintWriter out = new java.io.PrintWriter(
                    new java.io.OutputStreamWriter(outputStream, java.nio.charset.StandardCharsets.UTF_8));
            out.flush();
            runImport(out, request);
            out.flush();
        };
    }

    /**
     * 导入执行主体：解析 → 建表 → 写入，全程把进度写进 SSE 流。
     *
     * @param out     输出流
     * @param request 令牌 + 向导选项
     */
    private void runImport(java.io.PrintWriter out, Map<String, Object> request) {
        long startedAt = System.currentTimeMillis();
        try {
            com.chua.starter.datasourcehub.support.dbconsole.DbImportDTO option = importOptionFromBody(request);
            String token = option.token() == null ? "" : option.token();
            byte[] bytes = com.chua.starter.datasourcehub.support.dbconsole.DbImportUploadStore.read(token);
            String table = safeIdent(option.tableName(), "表名");
            var parsed = com.chua.starter.datasourcehub.support.dbconsole.DbImportSource.parse(bytes, option);
            if ("sql".equals(parsed.format())) {
                var result = executeSqlScript(
                        com.chua.starter.datasourcehub.support.dbconsole.DbTransferCodec.decode(bytes, option.encoding()),
                        option);
                writeImportDone(out, result, startedAt, table);
                return;
            }
            Map<String, String> mapping = option.columnMapping() == null ? Map.of() : option.columnMapping();
            if (mapping.isEmpty() && !Boolean.TRUE.equals(option.createTable())) {
                throw new IllegalArgumentException("请先在第 5 步配置字段映射");
            }
            var plan = new com.chua.starter.datasourcehub.support.dbconsole.DbImporter.ImportPlan(
                    table,
                    parsed.table().columns(),
                    mapping,
                    option.importMode(),
                    option.keyColumns(),
                    option.advanced(),
                    option.batchRows(),
                    Boolean.TRUE.equals(option.createTable()),
                    Boolean.TRUE.equals(option.truncateFirst()),
                    option.dateOptions());
            writeSseEvent(out, "phase", Map.of("message",
                    "开始导入 " + table + "（" + parsed.table().rows().size() + " 行，"
                            + option.importMode().label() + "）"));
            try (java.sql.Connection conn = datasourceService.connection(option.datasourceId());
                 SchemaScope scope = useSchema(conn, option.database())) {
                var result = com.chua.starter.datasourcehub.support.dbconsole.DbImporter.importRows(
                        conn, parsed.table().rows(), plan,
                        importProgress(out, parsed.table().rows().size()));
                // 这里刻意不丢弃 token：Excel 多工作表是「一张表一次 import/run」，
                // 同一次向导里第 2 张起还要用同一个 token 读文件。临时文件交给
                // DbImportUploadStore 的 TTL（60 分钟）与条数上限回收。
                writeImportDone(out, result, startedAt, table);
            }
        } catch (Exception e) {
            log.warn("[db] 导入失败: {}", e.getMessage(), e);
            writeSseEvent(out, "error", Map.of("message", e.getMessage() == null
                    ? e.getClass().getSimpleName() : e.getMessage()));
            writeSseEvent(out, "done", Map.of("total", 0, "inserted", 0, "updated", 0,
                    "skipped", 0, "deleted", 0, "errors", 0,
                    "elapsedMs", System.currentTimeMillis() - startedAt, "failed", true));
        }
    }

    /**
     * 把导入进度桥接成 SSE 事件。
     *
     * <p>逐行日志会让大文件产生几万条事件，前端渲染必然卡死。因此{@code onStatement}
     * 只<b>失败</b>时推送（用户只关心错在哪），成功语句汇总成每 200 行一条
     * {@code progress}。</p>
     *
     * @param out   输出流
     * @param total 总行数
     * @return 进度回调
     */
    private com.chua.starter.datasourcehub.support.dbconsole.DbImporter.Progress importProgress(
            java.io.PrintWriter out, int total) {
        int[] lastReported = {0};
        return new com.chua.starter.datasourcehub.support.dbconsole.DbImporter.Progress() {
            @Override
            public void onPhase(String text) {
                writeSseEvent(out, "phase", Map.of("message", text));
            }

            @Override
            public void onProgress(int done, int totalRows) {
                if (done - lastReported[0] < 200 && done < totalRows) {
                    return;
                }
                lastReported[0] = done;
                writeSseEvent(out, "progress", Map.of("done", done, "total", totalRows));
            }

            @Override
            public void onStatement(int no, String sql, boolean ok, int affected, String error) {
                if (ok) {
                    return;
                }
                writeSseEvent(out, "log", Map.of("no", no, "ok", false,
                        "affected", affected,
                        "error", error == null ? "" : error));
            }
        };
    }

    /**
     * 写一条导入完成事件。
     *
     * @param out       输出流
     * @param result    导入结果
     * @param startedAt 开始时间戳
     * @param table     目标表
     */
    private void writeImportDone(java.io.PrintWriter out,
                                 com.chua.starter.datasourcehub.support.dbconsole.DbImporter.ImportResult result,
                                 long startedAt, String table) {
        int failed = Math.max(0, result.totalRows() - result.inserted() - result.updated()
                - result.skipped() - result.deleted());
        writeSseEvent(out, "done", Map.of(
                "table", table,
                "total", result.totalRows(),
                "inserted", result.inserted(),
                "updated", result.updated(),
                "skipped", result.skipped(),
                "deleted", result.deleted(),
                "errors", result.errors().size(),
                "errorDetails", result.errors(),
                "elapsedMs", System.currentTimeMillis() - startedAt,
                "failed", false));
        // failed 与 errors 用同一份数据：前端只需要看 errors 是否 > 0
        if (failed > 0) {
            log.debug("[db] 导入 {} 有 {} 行未计入统计", table, failed);
        }
    }

    /**
     * 组装上传/解析接口的响应。
     *
     * @param token  令牌
     * @param parsed 解析结果
     * @param option 向导选项
     * @return 响应体
     */
    private Map<String, Object> describeImport(String token,
                                               com.chua.starter.datasourcehub.support.dbconsole.DbImportSource.Parsed parsed,
                                               com.chua.starter.datasourcehub.support.dbconsole.DbImportDTO option) {
        int previewSize = 100;
        var rows = parsed.table().rows();
        var preview = rows.size() <= previewSize ? rows : rows.subList(0, previewSize);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("token", token);
        body.put("format", parsed.format());
        body.put("columns", parsed.table().columns());
        body.put("rows", preview);
        body.put("rowCount", rows.size());
        body.put("previewSize", previewSize);
        body.put("delimiter", parsed.delimiter());
        body.put("encoding", parsed.encoding());
        body.put("sheets", parsed.sheets());
        body.put("activeSheet", parsed.activeSheet());
        body.put("warnings", parsed.warnings());
        body.put("excelAvailable", com.chua.starter.datasourcehub.support.dbconsole.DbImportExcel.available());
        body.put("fileName", option.fileName());
        return body;
    }

    /**
     * 从请求体构造导入选项。
     *
     * @param body 请求体
     * @return 选项
     */
    private com.chua.starter.datasourcehub.support.dbconsole.DbImportDTO importOptionFromBody(Map<String, Object> body) {
        Object option = body.get("option");
        if (option instanceof Map<?, ?> map) {
            try {
                return new com.fasterxml.jackson.databind.ObjectMapper()
                        .convertValue(map, com.chua.starter.datasourcehub.support.dbconsole.DbImportDTO.class);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("导入选项格式不正确: " + e.getMessage());
            }
        }
        // 兼容「选项平铺在请求体里」的调用方式
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .convertValue(body, com.chua.starter.datasourcehub.support.dbconsole.DbImportDTO.class);
    }

    /**
     * 空选项（只用来做格式探测）。
     *
     * @return 选项
     */
    private static com.chua.starter.datasourcehub.support.dbconsole.DbImportDTO emptyImportOption() {
        return new com.chua.starter.datasourcehub.support.dbconsole.DbImportDTO(
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    /**
     * 补文件名。
     *
     * @param option   选项
     * @param fileName 文件名
     * @return 新选项
     */
    private static com.chua.starter.datasourcehub.support.dbconsole.DbImportDTO withFileName(
            com.chua.starter.datasourcehub.support.dbconsole.DbImportDTO option, String fileName) {
        return new com.chua.starter.datasourcehub.support.dbconsole.DbImportDTO(
                fileName == null ? option.fileName() : fileName,
                option.format(), option.delimiter(), option.hasHeader(), option.encoding(),
                option.database(), option.tableName(), option.columnMapping(), option.truncateFirst(),
                option.onConflict(), option.batchSize(), option.datasourceId(), option.sheetName(),
                option.recordDelimiter(), option.textQualifier(), option.fieldNamesLine(),
                option.firstDataLine(), option.lastDataLine(), option.decimalSymbol(),
                option.dateOrder(), option.dateTimeOrder(), option.dateSeparator(),
                option.yearSeparator(), option.timeSeparator(), option.mode(),
                option.keyColumns(), option.extendedInsert(), option.maxStatementKb(),
                option.nullForEmpty(), option.ignoreForeignKey(), option.continueOnError(),
                option.createTable(), option.token());
    }

    /**
     * 取字符串。
     *
     * @param value 原值
     * @return 字符串；null 归一为空串
     */
    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 取字符串列表。
     *
     * @param value 原值
     * @return 字符串列表
     */
    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> out = new java.util.ArrayList<>(list.size());
            for (Object item : list) {
                out.add(item == null ? "" : String.valueOf(item));
            }
            return out;
        }
        return List.of();
    }

    /**
     * 导入预览：解析上传文件、回显列名与前若干行，供用户在向导第一步确认解析结果。
     *
     * <p>只解析不落库，因此不会对目标库产生任何副作用。</p>
     *
     * @param file     上传的数据文件
     * @param format   格式：auto / csv / tsv / json
     * @param hasHeader 首行是否为表头
     * @param encoding  字符集名，可为空
     * @return 预览结果
     */
    @Operation(summary = "导入预览（解析文件但不写库）")
    @PostMapping("import/preview")
    public ReturnResult<com.chua.starter.datasourcehub.support.dbconsole.DbImportPreviewVO> previewImportFile(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(required = false) String format,
            @RequestParam(required = false, defaultValue = "true") boolean hasHeader,
            @RequestParam(required = false) String encoding) {
        int previewSize = 50;
        return callDb("解析导入文件", () -> {
            byte[] bytes = file.getBytes();
            String text = com.chua.starter.datasourcehub.support.dbconsole.DbTransferCodec.decode(bytes, encoding);
            String resolvedFormat = resolveImportFormat(format, file.getOriginalFilename(), text);
            if ("sql".equals(resolvedFormat)) {
                int statements = com.chua.starter.datasourcehub.support.dbconsole.DbTransferCodec.splitSqlStatements(text).size();
                return new com.chua.starter.datasourcehub.support.dbconsole.DbImportPreviewVO(
                        List.of(), List.of(), statements, 0, ";", encoding == null ? "UTF-8" : encoding);
            }
            char delimiter = resolveDelimiter(resolvedFormat, text);
            var table = "json".equals(resolvedFormat)
                    ? com.chua.starter.datasourcehub.support.dbconsole.DbTransferCodec.parseJson(text, List.of())
                    : com.chua.starter.datasourcehub.support.dbconsole.DbTransferCodec.parseDelimited(text, delimiter, hasHeader);
            var rows = table.rows();
            var preview = rows.size() <= previewSize ? rows : rows.subList(0, previewSize);
            return new com.chua.starter.datasourcehub.support.dbconsole.DbImportPreviewVO(
                    table.columns(), preview, rows.size(), previewSize,
                    delimiter == '\t' ? "\\t" : String.valueOf(delimiter),
                    encoding == null ? "UTF-8" : encoding);
        });
    }

    /**
     * 执行导入：按字段映射把上传文件写入目标表。
     *
     * <p>安全豁免：目标表名与参与写入的列名经标识符白名单与 JDBC 元数据校验后引用；
     * 单元格值全部走 PreparedStatement 参数绑定。字段映射为空时报错而非静默按表结构硬套。</p>
     *
     * @param file   上传的数据文件
     * @param option 导入选项（格式 / 表头 / 映射 / 冲突策略 / 是否先清空）
     * @return 导入统计
     */
    @Operation(summary = "执行数据导入")
    @SysLog(name = "导入数据")
    @PostMapping("import")
    public ReturnResult<com.chua.starter.datasourcehub.support.dbconsole.DbImporter.ImportResult> importDataFile(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "option", required = false) String optionJson) {
        return callDb("导入数据", () -> {
            com.chua.starter.datasourcehub.support.dbconsole.DbImportDTO option = parseImportOption(optionJson);
            if (option == null) {
                throw new IllegalArgumentException("缺少导入选项 option");
            }
            String table = safeIdent(option.tableName(), "表名");
            String format = resolveImportFormat(option.format(), file.getOriginalFilename(), null);
            byte[] bytes = file.getBytes();
            String text = com.chua.starter.datasourcehub.support.dbconsole.DbTransferCodec.decode(bytes, option.encoding());
            if ("sql".equals(format)) {
                return executeSqlScript(text, option);
            }
            var options = new com.chua.starter.datasourcehub.support.dbconsole.DbImporter.ImportOptions(
                    table,
                    option.sourceColumns().isEmpty() ? List.of() : option.sourceColumns(),
                    option.columnMapping() == null ? Map.of() : option.columnMapping(),
                    option.truncateFirst(),
                    option.onConflict(),
                    List.of(),
                    option.batchSize() == null ? 0 : option.batchSize());
            try (java.sql.Connection conn = datasourceService.connection(option.datasourceId());
                 SchemaScope scope = useSchema(conn, option.database())) {
                requireTable(conn, table);
                if (option.truncateFirst()) {
                    try (java.sql.Statement stmt = conn.createStatement()) {
                        applyQueryTimeout(stmt);
                        stmt.executeUpdate("TRUNCATE TABLE " + DbDialectSupport.quoteIdent(conn, table, "表名"));
                    } catch (java.sql.SQLException fallback) {
                        try (java.sql.Statement stmt = conn.createStatement()) {
                            applyQueryTimeout(stmt);
                            stmt.executeUpdate("DELETE FROM " + DbDialectSupport.quoteIdent(conn, table, "表名"));
                        }
                    }
                }
                char delimiter = resolveDelimiter(format, text);
                var parsed = "json".equals(format)
                        ? com.chua.starter.datasourcehub.support.dbconsole.DbTransferCodec.parseJson(text, List.of())
                        : com.chua.starter.datasourcehub.support.dbconsole.DbTransferCodec.parseDelimited(
                                text, delimiter, !Boolean.FALSE.equals(option.hasHeader()));
                if (options.sourceColumns().isEmpty() && option.columnMapping() == null) {
                    throw new IllegalArgumentException("请先配置字段映射");
                }
                return com.chua.starter.datasourcehub.support.dbconsole.DbImporter.importRows(conn, parsed.rows(), options);
            }
        });
    }

    /**
     * 解析导入选项 JSON（前端以字符串形式随 multipart 一起提交）。
     *
     * @param optionJson 选项 JSON
     * @return 导入选项；输入为空返回 null
     */
    private static com.chua.starter.datasourcehub.support.dbconsole.DbImportDTO parseImportOption(String optionJson) {
        if (optionJson == null || optionJson.isBlank()) {
            return null;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(optionJson, com.chua.starter.datasourcehub.support.dbconsole.DbImportDTO.class);
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("导入选项格式不正确: " + e.getMessage());
        }
    }

    /**
     * 导入 SQL 脚本：逐条执行，支持空行与注释跳过。
     *
     * @param text   SQL 文本
     * @param option 导入选项（取 datasourceId / database）
     * @return 导入统计
     * @throws Exception 任一语句失败或取连接失败
     */
    private com.chua.starter.datasourcehub.support.dbconsole.DbImporter.ImportResult executeSqlScript(
            String text, com.chua.starter.datasourcehub.support.dbconsole.DbImportDTO option) throws Exception {
        var statements = com.chua.starter.datasourcehub.support.dbconsole.DbTransferCodec.splitSqlStatements(text);
        int affected = 0;
        try (java.sql.Connection conn = datasourceService.connection(option.datasourceId());
             SchemaScope scope = useSchema(conn, option.database());
             java.sql.Statement stmt = conn.createStatement()) {
            for (String statement : statements) {
                affected += stmt.executeUpdate(statement);
            }
        }
        return new com.chua.starter.datasourcehub.support.dbconsole.DbImporter.ImportResult(
                statements.size(), affected, 0, 0, 0, List.of());
    }

    /**
     * 导出：把表（或其结构 / 数据）序列化为目标格式。
     *
     * <p>安全豁免：表名、排序列经标识符白名单与 JDBC 元数据校验；WHERE 条件仅允许
     * 单条筛选语句（已剔除分隔符与注释符）。行数上限 {@value #EXPORT_MAX_ROWS}。</p>
     *
     * @param request 导出请求（tableName / format / scope / limit / where / orderBy）
     * @return 导出内容（字符串，前端侧落盘为文件）
     */
    @Operation(summary = "导出数据")
    @SysLog(name = "导出数据")
    @PostMapping("export")
    public ReturnResult<Map<String, Object>> exportData(@RequestBody Map<String, Object> request) {
        String table = safeIdent(String.valueOf(request.get("tableName")), "表名");
        String format = String.valueOf(request.getOrDefault("format", "csv"));
        String scopeName = String.valueOf(request.getOrDefault("scope", "data"));
        int limit = intOrDefault(request.get("limit"), 0);
        String where = request.get("where") == null ? null : sanitizeWhere(String.valueOf(request.get("where")));
        String orderBy = request.get("orderBy") == null ? null
                : safeIdent(String.valueOf(request.get("orderBy")), "排序列");
        String orderDir = request.get("orderDir") == null ? "ASC" : String.valueOf(request.get("orderDir"));
        Long dsId = bodyDatasourceId(request);
        String db = bodyDatabase(request);
        return callDb("导出数据", () -> {
            try (java.sql.Connection conn = datasourceService.connection(dsId);
                 SchemaScope scope = useSchema(conn, db)) {
                requireTable(conn, table);
                var formatEnum = com.chua.starter.datasourcehub.support.dbconsole.DbExporter.Format.of(format);
                var scopeEnum = com.chua.starter.datasourcehub.support.dbconsole.DbExporter.Scope.of(scopeName);
                var options = new com.chua.starter.datasourcehub.support.dbconsole.DbExporter.ExportOptions(
                        table, formatEnum, scopeEnum, limit, where, orderBy, orderDir, 100);
                var result = com.chua.starter.datasourcehub.support.dbconsole.DbExporter.export(conn, options);
                Map<String, Object> payload = new java.util.LinkedHashMap<>();
                payload.put("content", result.content());
                payload.put("rowCount", result.rowCount());
                payload.put("truncated", result.truncated());
                payload.put("format", formatEnum.name().toLowerCase(java.util.Locale.ROOT));
                payload.put("fileName", table + "_" + com.chua.starter.datasourcehub.support.dbconsole.DbTransferCodec.timestamp()
                        + "." + formatEnum.extension());
                return payload;
            }
        });
    }

    /**
     * 导出格式与范围的可选值（供前端下拉，避免前后端枚举各写一份）。
     *
     * @return 格式列表与范围列表
     */
    @Operation(summary = "导出格式与范围枚举")
    @GetMapping("export/options")
    public ReturnResult<Map<String, Object>> exportOptions() {
        List<Map<String, String>> formats = Arrays.stream(
                        com.chua.starter.datasourcehub.support.dbconsole.DbExporter.Format.values())
                .map(f -> Map.of("value", f.name().toLowerCase(java.util.Locale.ROOT), "label", switch (f) {
                    case CSV -> "CSV（逗号分隔）";
                    case TSV -> "TSV（制表符分隔）";
                    case JSON -> "JSON";
                    case XML -> "XML";
                    case SQL -> "SQL 脚本";
                }))
                .toList();
        List<Map<String, String>> scopes = Arrays.stream(
                        com.chua.starter.datasourcehub.support.dbconsole.DbExporter.Scope.values())
                .map(s -> Map.of("value", s.name().toLowerCase(java.util.Locale.ROOT), "label", switch (s) {
                    case STRUCTURE -> "仅结构";
                    case DATA -> "仅数据";
                    case ALL -> "结构 + 数据";
                }))
                .toList();
        return ReturnResult.ok(Map.of("formats", formats, "scopes", scopes));
    }

    /** 导出单次行数上限 */
    private static final int EXPORT_MAX_ROWS = com.chua.starter.datasourcehub.support.dbconsole.DbTransferCodec.MAX_EXPORT_ROWS;

    /**
     * 从请求体取整数，缺失或非数字时用默认值。
     */
    private static int intOrDefault(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 判定导入文件格式：显式指定优先，否则按后缀，再否则按内容首字符。
     *
     * @param format    前端指定的格式
     * @param fileName  原始文件名
     * @param text      文件文本（可能为 null）
     * @return 格式标识
     */
    private static String resolveImportFormat(String format, String fileName, String text) {
        String explicit = format == null ? "" : format.trim().toLowerCase(java.util.Locale.ROOT);
        if (!explicit.isEmpty() && !"auto".equals(explicit)) {
            return explicit;
        }
        String name = fileName == null ? "" : fileName.toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".sql")) {
            return "sql";
        }
        if (name.endsWith(".json")) {
            return "json";
        }
        if (name.endsWith(".tsv") || name.endsWith(".tab")) {
            return "tsv";
        }
        if (name.endsWith(".csv")) {
            return "csv";
        }
        String sample = text == null ? "" : text.stripLeading();
        if (sample.startsWith("[") || sample.startsWith("{")) {
            return "json";
        }
        return sample.toUpperCase(java.util.Locale.ROOT).startsWith("INSERT") ? "sql" : "csv";
    }

    /**
     * 判定分隔符：TSV 固定制表符，其余按内容探测（首行含制表符则视为 TSV）。
     */
    private static char resolveDelimiter(String format, String text) {
        if ("tsv".equals(format)) {
            return '\t';
        }
        if (text == null) {
            return ',';
        }
        return com.chua.starter.datasourcehub.support.dbconsole.DbTransferCodec.detectDelimiter(text);
    }

    /**
     * 清空表：优先 TRUNCATE，不支持的内核退化为 DELETE FROM。
     *
     * <p>安全豁免：表名经标识符白名单和 JDBC 元数据校验后才进入数据库方言 DDL。</p>
     */
    @Operation(summary = "清空表")
    @SysLog(name = "清空表")
    @PostMapping("table/{tableName}/truncate")
    public ReturnResult<Boolean> truncateTable(
            @PathVariable String tableName,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database,
            @RequestParam(required = false) String txId) {
        String t = safeIdent(tableName, "表名");
        java.util.function.Function<java.sql.Connection, Boolean> truncate = conn -> {
            try {
                requireTable(conn, t);
                try {
                    try (java.sql.PreparedStatement stmt = conn.prepareStatement("TRUNCATE TABLE " + DbDialectSupport.quoteIdent(conn, t, "表名"))) {
                        applyQueryTimeout(stmt);
                        stmt.executeUpdate();
                    }
                } catch (java.sql.SQLException fallback) {
                    try (java.sql.PreparedStatement stmt = conn.prepareStatement("DELETE FROM " + DbDialectSupport.quoteIdent(conn, t, "表名"))) {
                        applyQueryTimeout(stmt);
                        stmt.executeUpdate();
                    }
                }
                return Boolean.TRUE;
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        };
        // 事务内清空：TRUNCATE 在 MySQL 是 DDL，**本身就会隐式提交**，
        // 所以事务里执行它不会像 UPDATE 那样可以回滚。
        // 界面必须如实告知，否则用户会以为「回滚能把数据找回来」而实际数据已经没了。
        if (txId != null && !txId.isBlank()) {
            return callDb("清空表", () -> dbTxRegistry().require(txId).run(truncate));
        }
        return callDb("清空表", () -> {
            try (java.sql.Connection conn = datasourceService.connection(datasourceId);
                 SchemaScope scope = useSchema(conn, database)) {
                return truncate.apply(conn);
            }
        });

    }

    /**
     * 从请求体构造完整列定义。
     *
     * <p>所有属性由 {@link DbColumnDef} 在构造期校验，
     * 校验不通过直接抛 {@link IllegalArgumentException}，由 {@link #callDb} 转成可读错误。</p>
     *
     * @param body 列定义请求体
     * @return 完整列定义
     */
    private static DbColumnDef columnDef(Map<String, Object> body) {
        String columnName = String.valueOf(body.getOrDefault("columnName", ""));
        String type = String.valueOf(body.getOrDefault("type", ""));
        Integer size = intOrNull(body.get("size"));
        Integer scale = intOrNull(body.get("scale"));
        @SuppressWarnings("unchecked")
        List<String> enumValues = (List<String>) body.get("enumValues");
        return new DbColumnDef(
                columnName,
                type,
                size,
                scale,
                Boolean.TRUE.equals(body.get("unsigned")),
                Boolean.TRUE.equals(body.get("zerofill")),
                body.get("charset") == null ? "" : String.valueOf(body.get("charset")),
                body.get("collation") == null ? "" : String.valueOf(body.get("collation")),
                !Boolean.FALSE.equals(body.get("nullable")),
                body.get("defaultValue") == null ? "" : String.valueOf(body.get("defaultValue")),
                body.get("onUpdate") == null ? "" : String.valueOf(body.get("onUpdate")),
                Boolean.TRUE.equals(body.get("autoIncrement")),
                enumValues,
                Boolean.TRUE.equals(body.get("generated")),
                body.get("comment") == null ? "" : String.valueOf(body.get("comment")));
    }

    /**
     * 数据源 ID：body 优先，query 兜底。
     *
     * <p>新增端点历史上只从 query 取，前端把参数放 body 时就被静默忽略，
     * 于是 {@code useSchema} 收到 null、语句跑在连接默认库上（表现为
     * 「建到了 ngxacc 而不是请求的 jdbc_acc」，且建的过程不报错）。
     * 与 {@code DbObjectDialect} 那次是同一个坑，这里统一。</p>
     *
     * @param queryValue query 参数
     * @param body       请求体，可为空
     * @return 数据源 ID
     */
    private static Long queryOrBodyDatasourceId(Long queryValue, Map<String, Object> body) {
        return body == null ? queryValue : (bodyDatasourceId(body) != null ? bodyDatasourceId(body) : queryValue);
    }

    /**
     * 目标库：body 优先，query 兜底。
     *
     * @param queryValue query 参数
     * @param body       请求体，可为空
     * @return 库名，可为空（表示沿用连接默认库）
     */
    private static String queryOrBodyDatabase(String queryValue, Map<String, Object> body) {
        String fromBody = body == null ? null : bodyDatabase(body);
        return fromBody != null ? fromBody : queryValue;
    }

    /**
     * 取整数，缺失或非数字返回 null。
     *
     * @param value 原始值
     * @return 整数或 null
     */
    private static Integer intOrNull(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("必须是整数: " + value);
        }
    }

    /**
     * 取字符串，null 返回 null、纯空白返回空串。
     *
     * <p>列位置里空串有独立语义（FIRST），不能像普通字段那样吞掉。</p>
     *
     * @param value 原始值
     * @return null / 空串 / 去首尾空白后的字符串
     */
    private static String asStringOrBlank(Object value) {
        if (value == null) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    /**
     * 新增列（ALTER TABLE ADD COLUMN）。
     *
     * <p>走完整列定义：类型全集 + UNSIGNED/ZEROFILL + 字符集/排序规则 +
     * AUTO_INCREMENT + ON UPDATE + ENUM/SET 取值 + 生成列 + 注释。</p>
     *
     * @param tableName 表名
     * @param body      列定义
     * @return 是否成功
     */
    @Operation(summary = "新增列")
    @SysLog(name = "新增列")
    @PostMapping("table/{tableName}/column")
    public ReturnResult<Boolean> addColumn(@PathVariable String tableName,
                                           @RequestBody Map<String, Object> body,
                                           @RequestParam(required = false) Long datasourceId,
                                           @RequestParam(required = false) String database) {
        String t = safeIdent(tableName, "表名");
        var def = columnDef(body);
        Long dsId = queryOrBodyDatasourceId(datasourceId, body);
        String db = queryOrBodyDatabase(database, body);
        return callDb("新增列", () -> {
            try (java.sql.Connection conn = datasourceService.connection(dsId);
                 SchemaScope scope = useSchema(conn, db)) {
                requireMissingColumn(conn, t, def.name());
                DbDialectSupport.addColumn(conn, t, def);
                return Boolean.TRUE;
            }
        });
    }

    /**
     * 修改列（ALTER TABLE MODIFY COLUMN）。
     *
     * @param tableName  表名
     * @param columnName 列名
     * @param body       完整列定义（列名以路径参数为准）
     * @return 是否成功
     */
    @Operation(summary = "修改列")
    @SysLog(name = "修改列")
    @PutMapping("table/{tableName}/column/{columnName}")
    public ReturnResult<Boolean> modifyColumn(
            @PathVariable String tableName,
            @PathVariable String columnName,
            @RequestBody Map<String, Object> body,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        // InfluxDB 没有列注释概念：直接返回成功，备注仅前端展示
        InfluxdbManageService influxSvc = influxdbManageServiceProvider.getIfAvailable();
        if (influxSvc != null && isInfluxdbType(datasourceId)) {
            return ReturnResult.ok(Boolean.TRUE);
        }
        String t = safeIdent(tableName, "表名");
        String col = safeIdent(columnName, "列名");
        // 改列时列名不可变：忽略请求体里的 columnName，强制用路径参数
        body.put("columnName", col);
        var def = columnDef(body);
        String afterColumn = body.containsKey("afterColumn") ? asStringOrBlank(body.get("afterColumn")) : null;
        Long dsId = queryOrBodyDatasourceId(datasourceId, body);
        String db = queryOrBodyDatabase(database, body);
        return callDb("修改列", () -> {
            try (java.sql.Connection conn = datasourceService.connection(dsId);
                 SchemaScope scope = useSchema(conn, db)) {
                requireColumn(conn, t, col);
                DbDialectSupport.alterColumn(conn, t, def, afterColumn);
                return Boolean.TRUE;
            }
        });
    }

    /**
     * 删除列（ALTER TABLE DROP COLUMN）。
     *
     * <p>安全豁免：表名和列名经标识符白名单及 JDBC 元数据校验。</p>
     */
    @Operation(summary = "删除列")
    @SysLog(name = "删除列")
    @DeleteMapping("table/{tableName}/column/{columnName}")
    public ReturnResult<Boolean> dropColumn(
            @PathVariable String tableName,
            @PathVariable String columnName,
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) String database) {
        String t = safeIdent(tableName, "表名");
        String col = safeIdent(columnName, "列名");
        return callDb("删除列", () -> {
            try (java.sql.Connection conn = datasourceService.connection(datasourceId);
                 SchemaScope scope = useSchema(conn, database)) {
                requireColumn(conn, t, col);
                // 引用形式按方言拼（MySQL 反引号 / DuckDB·PG·SQLite 双引号）
                String sql = "ALTER TABLE " + DbDialectSupport.quoteIdent(conn, t, "表名")
                        + " DROP COLUMN " + DbDialectSupport.quoteIdent(conn, col, "列名");
                try (java.sql.PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.executeUpdate();
                }
                return Boolean.TRUE;
            }
        });

    }
}
