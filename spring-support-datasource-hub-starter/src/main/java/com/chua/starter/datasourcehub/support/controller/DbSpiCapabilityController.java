// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasourcehub.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.datasource.support.permission.PermissionManager;
import com.chua.datasource.support.process.ProcessInfo;
import com.chua.datasource.support.process.ProcessManager;
import com.chua.datasource.support.slowquery.SlowQueryInfo;
import com.chua.datasource.support.slowquery.SlowQueryManager;
import com.chua.datasource.support.user.DataSourceAware;
import com.chua.datasource.support.user.UserInfo;
import com.chua.datasource.support.user.UserManager;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据库 SPI 能力网关：把引擎侧 SPI 接到 HTTP 上。
 *
 * <p>为什么单独一个控制器：{@link DbConsoleController} 全是裸 JDBC（表结构 / SQL / 导入导出），
 * 而「用户管理 / 权限 / 进程 / 慢查询」这类能力的<b>有无</b>由引擎 SPI 决定，
 * 不是靠前端猜数据源类型。SPI 侧早已就绪：</p>
 *
 * <ul>
 *   <li>{@code com.chua.datasource.support.user.UserManager}（{@link UserManager}）</li>
 *   <li>{@code com.chua.datasource.support.permission.PermissionManager}（{@link PermissionManager}）</li>
 *   <li>{@code com.chua.datasource.support.process.ProcessManager}（{@link ProcessManager}）</li>
 *   <li>{@code com.chua.datasource.support.slowquery.SlowQueryManager}（{@link SlowQueryManager}）</li>
 *   <li>实现按方言协议注册在 {@code META-INF/extensions/} 里（如
 *       {@code mysql=com.chua.mysql.support.user.MysqlUserManager}）</li>
 * </ul>
 *
 * <p>此前 hub 里对这两个 SPI <b>零引用</b>，前端 {@code /ms/monitor/db/users} 等端点一律 404，
 * 菜单只能靠「类型 === jdbc」硬猜。本控制器补上「先问能力、再取数据」这条链路。</p>
 *
 * <p>能力探测的判据与 {@code JdbcEngine.resolveManager} 同源：按方言协议从 {@link ServiceProvider}
 * 取扩展，取不到即该内核无此能力（返回 {@code null}，不抛异常）；取到则注入
 * {@link DataSourceAware#setDataSource(DataSource)}。</p>
 *
 * <p>四类能力（用户 / 权限 / 进程 / 慢查询）现在都是「一次探测 = 一个 SPI 查询」：
 * {@code capabilities} 里各键的布尔值就是 {@code spi(XxxManager.class, protocol, id) != null}，
 * 前端据此决定菜单项，不需要知道任何方言细节。内核新增实现（哪怕只有 {@code PostgreSQL}）
 * 时，后端加一个 {@code META-INF/extensions} 注册文件即可，接口与前端都不用动。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
@Tag(name = "数据库 SPI 能力", description = "用户管理 / 权限 / 进程 / 慢查询的 SPI 能力判定与代理调用")
@RestController
@RequestMapping("/ms/monitor/db")
@RequiredArgsConstructor
@Slf4j
public class DbSpiCapabilityController {

    /**
     * 慢查询平均耗时的默认下限（毫秒）。
     *
     * <p>调用方不传 {@code minAvgMs} 时用它。取 1ms 而不是 0：{@code performance_schema}
     * 里绝大多数是 0.0x ms 的短语句，下限为 0 会把面板刷成一屏噪声，真正要看的慢语句被埋掉。
     * 需要全量时前端显式传 {@code minAvgMs=0}（本页目前固定不传，即走这个默认值）。</p>
     */
    private static final double DEFAULT_SLOW_MS = 1.0;

    /**
     * 数据源服务（台账读取 + JDBC 连接构建）。
     */
    private final DatasourceService datasourceService;

    // ==================== 能力清单 ====================

    /**
     * 数据源 SPI 能力清单。
     *
     * <p>前端右键菜单据此决定「用户管理 / 显示进程 / 慢查询」是否出现，不再自己按类型猜。</p>
     *
     * @param datasourceId 数据源 ID
     * @return {@code dialect} 与各能力布尔值
     */
    @Operation(summary = "数据源 SPI 能力清单")
    @GetMapping("capabilities")
    public ReturnResult<Map<String, Object>> capabilities(@RequestParam(required = false) Long datasourceId) {
        String protocol;
        try (Connection conn = datasourceService.connection(datasourceId)) {
            protocol = protocolOf(conn);
        } catch (Exception e) {
            return ReturnResult.error("读取数据源连接失败: " + e.getMessage());
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("dialect", protocol);
        payload.put("users", spi(UserManager.class, protocol, datasourceId) != null);
        payload.put("permissions", spi(PermissionManager.class, protocol, datasourceId) != null);
        payload.put("processes", spi(ProcessManager.class, protocol, datasourceId) != null);
        payload.put("slowQueries", spi(SlowQueryManager.class, protocol, datasourceId) != null);
        return ReturnResult.ok(payload);
    }

    // ==================== 用户管理 ====================

    /**
     * 数据库用户列表。
     *
     * <p>账号本体走 SPI {@link UserManager#listUsers()}；返回体里的资源限制 / SSL / 是否超级用户
     * 这些列 SPI 的 {@code UserInfo} 不含（只有 user / host / password），
     * 由 {@code mysql.user} 补齐（无权限读时整段跳过，不影响主体结果）。</p>
     *
     * @param datasourceId 数据源 ID
     * @return {@code supported} + {@code users}
     */
    @Operation(summary = "数据库用户列表（SPI UserManager）")
    @GetMapping("users")
    public ReturnResult<Map<String, Object>> users(@RequestParam(required = false) Long datasourceId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        String protocol;
        try {
            protocol = protocolOfDatasource(datasourceId);
        } catch (Exception e) {
            return ReturnResult.error("读取数据源连接失败: " + e.getMessage());
        }
        UserManager manager = spi(UserManager.class, protocol, datasourceId);
        if (manager == null) {
            payload.put("supported", false);
            payload.put("users", List.of());
            payload.put("message", "内核 " + protocol + " 未提供 UserManager SPI 实现");
            return ReturnResult.ok(payload);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            // 注意：DatasourceService.connection 在 TTL 内复用同一个物理连接，
            // 所以这里不能让「主流程持有的连接」和「SPI 内部取的连接」同时开着语句 —— 一律串行。
            for (UserInfo info : manager.listUsers()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", info.user());
                row.put("host", info.host());
                rows.add(row);
            }
            Map<String, Map<String, Object>> extras = userExtras(datasourceId);
            for (Map<String, Object> row : rows) {
                Map<String, Object> extra = extras.get(account((String) row.get("name"), (String) row.get("host")));
                if (extra != null) {
                    row.putAll(extra);
                }
            }
            payload.put("supported", true);
            payload.put("users", rows);
            return ReturnResult.ok(payload);
        } catch (Exception e) {
            // 常见于账号没有 mysql.user 的 SELECT 权限（SPI 的 MysqlUserManager 直查 mysql.user）。
            // 这不等于「没有用户」：退回 information_schema 的授权视图，普通账号也能看到被授权过的账号。
            log.warn("SPI 列出数据库用户失败，回退 information_schema: {}", e.getMessage());
            List<Map<String, Object>> fallback = granteeUsers(datasourceId);
            payload.put("supported", true);
            payload.put("users", fallback);
            payload.put("message", fallback.isEmpty()
                    ? "读取用户列表失败: " + e.getMessage()
                    : "SPI 直读 mysql.user 受限，已按 information_schema 授权视图列出账号（可能不含从未被授权的账号）");
            return ReturnResult.ok(payload);
        }
    }

    /**
     * 某账号的权限语句原文（SHOW GRANTS）。
     *
     * <p>为什么不直接搬 {@link PermissionManager#listPermissions(String)}：它的
     * {@code PermissionInfo} 把 {@code SHOW GRANTS} 拆成了结构化字段，而本面板要展示的是
     * <b>原文</b>（前端 {@code parseGrantsToSet} 按 {@code GRANT ... ON ...} 解析权限格）。
     * SPI 在这里仍然承担「能力判定」——没有 {@code PermissionManager} 实现就返回
     * {@code supported=false}，不去执行语句。</p>
     *
     * @param body {@code datasourceId} / {@code username} / {@code host}
     * @return {@code supported} + {@code grants} 原文行
     */
    @Operation(summary = "数据库用户权限（SHOW GRANTS 原文）")
    @PostMapping("users/grants")
    public ReturnResult<Map<String, Object>> userGrants(@RequestBody Map<String, Object> body) {
        Long datasourceId = toLong(body.get("datasourceId"));
        String username = text(body.get("username"));
        String host = text(body.get("host"));
        Map<String, Object> payload = new LinkedHashMap<>();
        if (isBlank(username)) {
            return ReturnResult.error("username 不能为空");
        }
        String protocol;
        try {
            protocol = protocolOfDatasource(datasourceId);
        } catch (Exception e) {
            return ReturnResult.error("读取数据源连接失败: " + e.getMessage());
        }
        if (spi(PermissionManager.class, protocol, datasourceId) == null) {
            payload.put("supported", false);
            payload.put("grants", List.of());
            payload.put("message", "内核 " + protocol + " 未提供 PermissionManager SPI 实现");
            return ReturnResult.ok(payload);
        }
        try (Connection conn = datasourceService.connection(datasourceId)) {
            List<String> grants = new ArrayList<>();
            String sql = "SHOW GRANTS FOR " + quoteLiteral(username) + "@" + quoteLiteral(isBlank(host) ? "%" : host);
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    grants.add(rs.getString(1));
                }
            }
            payload.put("supported", true);
            payload.put("grants", grants);
            return ReturnResult.ok(payload);
        } catch (Exception e) {
            log.warn("读取账号权限失败，回退 information_schema: {}", e.getMessage());
            List<String> fallback = granteeGrantLines(datasourceId, username, host);
            payload.put("supported", true);
            payload.put("grants", fallback);
            payload.put("message", fallback.isEmpty()
                    ? "读取权限失败: " + e.getMessage()
                    : "SHOW GRANTS 受限，已按 information_schema 全局授权视图拼装（不含库级/表级授权）");
            return ReturnResult.ok(payload);
        }
    }

    /**
     * 用 {@code information_schema.USER_PRIVILEGES} 拼 {@code GRANT} 行（SHOW GRANTS 被拒时的退路）。
     *
     * <p>只覆盖全局权限 —— 库级/表级授权在另外两张视图里，这里是降级展示，
     * 调用方会通过 message 告知「可能不含库级/表级」。</p>
     *
     * @param datasourceId 数据源 ID
     * @param username 用户名
     * @param host     主机
     * @return GRANT 语句行
     */
    private List<String> granteeGrantLines(Long datasourceId, String username, String host) {
        List<String> lines = new ArrayList<>();
        String grantee = quoteLiteral(username) + "@" + quoteLiteral(isBlank(host) ? "%" : host);
        String sql = "SELECT PRIVILEGE_TYPE, IS_GRANTABLE FROM information_schema.USER_PRIVILEGES"
                + " WHERE GRANTEE = " + quoteLiteral(grantee);
        try (Connection conn = datasourceService.connection(datasourceId);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String privilege = rs.getString("PRIVILEGE_TYPE");
                String grantable = rs.getString("IS_GRANTABLE");
                lines.add("GRANT " + privilege + " ON *.* TO " + grantee
                        + ("YES".equalsIgnoreCase(grantable) ? " WITH GRANT OPTION" : ""));
            }
        } catch (Exception e) {
            log.debug("information_schema 权限视图读取失败: {}", e.getMessage());
        }
        return lines;
    }

    /**
     * 新建数据库用户（SPI {@link UserManager#createUser}）。
     *
     * @param body {@code datasourceId} / {@code username} / {@code host} / {@code password} /
     *             {@code grants} / {@code superUser}
     * @return 是否成功
     */
    @Operation(summary = "新建数据库用户")
    @PostMapping("users")
    public ReturnResult<Boolean> createUser(@RequestBody Map<String, Object> body) {
        Long datasourceId = toLong(body.get("datasourceId"));
        String username = text(body.get("username"));
        String host = text(body.get("host"));
        String password = text(body.get("password"));
        if (isBlank(username)) {
            return ReturnResult.error("username 不能为空");
        }
        try (Connection conn = datasourceService.connection(datasourceId)) {
            String protocol = protocolOf(conn);
            UserManager manager = spi(UserManager.class, protocol, datasourceId);
            if (manager == null) {
                return ReturnResult.error("内核 " + protocol + " 不支持用户管理");
            }
            UserManager.CreateUserStep step = manager.createUser(username);
            if (!isBlank(password)) {
                step = step.withPassword(password);
            }
            if (!isBlank(host)) {
                step = step.withHost(host);
            }
            step.execute();
            applyGrants(protocol, datasourceId, username, host, body);
            return ReturnResult.ok(Boolean.TRUE);
        } catch (Exception e) {
            log.warn("新建数据库用户失败: {}", e.getMessage());
            return ReturnResult.error("新建用户失败: " + e.getMessage());
        }
    }

    /**
     * 修改数据库用户（SPI {@link UserManager#alterUser}）。
     *
     * @param body 同 {@link #createUser(Map)}
     * @return 是否成功
     */
    @Operation(summary = "修改数据库用户")
    @PutMapping("users")
    public ReturnResult<Boolean> updateUser(@RequestBody Map<String, Object> body) {
        Long datasourceId = toLong(body.get("datasourceId"));
        String username = text(body.get("username"));
        String host = text(body.get("host"));
        String password = text(body.get("password"));
        if (isBlank(username)) {
            return ReturnResult.error("username 不能为空");
        }
        try (Connection conn = datasourceService.connection(datasourceId)) {
            String protocol = protocolOf(conn);
            UserManager manager = spi(UserManager.class, protocol, datasourceId);
            if (manager == null) {
                return ReturnResult.error("内核 " + protocol + " 不支持用户管理");
            }
            UserManager.AlterUserStep step = manager.alterUser(username);
            if (!isBlank(password)) {
                step = step.withPassword(password);
            }
            step.execute();
            applyGrants(protocol, datasourceId, username, host, body);
            return ReturnResult.ok(Boolean.TRUE);
        } catch (Exception e) {
            log.warn("修改数据库用户失败: {}", e.getMessage());
            return ReturnResult.error("修改用户失败: " + e.getMessage());
        }
    }

    /**
     * 删除数据库用户（SPI {@link UserManager#dropUser}）。
     *
     * @param body {@code datasourceId} / {@code username} / {@code host}
     * @return 是否成功
     */
    @Operation(summary = "删除数据库用户")
    @DeleteMapping("users")
    public ReturnResult<Boolean> deleteUser(@RequestBody Map<String, Object> body) {
        Long datasourceId = toLong(body.get("datasourceId"));
        String username = text(body.get("username"));
        if (isBlank(username)) {
            return ReturnResult.error("username 不能为空");
        }
        try (Connection conn = datasourceService.connection(datasourceId)) {
            String protocol = protocolOf(conn);
            UserManager manager = spi(UserManager.class, protocol, datasourceId);
            if (manager == null) {
                return ReturnResult.error("内核 " + protocol + " 不支持用户管理");
            }
            manager.dropUser(username).execute();
            return ReturnResult.ok(Boolean.TRUE);
        } catch (Exception e) {
            log.warn("删除数据库用户失败: {}", e.getMessage());
            return ReturnResult.error("删除用户失败: " + e.getMessage());
        }
    }

    // ==================== 会话进程 ====================

    /**
     * 当前会话列表。
     *
     * <p>走 SPI {@link ProcessManager}：拿不到实现说明这个内核没有会话管理能力，
     * 返回 {@code supported=false}；拿到实现但取不到数据（如缺少 {@code PROCESS} 权限）
     * 返回 {@code supported=true} + {@code message}，两者语义不同，前端提示也不同。</p>
     *
     * @param datasourceId 数据源 ID
     * @return {@code supported} + {@code processes}
     */
    @Operation(summary = "数据库会话列表（SPI ProcessManager）")
    @GetMapping("processes")
    public ReturnResult<Map<String, Object>> processes(@RequestParam(required = false) Long datasourceId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        String protocol;
        try {
            protocol = protocolOfDatasource(datasourceId);
        } catch (Exception e) {
            return ReturnResult.error("读取数据源连接失败: " + e.getMessage());
        }
        ProcessManager manager = spi(ProcessManager.class, protocol, datasourceId);
        if (manager == null) {
            payload.put("supported", false);
            payload.put("processes", List.of());
            payload.put("message", "内核 " + protocol + " 未提供 ProcessManager SPI 实现");
            return ReturnResult.ok(payload);
        }
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (ProcessInfo info : manager.listProcesses()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("pid", info.id());
                row.put("user", info.user());
                row.put("host", info.host());
                row.put("db", info.database());
                row.put("command", info.command());
                row.put("time", info.time());
                row.put("info", info.info());
                rows.add(row);
            }
            payload.put("supported", true);
            payload.put("processes", rows);
            return ReturnResult.ok(payload);
        } catch (Exception e) {
            log.warn("SPI 列出数据库会话失败: {}", e.getMessage());
            payload.put("supported", true);
            payload.put("processes", List.of());
            payload.put("message", "读取会话列表失败: " + e.getMessage());
            return ReturnResult.ok(payload);
        }
    }

    /**
     * 关闭单个会话。
     *
     * @param body {@code datasourceId} / {@code pid}
     * @return 成功 / 失败计数与说明
     */
    @Operation(summary = "关闭数据库会话")
    @PostMapping("process/kill")
    public ReturnResult<Map<String, Object>> killProcess(@RequestBody Map<String, Object> body) {
        String pid = text(body.get("pid"));
        if (isBlank(pid)) {
            return ReturnResult.error("pid 不能为空");
        }
        return ReturnResult.ok(doKill(toLong(body.get("datasourceId")), List.of(pid)));
    }

    /**
     * 批量关闭会话。
     *
     * @param body {@code datasourceId} / {@code pids}
     * @return 成功 / 失败计数与说明
     */
    @Operation(summary = "批量关闭数据库会话")
    @PostMapping("processes/kill")
    public ReturnResult<Map<String, Object>> killProcesses(@RequestBody Map<String, Object> body) {
        List<String> pids = texts(body.get("pids"));
        if (pids.isEmpty()) {
            return ReturnResult.error("pids 不能为空");
        }
        return ReturnResult.ok(doKill(toLong(body.get("datasourceId")), pids));
    }

    /**
     * 逐个关闭会话并汇总结果（单个失败不影响其余）。
     *
     * @param datasourceId 数据源 ID
     * @param pids         会话 ID 列表
     * @return {@code successCount} / {@code failCount} / {@code message}
     */
    private Map<String, Object> doKill(Long datasourceId, List<String> pids) {
        Map<String, Object> result = new LinkedHashMap<>();
        ProcessManager manager;
        try {
            manager = spi(ProcessManager.class, protocolOfDatasource(datasourceId), datasourceId);
        } catch (Exception e) {
            result.put("successCount", 0);
            result.put("failCount", pids.size());
            result.put("message", "读取数据源连接失败: " + e.getMessage());
            return result;
        }
        if (manager == null) {
            result.put("successCount", 0);
            result.put("failCount", pids.size());
            result.put("message", "该内核不支持会话管理");
            return result;
        }
        int ok = 0;
        int fail = 0;
        List<String> reasons = new ArrayList<>();
        for (String pid : pids) {
            try {
                if (manager.killProcess(pid)) {
                    ok++;
                } else {
                    fail++;
                    reasons.add(pid + ": 未执行");
                }
            } catch (Exception e) {
                fail++;
                reasons.add(pid + ": " + e.getMessage());
            }
        }
        result.put("successCount", ok);
        result.put("failCount", fail);
        result.put("message", fail == 0
                ? "已关闭 " + ok + " 个会话"
                : "成功 " + ok + " 个、失败 " + fail + " 个：" + String.join("; ", reasons));
        return result;
    }

    // ==================== 慢查询 ====================

    /**
     * 慢查询摘要（按平均耗时倒序）。
     *
     * @param datasourceId 数据源 ID
     * @param minAvgMs     平均耗时下限（毫秒），可空
     * @param limit        最多返回条数，可空
     * @return {@code supported} + {@code rows}
     */
    @Operation(summary = "数据库慢查询（SPI SlowQueryManager）")
    @GetMapping("slow-queries")
    public ReturnResult<Map<String, Object>> slowQueries(
            @RequestParam(required = false) Long datasourceId,
            @RequestParam(required = false) Double minAvgMs,
            @RequestParam(required = false) Integer limit) {
        Map<String, Object> payload = new LinkedHashMap<>();
        String protocol;
        try {
            protocol = protocolOfDatasource(datasourceId);
        } catch (Exception e) {
            return ReturnResult.error("读取数据源连接失败: " + e.getMessage());
        }
        SlowQueryManager manager = spi(SlowQueryManager.class, protocol, datasourceId);
        if (manager == null) {
            payload.put("supported", false);
            payload.put("rows", List.of());
            payload.put("message", "内核 " + protocol + " 未提供 SlowQueryManager SPI 实现");
            return ReturnResult.ok(payload);
        }
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            // 默认只看 1ms 以上的语句：performance_schema 里大量 0.0x ms 的语句会把面板刷成噪声，
            // 需要全量时前端传 minAvgMs=0。
            double floor = minAvgMs == null ? DEFAULT_SLOW_MS : minAvgMs;
            int seq = 0;
            for (SlowQueryInfo info : manager.listSlowQueries(floor, limit == null ? 0 : limit)) {
                Map<String, Object> row = new LinkedHashMap<>();
                // 逐条事件来源下 digest 会重复（同一语句多个客户端/多次执行），
                // 前端表格 row-key 需要唯一字段：用行号序列（刷新全量替换，键稳定即可）。
                row.put("id", seq++);
                // 前端「语句摘要」列展示样板语句；没有样板时退回摘要哈希
                row.put("digest", info.digestText() == null ? info.digest() : info.digestText());
                row.put("avgMs", round1(info.avgMs()));
                row.put("maxMs", round1(info.maxMs()));
                row.put("calls", info.calls());
                row.put("lastAt", info.lastSeen());
                // 逐条事件 / 慢日志来源有客户端主机，聚合来源为 null（前端显示「—」）
                row.put("host", info.host());
                rows.add(row);
            }
            payload.put("supported", true);
            payload.put("rows", rows);
            return ReturnResult.ok(payload);
        } catch (Exception e) {
            // 有 SPI 实现但取不到数据（performance_schema 关闭、未装 pg_stat_statements 等）
            log.warn("SPI 读取慢查询失败: {}", e.getMessage());
            payload.put("supported", true);
            payload.put("rows", List.of());
            payload.put("message", "读取慢查询失败: " + e.getMessage());
            return ReturnResult.ok(payload);
        }
    }

    // ==================== 内部工具 ====================

    /**
     * 按勾选的权限与超级用户开关追加授权（走 SPI {@link PermissionManager#grant}）。
     *
     * <p>授权失败不吞掉用户创建结果，但会记日志——前端拿到 true 后仍会刷新列表，
     * 权限是否真加上由矩阵列体现。</p>
     */
    private void applyGrants(String protocol, Long datasourceId, String username,
                             String host, Map<String, Object> body) {
        PermissionManager manager = spi(PermissionManager.class, protocol, datasourceId);
        if (manager == null) {
            return;
        }
        List<String> grants = texts(body.get("grants"));
        boolean superUser = Boolean.TRUE.equals(body.get("superUser"))
                || "true".equalsIgnoreCase(String.valueOf(body.get("superUser")));
        try {
            if (!grants.isEmpty()) {
                manager.grant(String.join(", ", grants))
                        .toUser(username)
                        .onDatabase("*")
                        .execute();
            }
            if (superUser) {
                manager.grant("SUPER").toUser(username).onDatabase("*").execute();
            }
        } catch (Exception e) {
            log.warn("账号 {}@{} 授权失败（用户本体已保存）: {}", username, host, e.getMessage());
        }
    }

    /**
     * 从 {@code information_schema} 的授权视图里凑出账号列表（SPI 直读 {@code mysql.user} 被拒时的退路）。
     *
     * <p>普通账号读不到 {@code mysql.user}，但 {@code USER_PRIVILEGES / SCHEMA_PRIVILEGES /
     * TABLE_PRIVILEGES} 三张视图可见（内容按权限过滤）。它们只收录<b>被授权过</b>的账号，
     * 所以结果可能不全 —— 返回体里会带 message 说明，不会假装这是完整名单。</p>
     *
     * @param datasourceId 数据源 ID
     * @return 账号行（name / host）
     */
    private List<Map<String, Object>> granteeUsers(Long datasourceId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();
        String sql = "SELECT DISTINCT GRANTEE FROM information_schema.USER_PRIVILEGES"
                + " UNION SELECT DISTINCT GRANTEE FROM information_schema.SCHEMA_PRIVILEGES"
                + " UNION SELECT DISTINCT GRANTEE FROM information_schema.TABLE_PRIVILEGES";
        try (Connection conn = datasourceService.connection(datasourceId);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String grantee = rs.getString(1);
                String[] parts = splitGrantee(grantee);
                if (isBlank(parts[0]) || !seen.add(account(parts[0], parts[1]))) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", parts[0]);
                row.put("host", parts[1]);
                rows.add(row);
            }
        } catch (Exception e) {
            log.debug("information_schema 授权视图读取失败: {}", e.getMessage());
        }
        return rows;
    }

    /**
     * 拆分 {@code 'user'@'host'} 形式的 GRANTEE。
     *
     * @param grantee 原始 GRANTEE
     * @return 长度为 2 的数组：用户名、主机（解析失败时主机为空串）
     */
    private static String[] splitGrantee(String grantee) {
        String raw = grantee == null ? "" : grantee.trim();
        int at = raw.lastIndexOf("@");
        if (at < 0) {
            return new String[]{stripQuotes(raw), ""};
        }
        return new String[]{stripQuotes(raw.substring(0, at)), stripQuotes(raw.substring(at + 1))};
    }

    /**
     * strip Quotes
     * @param value 字符串
     * @return 返回值
    */
    private static String stripQuotes(String value) {
        String text = value == null ? "" : value.trim();
        while (!text.isEmpty() && (text.startsWith("'") || text.startsWith("`"))) {
            text = text.substring(1);
        }
        while (!text.isEmpty() && (text.endsWith("'") || text.endsWith("`"))) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    /**
     * 读取 {@code mysql.user} 的扩展列（SSL / 每小时限制 / 是否超级用户）。
     *
     * <p>SPI 的 {@code UserInfo} 只有 user / host / password，界面上的这几列不在 SPI 契约里。
     * 无权限读 {@code mysql.user} 时整段跳过——列表主体仍可用。</p>
     *
     * @param conn 已建立的连接
     * @return {@code user@host} → 扩展字段
     */
    private Map<String, Map<String, Object>> mysqlUserExtras(Connection conn) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        String sql = "SELECT User AS user, Host AS host, ssl_type, max_questions, max_updates,"
                + " max_connections, max_user_connections, Super_priv FROM mysql.user";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> extra = new LinkedHashMap<>();
                extra.put("sslType", rs.getString("ssl_type"));
                extra.put("maxQuestionsPerHour", rs.getObject("max_questions"));
                extra.put("maxUpdatesPerHour", rs.getObject("max_updates"));
                extra.put("maxConnectionsPerHour", rs.getObject("max_connections"));
                extra.put("maxUserConnections", rs.getObject("max_user_connections"));
                String superPriv = rs.getString("Super_priv");
                extra.put("isSuper", "Y".equalsIgnoreCase(superPriv));
                result.put(account(rs.getString("user"), rs.getString("host")), extra);
            }
        } catch (Exception e) {
            log.debug("跳过 mysql.user 扩展列（权限不足或非 MySQL 内核）: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 取数据源的方言协议（用一条短连接读完元数据就放掉）。
     *
     * <p>{@link DatasourceService#connection(Long)} 在 TTL 内复用同一个物理连接，
     * 所以每次只借一小会儿、语句结束就还，避免和后面的 SPI 调用交错。</p>
     *
     * @param datasourceId 数据源 ID
     * @return 小写协议名
     * @throws Exception 连接或元数据读取失败
     */
    private String protocolOfDatasource(Long datasourceId) throws Exception {
        try (Connection conn = datasourceService.connection(datasourceId)) {
            return protocolOf(conn);
        }
    }

    /**
     * 读取账号的扩展列（SSL / 每小时限制 / 是否超级用户），读不到就是空表。
     *
     * @param datasourceId 数据源 ID
     * @return {@code user@host} → 扩展字段
     */
    private Map<String, Map<String, Object>> userExtras(Long datasourceId) {
        try (Connection conn = datasourceService.connection(datasourceId)) {
            return mysqlUserExtras(conn);
        } catch (Exception e) {
            log.debug("读取账号扩展列失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 解析连接的方言协议（与 SPI 扩展键同一套命名：mysql / mariadb / postgresql / oracle / sqlserver）。
     *
     * @param conn 连接
     * @return 小写协议名
     * @throws SQLException 元数据读取失败
     */
    private String protocolOf(Connection conn) throws SQLException {
        DatabaseMetaData md = conn.getMetaData();
        String url = md.getURL();
        if (url != null && url.startsWith("jdbc:")) {
            String rest = url.substring(5);
            int colon = rest.indexOf(':');
            if (colon > 0) {
                return rest.substring(0, colon).toLowerCase(Locale.ROOT);
            }
        }
        String product = md.getDatabaseProductName();
        String lower = product == null ? "" : product.toLowerCase(Locale.ROOT);
        if (lower.contains("mariadb")) {
            return "mariadb";
        }
        if (lower.contains("mysql")) {
            return "mysql";
        }
        if (lower.contains("postgres")) {
            return "postgresql";
        }
        if (lower.contains("oracle")) {
            return "oracle";
        }
        if (lower.contains("sql server") || lower.contains("mssql")) {
            return "sqlserver";
        }
        return lower;
    }

    /**
     * 解析引擎 SPI 扩展（与 {@code JdbcEngine.resolveManager} 同源：协议键 + 注入数据源）。
     *
     * @param type         SPI 接口
     * @param protocol     方言协议
     * @param datasourceId 数据源 ID（用于构造可复用连接的数据源）
     * @param <T>          SPI 类型
     * @return 实现实例；该内核无实现时为 null
     */
    private <T> T spi(Class<T> type, String protocol, Long datasourceId) {
        try {
            ServiceProvider<T> provider = ServiceProvider.of(type);
            T ext = protocol == null ? null : provider.getExtension(protocol);
            if (ext == null && "mariadb".equals(protocol)) {
                // MariaDB 没有单独的 UserManager 实现，走 MySQL 那套语句完全兼容
                ext = provider.getExtension("mysql");
            }
            if (ext instanceof DataSourceAware aware) {
                aware.setDataSource(dataSourceOf(datasourceId));
            }
            return ext;
        } catch (Exception e) {
            log.debug("SPI {} 解析失败: {}", type.getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * 给 SPI 实现注入的 {@link DataSource}：每次 {@code getConnection()} 都走
     * {@link DatasourceService#connection(Long)} 的缓存代理（其 close 是空操作，重复取用安全）。
     *
     * @param datasourceId 数据源 ID
     * @return 数据源
     */
    private DataSource dataSourceOf(Long datasourceId) {
        return new DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                try {
                    return datasourceService.connection(datasourceId);
                } catch (SQLException e) {
                    throw e;
                } catch (Exception e) {
                    throw new SQLException(e.getMessage(), e);
                }
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return getConnection();
            }

            @Override
            public PrintWriter getLogWriter() {
                return null;
            }

            @Override
            public void setLogWriter(PrintWriter out) {
                // 代理数据源不写日志
            }

            @Override
            public void setLoginTimeout(int seconds) {
                // 超时由 DatasourceService 的连接构建统一控制
            }

            @Override
            public int getLoginTimeout() {
                return 0;
            }

            @Override
            public java.util.logging.Logger getParentLogger() {
                return java.util.logging.Logger.getGlobal();
            }

            @Override
            public <X> X unwrap(Class<X> iface) throws SQLException {
                throw new SQLException("代理数据源不支持 unwrap: " + iface);
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) {
                return false;
            }
        };
    }

    /**
     * account
     * @param host 设置主机地址
     * @param user 字符串
     * @return 返回值
    */
    private static String account(String user, String host) {
        return (user == null ? "" : user) + "@" + (host == null ? "" : host);
    }

    /**
     * quote Literal
     * @param value 字符串
     * @return 返回值
    */
    private static String quoteLiteral(String value) {
        return "'" + (value == null ? "" : value.replace("\\", "\\\\").replace("'", "''")) + "'";
    }

    /**
     * is Blank
     * @param value 字符串
     * @return 返回值
    */
    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 毫秒保留一位小数（前端只展示，不做二次计算）。
     *
     * @param value 原始毫秒
     * @return 四舍五入到 0.1ms
     */
    private static double round1(double value) {
        return Math.round(value * 10D) / 10D;
    }

    /**
     * text
     * @param value 对象
     * @return 返回值
    */
    private static String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    /**
     * to Long
     * @param value 对象
     * @return 返回值
    */
    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String raw = String.valueOf(value).trim();
        if (raw.isEmpty() || "null".equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    /**
     * texts
     * @param value 对象
     * @return 返回值
    */
    private static List<String> texts(Object value) {
        List<String> result = new ArrayList<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                String text = item == null ? null : String.valueOf(item).trim();
                if (text != null && !text.isEmpty()) {
                    result.add(text);
                }
            }
        }
        return result;
    }

    /**
     * close Quietly
     * @param conn 参数
     * @return 返回值
    */
    private static void closeQuietly(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception ignored) {
                // 代理连接的 close 是空操作，失败也无所谓
            }
        }
    }
}
