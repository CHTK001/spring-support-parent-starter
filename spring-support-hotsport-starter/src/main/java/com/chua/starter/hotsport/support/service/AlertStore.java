package com.chua.starter.hotsport.support.service;

import com.chua.starter.hotsport.support.config.HotsportProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 告警规则 / 通知渠道 / 告警事件的 MySQL 持久化（可选写穿层）。
 *
 * <p><b>为什么需要它</b>：告警本体的内存态（{@link AlertMonitorService} 的
 * {@code ConcurrentHashMap} / 环形缓冲）在服务重启后会全部丢失，规则、渠道、
 * 事件历史都是运维关心的资产。本类把三者同步落库，重启后自动回灌。</p>
 *
 * <p><b>什么时候激活</b>：复用 {@code plugin.hotsport.apm.storage.*} 的同源配置，
 * 仅当存储类型为 {@code mysql} 且 JDBC URL 非空时落库。其它后端（sqlite /
 * inmemory / noop）保持纯内存语义，本类静默不工作。</p>
 *
 * <p><b>为什么不会拖垮宿主</b>：</p>
 * <ul>
 *   <li>激活前的连通性 / 建表校验失败只记日志并保持非激活，观测组件照常运行；</li>
 *   <li>每次读写都是一次短连接，超时 5s，任何 SQL 异常只记日志不向上抛；</li>
 *   <li>写穿是尽力而为——内存态永远是权威，DB 坏了只是「历史查不到」，不阻塞告警主流程。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class AlertStore {

    /**
     * 事件表保留的最大行数（超出按最旧删除，防止无限膨胀）。
     */
    private static final int MAX_EVENT_ROWS = 2000;

    /**
     * 连接 / 查询超时（毫秒）：观测组件绝不为一次持久化挂太久。
     */
    private static final int TIMEOUT_MS = 5000;

    /**
     * 是否已激活（mysql 后端且建表成功）。
     */
    private volatile boolean active = false;

    private volatile String url = "";
    private volatile String user = "";
    private volatile String password = "";

    /**
     * 建表语句：全部幂等（IF NOT EXISTS），重复启动安全。
     * <p>id 由 DB AUTO_INCREMENT 分配——多实例共享同一张表时，应用级计数器无法跨
     * 实例协调，实测两实例并发新增会分配相同 id，INSERT 撞主键静默丢数据；DB 自增
     * 是唯一可靠的全局 id 协调点。INSERT 后经 {@code RETURN_GENERATED_KEYS} 回读
     * 新 id 同步内存（事件 ack/mute 仍按同一 id 寻址）；规则/渠道另有业务唯一键
     * （metric/name）先查后写合并同名。</p>
     */
    private static final String[] DDL = {
            "CREATE TABLE IF NOT EXISTS alert_rules ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT,"
                    + "metric VARCHAR(64) NOT NULL,"
                    + "threshold DOUBLE NOT NULL DEFAULT 0,"
                    + "severity VARCHAR(32),"
                    + "debounce_ms BIGINT DEFAULT 5000,"
                    + "enabled TINYINT(1) DEFAULT 1,"
                    + "PRIMARY KEY (id),"
                    + "UNIQUE KEY uk_alert_rule_metric (metric)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS alert_channels ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT,"
                    + "name VARCHAR(128),"
                    + "type VARCHAR(32),"
                    + "url VARCHAR(1024),"
                    + "secret VARCHAR(256),"
                    + "enabled TINYINT(1) DEFAULT 1,"
                    + "PRIMARY KEY (id),"
                    + "UNIQUE KEY uk_alert_channel_name (name)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
            "CREATE TABLE IF NOT EXISTS alert_events ("
                    + "id BIGINT NOT NULL AUTO_INCREMENT,"
                    + "ts BIGINT DEFAULT 0,"
                    + "metric VARCHAR(64),"
                    + "current_value DOUBLE DEFAULT 0,"
                    + "threshold DOUBLE DEFAULT 0,"
                    + "severity VARCHAR(32),"
                    + "message VARCHAR(512),"
                    + "status VARCHAR(32),"
                    + "status_ts BIGINT DEFAULT 0,"
                    + "fingerprint VARCHAR(128),"
                    + "silenced_until BIGINT DEFAULT 0,"
                    + "repeat_count BIGINT DEFAULT 1,"
                    + "PRIMARY KEY (id),"
                    + "KEY idx_alert_event_ts (ts)"
                    + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
    };

    /**
     * 渠道名称定位（业务唯一键，保证多实例共享同一张表时按 name 寻址）。
     */
    private static final String FIND_CHANNEL = "SELECT id FROM alert_channels WHERE name = ?";

    /**
     * 渠道写入（新 id）。
     */
    private static final String INSERT_CHANNEL = "INSERT INTO alert_channels "
            + "(name, type, url, secret, enabled) "
            + "VALUES (?,?,?,?,?)";

    /**
     * 渠道更新（按已有 id）。
     */
    private static final String UPDATE_CHANNEL = "UPDATE alert_channels SET "
            + "type = ?, url = ?, secret = ?, enabled = ? WHERE id = ?";

    /**
     * 事件写入。
     */
    private static final String INSERT_EVENT = "INSERT INTO alert_events "
            + "(ts, metric, current_value, threshold, severity, message, status,"
            + " status_ts, fingerprint, silenced_until, repeat_count) "
            + "VALUES (?,?,?,?,?,?,?,?,?,?,?)";

    /**
     * 事件状态更新（ack / mute / ignore 共用）。
     */
    private static final String UPDATE_EVENT_STATUS = "UPDATE alert_events "
            + "SET status = ?, status_ts = ?, silenced_until = ? WHERE id = ?";

    /**
     * 事件去抖静默期内的滚动更新（同指纹重复触发：累加次数并刷新当前值）。
     */
    private static final String UPDATE_EVENT_RECENT = "UPDATE alert_events "
            + "SET current_value = ?, ts = ?, repeat_count = ? WHERE id = ?";

    /**
     * 历史事件查询：按时间倒序分页（超出内存环形缓冲的历史）。
     */
    private static final String SELECT_EVENTS = "SELECT id, ts, metric, current_value, threshold,"
            + " severity, message, status, status_ts, fingerprint, silenced_until, repeat_count"
            + " FROM alert_events ORDER BY ts DESC, id DESC LIMIT ? OFFSET ?";

    /**
     * 最旧事件裁剪：只保留最近 MAX_EVENT_ROWS 行。
     */
    private static final String TRIM_EVENTS = "DELETE FROM alert_events WHERE id < "
            + "(SELECT m FROM (SELECT MAX(id) - " + MAX_EVENT_ROWS + " AS m FROM alert_events) t)";

    private final HotsportProperties properties;

    /**
     * @param properties Hotsport 配置（取 apm.storage.* 的 JDBC 参数）
     */
    public AlertStore(HotsportProperties properties) {
        this.properties = properties;
    }

    /**
     * 尝试激活：mysql 后端 + URL 非空时建表并置为可用。
     *
     * <p>任何失败只记日志，保持非激活（纯内存语义），观测组件照常启动。</p>
     */
    public synchronized void start() {
        try {
            HotsportProperties.Storage storage = properties.getApm().getStorage();
            String type = storage == null ? "" : storage.getType();
            if (!"mysql".equalsIgnoreCase(trim(type))) {
                log.info("[Hotsport] 告警持久化未启用（存储类型 {}，仅 mysql 落库）",
                        type == null || type.isBlank() ? "未配置" : type);
                return;
            }
            String u = trim(storage.getUrl());
            if (u.isEmpty()) {
                log.warn("[Hotsport] 告警持久化未启用：mysql 后端缺少 JDBC URL");
                return;
            }
            url = u;
            user = trim(storage.getUsername());
            password = storage.getPassword() == null ? "" : storage.getPassword();
            try (Connection conn = connect()) {
                try (Statement st = conn.createStatement()) {
                    for (String ddl : DDL) {
                        st.execute(ddl);
                    }
                }
                migrateChannelNameKey(conn);
                migrateAutoIncrement(conn);
            }
            active = true;
            log.info("[Hotsport] 告警持久化已激活（MySQL: {}）", url);
        } catch (Exception e) {
            active = false;
            log.warn("[Hotsport] 告警持久化激活失败，降级为纯内存: {}", e.getMessage());
        }
    }

    /**
     * 幂等迁移：给已存在的渠道表补 name 唯一键。
     *
     * <p>老库的 alert_channels 建表时没有 uk_alert_channel_name（{@code CREATE TABLE
     * IF NOT EXISTS} 不会改已有表结构），而多实例共享该表时 name 唯一键是先查后写
     * 策略的落点——没有它，并发新增渠道仍可能因各实例独立 id 计数器而错行。此处
     * 尝试 {@code ADD UNIQUE KEY}，键已存在（重复启动 / 新库已建）则静默跳过。</p>
     *
     * @param conn 连接
     */
    private static void migrateChannelNameKey(Connection conn) {
        try (Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE alert_channels "
                    + "ADD UNIQUE KEY uk_alert_channel_name (name)");
        } catch (SQLException e) {
            // 1061 = Duplicate key name（已存在，预期内）；其它错误才告警
            if (e.getErrorCode() != 1061 && !String.valueOf(e.getMessage()).contains("Duplicate key name")) {
                log.warn("[Hotsport] 渠道 name 唯一键迁移失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 幂等迁移：老库三张表的 id 列补 AUTO_INCREMENT。
     *
     * <p>新库由 DDL 直接建出自增 id；老库（应用显式写 id 时代建的）需在这里补改。
     * 多实例共享同一张表时 id 必须由 DB 全局分配，否则两实例并发新增必撞主键
     * （实测 Duplicate entry for key 'PRIMARY' 静默丢数据）。重复执行 MODIFY
     * 是幂等无操作。</p>
     *
     * @param conn 连接
     */
    private static void migrateAutoIncrement(Connection conn) {
        String[] tables = {"alert_rules", "alert_channels", "alert_events"};
        try (Statement st = conn.createStatement()) {
            for (String t : tables) {
                st.execute("ALTER TABLE " + t + " MODIFY id BIGINT NOT NULL AUTO_INCREMENT");
            }
        } catch (SQLException e) {
            log.warn("[Hotsport] id 自增迁移失败: {}", e.getMessage());
        }
    }

    /**
     * @return 是否已激活（否则调用方不需要做任何落库动作）
     */
    public boolean isActive() {
        return active;
    }

    /**
     * 启动回灌：读取持久化的规则（DB 为空返回空表，由调用方决定是否写回默认）。
     *
     * @return 规则列表
     */
    public List<AlertMonitorService.AlertRule> loadRules() {
        List<AlertMonitorService.AlertRule> out = new ArrayList<>();
        if (!active) {
            return out;
        }
        String sql = "SELECT id, metric, threshold, severity, debounce_ms, enabled"
                + " FROM alert_rules";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                AlertMonitorService.AlertRule rule = new AlertMonitorService.AlertRule();
                rule.id = rs.getLong("id");
                rule.metric = rs.getString("metric");
                rule.threshold = rs.getDouble("threshold");
                rule.severity = rs.getString("severity");
                rule.debounceMs = rs.getLong("debounce_ms");
                rule.enabled = rs.getBoolean("enabled");
                out.add(rule);
            }
        } catch (Exception e) {
            log.warn("[Hotsport] 告警规则回灌失败: {}", e.getMessage());
        }
        return out;
    }

    /**
     * 启动回灌：读取持久化的通知渠道。
     *
     * @return 渠道列表
     */
    public List<AlertMonitorService.AlertChannel> loadChannels() {
        List<AlertMonitorService.AlertChannel> out = new ArrayList<>();
        if (!active) {
            return out;
        }
        String sql = "SELECT id, name, type, url, secret, enabled FROM alert_channels";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                AlertMonitorService.AlertChannel ch = new AlertMonitorService.AlertChannel();
                ch.id = rs.getLong("id");
                ch.name = rs.getString("name");
                ch.type = rs.getString("type");
                ch.url = rs.getString("url");
                ch.secret = rs.getString("secret");
                ch.enabled = rs.getBoolean("enabled");
                out.add(ch);
            }
        } catch (Exception e) {
            log.warn("[Hotsport] 告警渠道回灌失败: {}", e.getMessage());
        }
        return out;
    }

    /**
     * 启动回灌：读取最近事件（按时间倒序，供环形缓冲初始填充）。
     *
     * @param limit 条数
     * @return 事件列表（时间倒序）
     */
    public List<AlertMonitorService.AlertEvent> loadRecentEvents(int limit) {
        List<AlertMonitorService.AlertEvent> out = new ArrayList<>();
        if (!active || limit <= 0) {
            return out;
        }
        String sql = "SELECT id, ts, metric, current_value, threshold, severity, message, status,"
                + " status_ts, fingerprint, silenced_until, repeat_count"
                + " FROM alert_events ORDER BY ts DESC, id DESC LIMIT ?";
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AlertMonitorService.AlertEvent e = new AlertMonitorService.AlertEvent();
                    e.id = rs.getLong("id");
                    e.ts = rs.getLong("ts");
                    e.metric = rs.getString("metric");
                    e.current = rs.getDouble("current_value");
                    e.threshold = rs.getDouble("threshold");
                    e.severity = rs.getString("severity");
                    e.message = rs.getString("message");
                    e.status = rs.getString("status");
                    e.statusTs = rs.getLong("status_ts");
                    e.fingerprint = rs.getString("fingerprint");
                    e.silencedUntil = rs.getLong("silenced_until");
                    e.repeatCount = rs.getLong("repeat_count");
                    out.add(e);
                }
            }
        } catch (Exception e) {
            log.warn("[Hotsport] 告警事件回灌失败: {}", e.getMessage());
        }
        return out;
    }

    /**
     * 历史事件分页查询（DB 全量历史，超越内存环形缓冲）。
     *
     * @param limit  每页条数（1..200）
     * @param offset 起始偏移（按时间倒序）
     * @return 事件列表（时间倒序）；失败返回空表
     */
    public List<AlertMonitorService.AlertEvent> history(int limit, int offset) {
        List<AlertMonitorService.AlertEvent> out = new ArrayList<>();
        if (!active) {
            return out;
        }
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(SELECT_EVENTS)) {
            ps.setInt(1, Math.min(200, Math.max(1, limit)));
            ps.setInt(2, Math.max(0, offset));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AlertMonitorService.AlertEvent e = new AlertMonitorService.AlertEvent();
                    e.id = rs.getLong("id");
                    e.ts = rs.getLong("ts");
                    e.metric = rs.getString("metric");
                    e.current = rs.getDouble("current_value");
                    e.threshold = rs.getDouble("threshold");
                    e.severity = rs.getString("severity");
                    e.message = rs.getString("message");
                    e.status = rs.getString("status");
                    e.statusTs = rs.getLong("status_ts");
                    e.fingerprint = rs.getString("fingerprint");
                    e.silencedUntil = rs.getLong("silenced_until");
                    e.repeatCount = rs.getLong("repeat_count");
                    out.add(e);
                }
            }
        } catch (Exception e) {
            log.warn("[Hotsport] 告警历史查询失败: {}", e.getMessage());
        }
        return out;
    }

    /**
     * 规则写穿（新增或覆盖，按 metric 定位）。
     *
     * <p><b>为什么先查再写、而不是 ON DUPLICATE KEY</b>：UPSERT 同时携带
     * {@code id}（应用分配的递增号）与 {@code metric}（业务唯一键）两个唯一键，
     * MariaDB 在双键同时冲突时更新哪一行依赖索引检查顺序，多实例共享同一张表时
     * 极易更新错行（实测出现规则值写入其它 metric 行的脏数据）。改为显式按
     * metric 定位：行已存在用其 id 更新，不存在才以 DB 自增 id 插入。</p>
     *
     * <p>id 列已改为 {@code AUTO_INCREMENT}：INSERT 不带 id，落库后经
     * {@code RETURN_GENERATED_KEYS} 回读 DB 分配的真实 id 返回给调用方，由调用方
     * 同步内存——应用级计数器无法跨实例协调，DB 自增是唯一全局协调点。</p>
     *
     * @param rule 规则
     * @return DB 实际生效的规则 id（更新路径返回 DB id，插入路径返回自增 id；失败 -1）
     */
    public long saveRule(AlertMonitorService.AlertRule rule) {
        if (!active || rule == null) {
            return -1L;
        }
        String find = "SELECT id FROM alert_rules WHERE metric = ?";
        String insert = "INSERT INTO alert_rules "
                + "(metric, threshold, severity, debounce_ms, enabled) "
                + "VALUES (?,?,?,?,?)";
        String update = "UPDATE alert_rules SET threshold = ?, severity = ?, "
                + "debounce_ms = ?, enabled = ? WHERE id = ?";
        try (Connection conn = connect()) {
            try (PreparedStatement psFind = conn.prepareStatement(find)) {
                psFind.setString(1, rule.metric);
                try (ResultSet rs = psFind.executeQuery()) {
                    if (rs.next()) {
                        long dbId = rs.getLong("id");
                        try (PreparedStatement ps = conn.prepareStatement(update)) {
                            ps.setDouble(1, rule.threshold);
                            ps.setString(2, rule.severity);
                            ps.setLong(3, rule.debounceMs);
                            ps.setBoolean(4, rule.enabled);
                            ps.setLong(5, dbId);
                            ps.executeUpdate();
                        }
                        return dbId;
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, rule.metric);
                ps.setDouble(2, rule.threshold);
                ps.setString(3, rule.severity);
                ps.setLong(4, rule.debounceMs);
                ps.setBoolean(5, rule.enabled);
                ps.executeUpdate();
                return generatedId(ps, conn, find, rule.metric);
            }
        } catch (Exception e) {
            // 并发同 metric 同时 INSERT：唯一键冲突说明另一实例先落库，按业务键回查合并
            if (isDuplicateKey(e)) {
                long id = refindId(find, rule.metric);
                if (id > 0) {
                    return id;
                }
            }
            log.warn("[Hotsport] 告警规则落库失败: {}", e.getMessage());
        }
        return -1L;
    }

    /**
     * 规则删除。
     *
     * @param metric 指标键
     */
    public void deleteRule(String metric) {
        if (!active || metric == null) {
            return;
        }
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM alert_rules WHERE metric = ?")) {
            ps.setString(1, metric);
            ps.executeUpdate();
        } catch (Exception e) {
            log.warn("[Hotsport] 告警规则删除落库失败: {}", e.getMessage());
        }
    }

    /**
     * 渠道写穿（新增或覆盖，按 name 定位）。
     *
     * <p><b>为什么先查再写而不是 UPSERT</b>：渠道 id 由各实例独立 {@code AtomicLong}
     * 分配，无业务键时两实例并发新增会分配相同 id，UPSERT 后写静默覆盖先写。
     * 改为按 name 唯一键定位：存在则沿用其 id 更新（返回该 id），不存在则以 DB
     * 自增 id 插入（{@code RETURN_GENERATED_KEYS} 回读，杜绝跨实例撞主键）。</p>
     *
     * @param channel 渠道
     * @return DB 实际生效的渠道 id（更新路径返回 DB id，插入路径返回自增 id；失败 -1）
     */
    public long saveChannel(AlertMonitorService.AlertChannel channel) {
        if (!active || channel == null) {
            return -1L;
        }
        try (Connection conn = connect()) {
            try (PreparedStatement psFind = conn.prepareStatement(FIND_CHANNEL)) {
                psFind.setString(1, channel.name);
                try (ResultSet rs = psFind.executeQuery()) {
                    if (rs.next()) {
                        long dbId = rs.getLong("id");
                        try (PreparedStatement ps = conn.prepareStatement(UPDATE_CHANNEL)) {
                            ps.setString(1, channel.type);
                            ps.setString(2, channel.url);
                            ps.setString(3, channel.secret);
                            ps.setBoolean(4, channel.enabled);
                            ps.setLong(5, dbId);
                            ps.executeUpdate();
                        }
                        return dbId;
                    }
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(INSERT_CHANNEL, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, channel.name);
                ps.setString(2, channel.type);
                ps.setString(3, channel.url);
                ps.setString(4, channel.secret);
                ps.setBoolean(5, channel.enabled);
                ps.executeUpdate();
                return generatedId(ps, conn, FIND_CHANNEL, channel.name);
            }
        } catch (Exception e) {
            // 并发同名同时 INSERT：唯一键（uk_alert_channel_name）冲突说明另一实例
            // 先落库，按 name 回查它的 id 合并，而不是凭空丢数据
            if (isDuplicateKey(e)) {
                long id = refindId(FIND_CHANNEL, channel.name);
                if (id > 0) {
                    return id;
                }
            }
            log.warn("[Hotsport] 告警渠道落库失败: {}", e.getMessage());
        }
        return -1L;
    }

    /**
     * 渠道删除（DB 为权威）。
     *
     * @param id 渠道 ID
     * @return 是否真正删除了行（affected > 0）；持久化未激活时返回 false
     */
    public boolean deleteChannel(long id) {
        if (!active) {
            return false;
        }
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM alert_channels WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            log.warn("[Hotsport] 告警渠道删除落库失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 事件写入（新增），id 由 DB 自增；顺带裁剪最旧行防止表无限膨胀。
     *
     * <p>id 由 DB AUTO_INCREMENT 分配（多实例共享事件表时唯一可靠协调点），INSERT
     * 后经 {@code RETURN_GENERATED_KEYS} 回读，调用方用它修正内存事件 id，保证
     * ack/mute/touch 始终按 DB 同一 id 寻址。</p>
     *
     * @param event 事件
     * @return DB 分配的事件 id；持久化未激活或失败返回 -1
     */
    public long saveEvent(AlertMonitorService.AlertEvent event) {
        if (!active || event == null) {
            return -1L;
        }
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(INSERT_EVENT, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, event.ts);
            ps.setString(2, event.metric);
            ps.setDouble(3, event.current);
            ps.setDouble(4, event.threshold);
            ps.setString(5, event.severity);
            ps.setString(6, event.message);
            ps.setString(7, event.status);
            ps.setLong(8, event.statusTs);
            ps.setString(9, event.fingerprint);
            ps.setLong(10, event.silencedUntil);
            ps.setLong(11, event.repeatCount);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys != null && keys.next() ? keys.getLong(1) : -1L;
            }
        } catch (Exception e) {
            log.debug("[Hotsport] 告警事件落库失败: {}", e.getMessage());
            return -1L;
        }
    }

    /**
     * 事件状态更新（ack / mute / ignore）。
     *
     * @param id            事件 ID
     * @param status        新状态
     * @param statusTs      状态变更时间
     * @param silencedUntil 静默截点（未静默传 0）
     */
    public void updateEventStatus(long id, String status, long statusTs, long silencedUntil) {
        if (!active) {
            return;
        }
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(UPDATE_EVENT_STATUS)) {
            ps.setString(1, status);
            ps.setLong(2, statusTs);
            ps.setLong(3, silencedUntil);
            ps.setLong(4, id);
            ps.executeUpdate();
        } catch (Exception e) {
            log.debug("[Hotsport] 告警事件状态落库失败: {}", e.getMessage());
        }
    }

    /**
     * 事件滚动更新（同指纹静默期内重复触发：刷新当前值并累加次数）。
     *
     * @param event 最新状态的事件（id 用于定位）
     */
    public void touchEvent(AlertMonitorService.AlertEvent event) {
        if (!active || event == null) {
            return;
        }
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(UPDATE_EVENT_RECENT)) {
            ps.setDouble(1, event.current);
            ps.setLong(2, event.ts);
            ps.setLong(3, event.repeatCount);
            ps.setLong(4, event.id);
            ps.executeUpdate();
        } catch (Exception e) {
            log.debug("[Hotsport] 告警事件滚动更新落库失败: {}", e.getMessage());
        }
    }

    /**
     * @return 事件表当前行数（运维看板用；查询失败返回 -1）
     */
    public long eventCount() {
        if (!active) {
            return 0L;
        }
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM alert_events");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (Exception e) {
            return -1L;
        }
    }

    /**
     * 裁剪最旧事件（保留最近 MAX_EVENT_ROWS 行）。
     */
    public void trimEvents() {
        if (!active) {
            return;
        }
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(TRIM_EVENTS)) {
            int n = ps.executeUpdate();
            if (n > 0) {
                log.debug("[Hotsport] 告警事件裁剪 {} 行", n);
            }
        } catch (Exception e) {
            log.debug("[Hotsport] 告警事件裁剪失败: {}", e.getMessage());
        }
    }

    /**
     * 从 {@code RETURN_GENERATED_KEYS} 回读 DB 自增 id。
     *
     * <p>绝大多数驱动对 AUTO_INCREMENT 都会返回生成键；万一驱动行为异常（返回空集），
     * 按业务唯一键回查兜底，保证调用方始终拿到真实 id。</p>
     *
     * @param ps      已执行 INSERT 的语句（RETURN_GENERATED_KEYS）
     * @param conn    同连接（回查用）
     * @param findSql 业务键定位 SQL（一个 ?）
     * @param key     业务键值
     * @return id；回读失败返回 -1
     * @throws SQLException 连接/SQL 异常
     */
    private static long generatedId(PreparedStatement ps, Connection conn, String findSql, String key)
            throws SQLException {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            if (keys != null && keys.next()) {
                return keys.getLong(1);
            }
        }
        try (PreparedStatement psFind = conn.prepareStatement(findSql)) {
            psFind.setString(1, key);
            try (ResultSet rs = psFind.executeQuery()) {
                return rs.next() ? rs.getLong("id") : -1L;
            }
        }
    }

    /**
     * 判定唯一键冲突异常（并发 INSERT 撞业务唯一键：duplicate entry/key ...）。
     */
    private static boolean isDuplicateKey(Exception e) {
        String msg = String.valueOf(e.getMessage()).toLowerCase();
        return msg.contains("duplicate") && (msg.contains("key") || msg.contains("entry"));
    }

    /**
     * 并发唯一键冲突后的回查：另一实例刚落库，按业务键取回它的 id（合并语义）。
     *
     * @param findSql 定位 SQL（含一个 ? 业务键）
     * @param key     业务键值
     * @return DB id；仍查不到返回 -1
     */
    private long refindId(String findSql, String key) {
        try (Connection conn = connect();
             PreparedStatement ps = conn.prepareStatement(findSql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong("id") : -1L;
            }
        } catch (Exception e) {
            log.warn("[Hotsport] 并发冲突回查失败: {}", e.getMessage());
            return -1L;
        }
    }

    /**
     * 短连接（每次操作独立，超时 5s；观测组件绝不为持久化挂太久）。
     *
     * @return 连接
     * @throws SQLException 连接失败
     */
    private Connection connect() throws SQLException {
        java.util.Properties info = new java.util.Properties();
        info.setProperty("user", user);
        info.setProperty("password", password);
        info.setProperty("connectTimeout", String.valueOf(TIMEOUT_MS));
        info.setProperty("socketTimeout", String.valueOf(TIMEOUT_MS));
        info.setProperty("useSSL", "false");
        info.setProperty("serverTimezone", "Asia/Shanghai");
        return DriverManager.getConnection(url, info);
    }

    /**
     * 环境/运行时信息（运维排查用）。
     *
     * @return 键值对：type / active / url / rows（事件行数）
     */
    public Map<String, Object> info() {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("type", active ? "mysql" : "memory");
        m.put("active", active);
        m.put("url", active ? url : "");
        m.put("rows", eventCount());
        return m;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}