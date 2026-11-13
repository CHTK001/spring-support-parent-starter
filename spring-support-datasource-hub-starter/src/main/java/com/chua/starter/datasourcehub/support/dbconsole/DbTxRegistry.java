package com.chua.starter.datasourcehub.support.dbconsole;

import com.chua.starter.datasourcehub.support.model.DatasourceJdbcInfo;
import com.chua.starter.datasourcehub.support.service.DatasourceService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 显式事务会话注册表。
 *
 * <p>把 {@link DbTxSession} 按 txId 存起来，供 begin / commit / rollback /
 * 空闲回收共用。</p>
 *
 * <p><b>为什么必须有空闲回收</b>：事务会话把一条连接从「一次请求」
 * 拉长到「一次会话」。用户开了事务然后忘了提交，那条连接就永远不还池 ——
 * 表现是跑一阵之后「连接池耗尽」，而根因在某个用户忘了点提交。
 * 没有超时回收，一个手滑就足以拖垮整个实例。</p>
 *
 * <p><b>为什么要有并发上限</b>：同上，只是从「忘了提交」变成「正常批量导入」，
 * 也要有闸门。上限按连接池能力取，且开启时明确报错，
 * 而不是等池耗尽后报一句语焉不详的「连接获取超时」。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@Component
public class DbTxRegistry {

    /** 日志 */
    private static final Logger LOG = LoggerFactory.getLogger(DbTxRegistry.class);

    /** 空闲多久回收（分钟） */
    private static final long IDLE_TIMEOUT_MINUTES =
            // [P3C A20 豁免]
            Long.getLong("monitor.db.tx.idleTimeoutMinutes", 10L);

    /** 同时最多开几个事务会话 */
    private static final int MAX_SESSIONS =
            Integer.getInteger("monitor.db.tx.maxSessions", 8);

    /** 正在持有连接的会话 */
    private final Map<String, DbTxSession> sessions = new ConcurrentHashMap<>();

    /** 数据源服务，用于开启事务时拿连接 */
    private final DatasourceService datasourceService;

    /**
     * 构造注册表。
     *
     * @param datasourceService 数据源服务
     */
    public DbTxRegistry(DatasourceService datasourceService) {
        this.datasourceService = datasourceService;
    }

    /**
     * 开启一个事务会话。
     *
     * @param datasourceId 数据源 ID
     * @param database     库名，可为空
     * @return 新建的事务会话
     * @throws Exception 连接获取失败
     */
    public DbTxSession begin(Long datasourceId, String database) throws Exception {
        purgeIdle();
        if (sessions.size() >= MAX_SESSIONS) {
            throw new IllegalStateException("同时开启的事务数已达上限 " + MAX_SESSIONS
                    + "，请先提交或回滚已有事务");
        }
        java.sql.Connection conn = dedicatedConnection(datasourceId);
        try {
            conn.setAutoCommit(false);
        } catch (java.sql.SQLException e) {
            conn.close();
            throw e;
        }
        String txId = UUID.randomUUID().toString();
        DbTxSession session = new DbTxSession(txId, datasourceId, database, conn);
        sessions.put(txId, session);
        LOG.info("开启事务会话 {}（数据源 {}，库 {}）", txId, datasourceId, database);
        return session;
    }

    /**
     * 为事务开一条**独占**连接。
     *
     * <p><b>这里绝对不能用 {@code datasourceService.connection(id)}</b>：那返回的是
     * 按数据源缓存、并在 TTL 内被所有请求复用的<b>同一个物理连接</b>，
     * 而且 {@code close()} 被屏蔽成空操作。对它做 {@code setAutoCommit(false)}
     * 会把**这个数据源上所有并发请求**一起拖进同一个事务，而且因为连接从不真正
     * 关闭，{@code autoCommit} 也永远不会恢复 —— 表现为事务结束后所有普通请求
     * 报 MariaDB 1412「Table definition has changed」，改动全部静默不落库。</p>
     *
     * <p>实测踩过：2026-10-05 第一版就是复用它，事务一提交，后续所有非事务
     * UPDATE 都变成「返回成功但数据没变」。事务必须独占连接。</p>
     *
     * @param datasourceId 数据源 ID
     * @return 独占连接，调用方负责关闭
     * @throws Exception 数据源不可用于 JDBC 或建连失败
     */
    private java.sql.Connection dedicatedConnection(Long datasourceId) throws Exception {
        DatasourceJdbcInfo info = datasourceService.jdbcInfo(datasourceId);
        String url = info.url();
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("数据源没有 JDBC URL，显式事务不可用: " + datasourceId);
        }
        if (info.driverClassName() != null && !info.driverClassName().isBlank()) {
            Class.forName(info.driverClassName());
        }
        Properties props = new Properties();
        props.setProperty("user", info.username() == null ? "" : info.username());
        props.setProperty("password", info.password() == null ? "" : info.password());
        // 与 DataSourceServiceImpl#connection 保持一致：不显式开启的话，
        // MySQL/MariaDB 驱动不读 information_schema，列注释与索引元数据会缺。
        if (url.regionMatches(true, 0, "jdbc:mysql:", 0, "jdbc:mysql:".length())
                || url.regionMatches(true, 0, "jdbc:mariadb:", 0, "jdbc:mariadb:".length())) {
            props.setProperty("useInformationSchema", "true");
        }
        return java.sql.DriverManager.getConnection(url, props);
    }

    /**
     * 取事务会话。
     *
     * @param txId 事务标识
     * @return 会话
     * @throws IllegalArgumentException 会话不存在或已结束
     */
    public DbTxSession require(String txId) {
        DbTxSession session = txId == null ? null : sessions.get(txId);
        if (session == null) {
            throw new IllegalArgumentException("事务不存在或已结束: " + txId);
        }
        if (session.finished()) {
            // 已结束的事务要从表里摘掉，否则每次都命中它、每次都报错，
            // 而用户看到的提示会是「事务不存在」—— 与真实原因（已提交过）对不上。
            sessions.remove(txId);
            throw new IllegalArgumentException("事务已提交或回滚，请重新开启: " + txId);
        }
        return session;
    }

    /**
     * 提交并摘除会话。
     *
     * @param txId 事务标识
     * @throws Exception 提交失败
     */
    public void commit(String txId) throws Exception {
        DbTxSession session = require(txId);
        try {
            session.commit();
        } finally {
            // 无论提交成功与否都要摘除并关连接：
            // 提交失败时连接上的事务状态已不可信，留着会污染下一个借用者。
            sessions.remove(txId);
            session.close(true);
        }
        LOG.info("提交事务 {}", txId);
    }

    /**
     * 回滚并摘除会话。
     *
     * @param txId 事务标识
     * @throws Exception 回滚失败
     */
    public void rollback(String txId) throws Exception {
        DbTxSession session = require(txId);
        try {
            session.rollback();
        } finally {
            sessions.remove(txId);
            session.close(true);
        }
        LOG.info("回滚事务 {}", txId);
    }

    /**
     * 当前所有会话的状态，供 UI 展示。
     *
     * @return 会话状态列表
     */
    public List<DbTxStatus> status() {
        List<DbTxStatus> out = new ArrayList<>(sessions.size());
        for (DbTxSession s : sessions.values()) {
            out.add(new DbTxStatus(s.txId(), s.datasourceId(), s.database(),
                    s.idleFor().getSeconds()));
        }
        out.sort(Comparator.comparing(DbTxStatus::txId));
        return out;
    }

    /**
     * 回收空闲超时会话：回滚并关连接。
     *
     * @return 被回收的会话数
     */
    public int purgeIdle() {
        Duration limit = Duration.ofMinutes(IDLE_TIMEOUT_MINUTES);
        AtomicInteger purged = new AtomicInteger();
        for (Map.Entry<String, DbTxSession> e : sessions.entrySet()) {
            DbTxSession s = e.getValue();
            if (s.idleFor().compareTo(limit) < 0) {
                continue;
            }
            // 先摘除再关：并发下 commit 也会摘除，
            // 先摘除才能保证连接只被关一次。
            if (sessions.remove(e.getKey(), s)) {
                try {
                    s.close(true);
                } catch (RuntimeException ex) {
                    LOG.warn("回收空闲事务 {} 时关闭连接失败: {}", e.getKey(), ex.getMessage());
                }
                purged.incrementAndGet();
                LOG.info("回收空闲事务 {}（空闲超过 {} 分钟）", e.getKey(), IDLE_TIMEOUT_MINUTES);
            }
        }
        return purged.get();
    }

    /**
     * 定时回收空闲事务。
     */
    @Scheduled(fixedDelay = 60_000L)
    public void purgeIdlePeriodically() {
        if (!sessions.isEmpty()) {
            purgeIdle();
        }
    }

    /**
     * 应用关闭时回滚所有未提交事务。
     *
     * <p>不回收的后果：进程退出时所有未提交的事务由内核回滚（连接断开），
     * 这没问题；但如果连接来自池且 JVM 关闭顺序让 `close()` 没跑到，
            连接就带着脏状态还回去了。所以显式收一遍。</p>
     */
    @PreDestroy
    public void closeAll() {
        for (String txId : sessions.keySet()) {
            DbTxSession s = sessions.remove(txId);
            if (s != null) {
                try {
                    s.close(true);
                } catch (RuntimeException ex) {
                    LOG.warn("应用关闭时回滚事务 {} 失败: {}", txId, ex.getMessage());
                }
            }
        }
    }
}
