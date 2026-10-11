package com.chua.starter.datasourcehub.support.operlog;

import com.chua.starter.common.support.logger.SysLoggerInfo;
import com.chua.starter.datasourcehub.support.entity.MsDatasource;
import com.chua.starter.datasourcehub.support.entity.MsDatasourceOperLog;
import com.chua.starter.datasourcehub.support.mapper.MsDatasourceMapper;
import com.chua.starter.datasourcehub.support.service.DatasourceOperLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code @SysLog} 事件 → {@code ms_datasource_oper_log} 的落库监听器。
 *
 * <p><strong>它是这条链路唯一的消费者。</strong>切面（{@code SysLoggerPointcutAdvisor}）
 * 把 {@link SysLoggerInfo} 用 {@code ApplicationContext} 发出去，但全仓没有任何
 * {@code @EventListener} 接它：事件被发布即被丢弃。查询端 {@code GET /v2/log/page}
 * 也找不到对应表，实测稳定返回 {@code S0503C0000 资源不存在}。
 * 也就是说「操作日志」此前既不写也不读。本监听器补上写的一端。</p>
 *
 * <p><strong>为什么按 URL 收窄而不是全量入库</strong>：切面对整个应用生效，
 * 系统模块（用户、租户、登录…）的日志不该出现在「某个数据源的操作历史」里。
 * 这里只收数据源中心自己的两个前缀：{@code /ms/datasource}（台账与隧道）和
 * {@code /ms/monitor/db}（数据库控制台）。</p>
 *
 * <p><strong>为什么只记能定位到数据源的操作</strong>：{@code datasource_id} 是非空列。
 * 「列出全部数据源」这类中心级查询没有主语，记下来只会稀释时间线；
 * 定位不到数据源的请求一律丢弃，并打 debug 便于核对口径。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatasourceOperLogListener {

    /**
     * 数据源中心自己的 URL 前缀，其余模块的 {@code @SysLog} 一律不收。
     */
    private static final String[] SCOPED_PREFIXES = {"/ms/datasource", "/ms/monitor/db"};

    /**
     * 路径里的数据源 id：{@code /ms/datasource/20}、{@code /ms/datasource/tunnel/20}。
     */
    private static final Pattern PATH_ID = Pattern.compile("^/ms/datasource/(?:tunnel/)?(\\d+)(?:/|$)");

    /**
     * 查询串里的数据源 id：控制台的绝大多数端点（{@code /ms/monitor/db/**}）
     * 把 {@code datasourceId} 放在查询参数里而不是路径里。
     */
    private static final Pattern QUERY_ID = Pattern.compile("[?&]datasourceId=(\\d+)");

    /**
     * 数据源台账端点的方法名 → 操作码（{@code /ms/datasource/{id}}）。
     */
    private static final Map<String, String> CRUD_CODES = Map.of(
            "create", "CREATE",
            "update", "UPDATE",
            "delete", "DELETE",
            "test", "TEST");

    /**
     * 隧道端点的方法名 → 操作码（{@code /ms/datasource/tunnel/{id}}）。
     *
     * <p>单独一张表而不是并进上面那张：两个控制器都有 {@code get}/{@code delete}
     * 方法名，只按方法名映射无法区分是谁，记出来的日志会张冠李戴。
     * 所以先按 URL 前缀定范围，再在该范围内按方法名映射。</p>
     */
    private static final Map<String, String> TUNNEL_CODES = Map.of(
            "get", "TUNNEL_QUERY",
            "save", "TUNNEL_SAVE",
            "delete", "TUNNEL_DELETE");

    /**
     * 数据库控制台的方法名 → 操作码（{@code /ms/monitor/db/**}）。
     *
     * <p>控制台有二十多个写操作，方法名（如 {@code upsertDbObject}、{@code updateCell}）
     * 直接大写当操作码虽然可检索，但读起来像内部符号。这里按「对象_动作」给稳定码，
     * 前端按码过滤、按文案展示都不用跟着 Java 方法名走。</p>
     */
    private static final Map<String, String> CONSOLE_CODES = Map.ofEntries(
            Map.entry("executeDbSql", "CONSOLE_SQL_EXECUTE"),
            Map.entry("executeDbSqlBatch", "CONSOLE_SQL_EXECUTE_BATCH"),
            Map.entry("commitDbTx", "CONSOLE_TX_COMMIT"),
            Map.entry("createTable", "CONSOLE_TABLE_CREATE"),
            Map.entry("dropTable", "CONSOLE_TABLE_DROP"),
            Map.entry("renameTable", "CONSOLE_TABLE_RENAME"),
            Map.entry("truncateTable", "CONSOLE_TABLE_TRUNCATE"),
            Map.entry("updateTableOptions", "CONSOLE_TABLE_OPTION_UPDATE"),
            Map.entry("setTablePrimaryKey", "CONSOLE_PRIMARY_KEY_SET"),
            Map.entry("upsertDbObject", "CONSOLE_OBJECT_UPSERT"),
            Map.entry("dropDbObject", "CONSOLE_OBJECT_DROP"),
            Map.entry("addForeignKey", "CONSOLE_FOREIGN_KEY_ADD"),
            Map.entry("dropForeignKey", "CONSOLE_FOREIGN_KEY_DROP"),
            Map.entry("createIndex", "CONSOLE_INDEX_CREATE"),
            Map.entry("dropIndex", "CONSOLE_INDEX_DROP"),
            Map.entry("addColumn", "CONSOLE_COLUMN_ADD"),
            Map.entry("modifyColumn", "CONSOLE_COLUMN_MODIFY"),
            Map.entry("dropColumn", "CONSOLE_COLUMN_DROP"),
            Map.entry("updateCell", "CONSOLE_CELL_UPDATE"),
            Map.entry("importDataFile", "CONSOLE_DATA_IMPORT"),
            Map.entry("exportData", "CONSOLE_DATA_EXPORT"),
            Map.entry("createDatabase", "CONSOLE_DATABASE_CREATE"),
            Map.entry("dropDatabase", "CONSOLE_DATABASE_DROP"));

    /**
     * 操作对象（{@code target} 列）的提取规则：路径 → 对象名。
     *
     * <p>这一列原先建了却没人写，读日志时只能看到「执行 SQL」而看不到动的是哪张表 ——
     * 对数据库控制台来说，缺了对象名这条日志基本没有取证价值。</p>
     *
     * <p>按顺序匹配，{@code #1} 是被捕获的片段。路径变量是 URL 编码的，提取后要解码
     * （表名带中文/空格时不解码会存成一串 %E4%B8%AD）。</p>
     */
    private static final List<Pattern> TARGET_PATTERNS = List.of(
            // /ms/monitor/db/table/{table}/...
            Pattern.compile("^/ms/monitor/db/table/([^/]+)"),
            // /ms/monitor/db/objects/{category}/{name}
            Pattern.compile("^/ms/monitor/db/objects/[^/]+/([^/]+)"),
            // 建/删库的对象是库名，但库名在请求体或 ?name= 里，路径上没有
            Pattern.compile("^/ms/monitor/db/database$"));

    private final DatasourceOperLogService operLogService;

    private final MsDatasourceMapper datasourceMapper;

    /**
     * 接收切面发出的操作日志事件。
     *
     * @param info 事件载荷
     */
    @EventListener
    public void onSysLog(SysLoggerInfo info) {
        try {
            Long datasourceId = resolveDatasourceId(info == null ? null : info.getLogMapping());
            if (datasourceId == null) {
                return;
            }
            operLogService.record(toRow(info, datasourceId));
        } catch (RuntimeException e) {
            // 监听器抛异常会冒泡回切面、进而让业务请求报错，
            // 所以这里必须自己吞掉：留痕失败不影响用户操作。
            log.warn("[datasource-oper-log] 处理操作日志事件失败 reason={}", e.getMessage());
        }
    }

    /**
     * 从请求 URL 里解析数据源 id。
     *
     * @param url 完整请求 URL（含查询串）
     * @return 数据源 id；不在范围内或解析不出时返回 null
     */
    private Long resolveDatasourceId(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        // 只保留 path 部分做前缀判定，避免 host 名里恰好含 /ms 之类的误命中
        String path = pathOf(url);
        if (path.startsWith("/ms/datasource")) {
            Matcher m = PATH_ID.matcher(path);
            if (m.find()) {
                return Long.valueOf(m.group(1));
            }
        }
        boolean inScope = false;
        for (String prefix : SCOPED_PREFIXES) {
            if (path.startsWith(prefix)) {
                inScope = true;
                break;
            }
        }
        if (!inScope) {
            return null;
        }
        Matcher q = QUERY_ID.matcher(url);
        if (q.find()) {
            return Long.valueOf(q.group(1));
        }
        // POST /ms/datasource（新建数据源）的 id 在响应体里，事件里拿不到 —— 这次不记
        return null;
    }

    /**
     * 事件转日志行。
     *
     * @param info         事件
     * @param datasourceId 数据源 id
     * @return 日志行
     */
    private MsDatasourceOperLog toRow(SysLoggerInfo info, Long datasourceId) {
        String path = pathOf(info.getLogMapping());
        MsDatasourceOperLog row = new MsDatasourceOperLog();
        row.setDatasourceId(datasourceId);
        row.setDatasourceName(resolveName(datasourceId));
        row.setAction(actionOf(path, info.getMethodName()));
        row.setActionName(info.getLogName() == null ? info.getLogModule() : info.getLogName());
        row.setTarget(targetOf(info.getLogMapping(), path));
        row.setDetail(buildDetail(path, info));
        row.setSuccess(info.getLogStatus());
        row.setCostMs(info.getLogCost());
        row.setOperator(info.getCreateName() == null ? info.getCreateBy() : info.getCreateName());
        row.setClientIp(info.getClientIp());
        row.setRequestUrl(info.getLogMapping());
        row.setCreateTime(info.getCreateTime() == null
                ? LocalDateTime.now()
                : LocalDateTime.ofInstant(info.getCreateTime().toInstant(), ZoneId.systemDefault()));
        return row;
    }

    /**
     * 取 URL 的 path 部分（含查询串）。
     *
     * @param url 完整请求 URL
     * @return path
     */
    private String pathOf(String url) {
        if (url == null) {
            return "";
        }
        int schemeEnd = url.indexOf("://");
        String rest = schemeEnd >= 0 ? url.substring(schemeEnd + 3) : url;
        int slash = rest.indexOf('/');
        return slash >= 0 ? rest.substring(slash) : "/";
    }

    /**
     * 操作码：先按 URL 前缀定范围，再在该范围内按方法名映射。
     *
     * <p>未登记的用 {@code 前缀_方法名大写} 兜底，而不是丢弃 ——
     * 「没登记」不该等于「记下来没人看得懂」，大写的方法名本身是可检索的线索。</p>
     *
     * @param path        请求 path
     * @param methodName  控制器方法名
     * @return 操作码
     */
    private String actionOf(String path, String methodName) {
        String method = methodName == null ? "" : methodName;
        if (path.startsWith("/ms/datasource/tunnel/")) {
            return TUNNEL_CODES.getOrDefault(method, "TUNNEL_" + upper(method));
        }
        if (path.startsWith("/ms/datasource")) {
            return CRUD_CODES.getOrDefault(method, upper(method));
        }
        return CONSOLE_CODES.getOrDefault(method, "CONSOLE_" + upper(method));
    }

    /**
     * 操作对象：控制台类操作记「动了哪个库/表」，其余前缀留空。
     *
     * <p>库名不从路径取（{@code /ms/monitor/db/database} 的库名在请求体里，事件里取不到），
     * 退而取查询串里的 {@code name=} / {@code database=}；都没有就留空 ——
     * 与 {@code resolveName} 的取舍一致：宁缺勿编。</p>
     *
     * @param url 完整请求 URL（含查询串）
     * @param path URL 的 path 部分
     * @return 操作对象；取不到返回 null
     */
    private String targetOf(String url, String path) {
        if (path == null || !path.startsWith("/ms/monitor/db")) {
            return null;
        }
        for (Pattern pattern : TARGET_PATTERNS) {
            Matcher m = pattern.matcher(path);
            if (!m.find()) {
                continue;
            }
            if (m.groupCount() >= 1) {
                return decode(m.group(1));
            }
            // 库级操作：路径里没有对象，改从查询串拿库名
            String db = queryValue(url, "database");
            return db != null ? db : queryValue(url, "name");
        }
        return null;
    }

    /**
     * 取查询串里的某个参数值。
     *
     * @param url 完整请求 URL
     * @param key 参数名
     * @return 参数值；不存在返回 null
     */
    private String queryValue(String url, String key) {
        if (url == null) {
            return null;
        }
        Matcher m = Pattern.compile("[?&]" + Pattern.quote(key) + "=([^&#]*)").matcher(url);
        return m.find() ? decode(m.group(1)) : null;
    }

    /**
     * URL 解码；解不出来（非法编码）就返回原串，不让留痕失败。
     *
     * @param raw 原始片段
     * @return 解码结果
     */
    private String decode(String raw) {
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            return raw;
        }
    }

    /**
     * 明细：控制台类操作把入参摘要带上，CRUD/隧道类记路径即可。
     *
     * <p>控制台的入参 JSON 里含表名、SQL 片段，是「当时动了什么」的主要证据；
     * CRUD 的目标就是那条数据源本身，路径已经说清了，再塞一份入参只是噪音。</p>
     *
     * @param path 请求 path
     * @param info 事件
     * @return 明细
     */
    private String buildDetail(String path, SysLoggerInfo info) {
        if (info.getLogContent() != null && !info.getLogContent().isBlank()) {
            return info.getLogContent();
        }
        if (path.startsWith("/ms/monitor/db") && info.getLogParam() != null) {
            return info.getLogParam();
        }
        return null;
    }

    /**
     * 方法名大写（空名返回 {@code UNKNOWN}）。
     *
     * @param method 方法名
     * @return 大写结果
     */
    private String upper(String method) {
        return method.isBlank() ? "UNKNOWN" : method.toUpperCase(Locale.ROOT);
    }

    /**
     * 冗余一份数据源名称。
     *
     * <p>删除操作触发时数据源已经没了，这里查不到就留空 —— 历史日志里
     * 「已删除」和「当时叫什么」只能保其一，取「当时还在、能查到」的那一侧，
     * 因为绝大多数日志是日常操作，不是一次性的删除。</p>
     *
     * @param datasourceId 数据源 id
     * @return 数据源名称，查不到返回空串
     */
    private String resolveName(Long datasourceId) {
        try {
            MsDatasource row = datasourceMapper.selectById(datasourceId);
            return row == null || row.getDatasourceName() == null ? "" : row.getDatasourceName();
        } catch (RuntimeException e) {
            log.debug("[datasource-oper-log] 查数据源名称失败 datasourceId={}", datasourceId);
            return "";
        }
    }
}