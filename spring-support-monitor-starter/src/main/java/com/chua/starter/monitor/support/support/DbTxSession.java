package com.chua.starter.monitor.support.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 一个显式事务会话：跨多个 HTTP 请求持有**同一条**连接。
 *
 * <p>为什么必须有它：数据库管理器的每个端点都是
 * {@code try (Connection conn = datasourceService.connection(id)) {...}}，
 * 用完就关。所以 {@code BEGIN; UPDATE; ...; COMMIT} 这种脚本
 * 跨请求执行时，每条语句各自一条连接、各自自动提交，
 * 中途一条失败，前面的改动**已经落库**了 —— 现象是「回滚了但数据没回去」。
 * 本类把连接的生命周期从「一次请求」拉长到「一次会话」。</p>
 *
 * <p><b>并发</b>：一条 {@link Connection} 不是线程安全的（驱动内部有可变状态，
 * 且事务上下文是连接级的），所以本类自带一把 {@link ReentrantLock}。
 * 所有使用方都必须通过 {@link #run} 拿连接，不能直接 {@code getConnection()}。</p>
 *
 * <p><b>不用 AutoCloseable</b>：会话不是 try-with-resources 的作用域 ——
 * 它必须活过若干个请求，由注册表显式 commit / rollback / 回收。
 * 实现 {@link AutoCloseable} 会诱导调用方写进 try-with-resources，
 * 那会在每个请求结束时把事务悄悄回滚掉，是最难查的一类 bug。</p>
 *
 * @author CH
 * @since 4.0.0
 */
public final class DbTxSession {

    /** 事务标识 */
    private final String txId;

    /** 数据源 ID */
    private final Long datasourceId;

    /** 开启事务时选定的库（可为空，表示数据源默认库） */
    private final String database;

    /** 持有的连接，autoCommit=false */
    private final Connection connection;

    /** 串行化同一事务上的并发请求 */
    private final ReentrantLock lock = new ReentrantLock();

    /** 开启时刻 */
    private final Instant openedAt = Instant.now();

    /** 最近一次被使用（或刷新）的时刻 */
    private volatile Instant lastUsedAt = Instant.now();

    /** 是否已经 commit / rollback 过；已结束的事务不再接受任何语句 */
    private volatile boolean finished;

    /**
     * 构造事务会话。
     *
     * @param txId         事务标识
     * @param datasourceId 数据源 ID
     * @param database     库名，可为空
     * @param connection   已置 autoCommit=false 的连接
     */
    public DbTxSession(String txId, Long datasourceId, String database, Connection connection) {
        this.txId = txId;
        this.datasourceId = datasourceId;
        this.database = database;
        this.connection = connection;
    }

    /**
     * 事务标识。
     *
     * @return txId
     */
    public String txId() {
        return txId;
    }

    /**
     * 数据源 ID。
     *
     * @return datasourceId
     */
    public Long datasourceId() {
        return datasourceId;
    }

    /**
     * 开启时选定的库。
     *
     * @return database，可为空
     */
    public String database() {
        return database;
    }

    /**
     * 事务里的连接。
     *
     * <p><b>只在 {@link #run} 的回调里用</b>；锁由 {@code run} 持有，
     * 回调内并发使用同一条连接会绕过串行化保护。</p>
     *
     * @return connection
     */
    public Connection connection() {
        return connection;
    }

    /**
     * 是否已经结束（commit 或 rollback 过）。
     *
     * @return true=已结束
     */
    public boolean finished() {
        return finished;
    }

    /**
     * 空闲时长。
     *
     * @return 距上次使用的时长
     */
    public Duration idleFor() {
        return Duration.between(lastUsedAt, Instant.now());
    }

    /** 记一次使用，刷新空闲计时 */
    public void touch() {
        lastUsedAt = Instant.now();
    }

    /**
     * 在锁内执行一段使用本事务连接的操作。
     *
     * <p>回调抛异常时**不回滚**：语句级成败由各自的端点决定语义
     * （批量执行要「继续执行并逐条记账」，这与「一条失败就整体回滚」不同）。
     * 整段回滚是用户显式点「回滚」时的行为。</p>
     *
     * @param action 操作
     * @param <T>    结果类型
     * @return 回调返回值
     * @throws IllegalStateException 事务已结束
     */
    public <T> T run(java.util.function.Function<Connection, T> action) {
        if (finished) {
            throw new IllegalStateException("事务已结束，请重新开启");
        }
        lock.lock();
        try {
            if (finished) {
                throw new IllegalStateException("事务已结束，请重新开启");
            }
            touch();
            return action.apply(connection);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 提交。
     *
     * <p>提交后**立刻把 autoCommit 复位为 true**：这条连接交回去时若还停在
     * 事务模式，后续复用它的每个请求都会静默地不提交。这个复位放在 commit
     * 里而不是只放在 {@link #close}，是为了让「提交成功但连接没关掉」
     * 也不会留下一个永远不提交的连接。</p>
     *
     * @throws SQLException 提交失败
     */
    public void commit() throws SQLException {
        lock.lock();
        try {
            if (finished) {
                return;
            }
            connection.commit();
            finished = true;
            if (!connection.isClosed()) {
                connection.setAutoCommit(true);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 回滚。
     *
     * <p>回滚失败也不能吞：连接一旦处于「未知」状态就必须丢弃，
     * 但把原因报出来才能定位（例如锁等待超时 vs 连接已断）。</p>
     *
     * @throws SQLException 回滚失败
     */
    public void rollback() throws SQLException {
        lock.lock();
        try {
            if (finished) {
                return;
            }
            connection.rollback();
            finished = true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 关闭连接。幂等，且**先回滚、恢复 autoCommit、再关**：
     * 连接池会把连接还回去复用，带着未提交的事务还池，下一个借用者可能看到脏状态
     * 或直接被驱动拒绝；而 {@code autoCommit=false} 没复位的话更隐蔽 ——
     * 后面**所有**借用者都会静默地跑在一个永不提交的事务里，
     * 表现为「语句返回成功、数据却不变」。
     *
     * @param swallow 是否吞掉关闭期的异常（回收路径用；用户显式关闭不吞）
     */
    public void close(boolean swallow) {
        lock.lock();
        try {
            if (!finished) {
                try {
                    connection.rollback();
                } catch (SQLException e) {
                    if (!swallow) {
                        // 记到日志而不是静默：连接池里最常见的一种「莫名其妙的状态」
                        throw new IllegalStateException("回滚未提交事务失败: " + e.getMessage(), e);
                    }
                }
            }
            finished = true;
            // 必须复位，否则这条连接（若被复用）会永远停留在事务模式
            try {
                if (!connection.isClosed()) {
                    connection.setAutoCommit(true);
                }
            } catch (SQLException e) {
                if (!swallow) {
                    throw new IllegalStateException("复位 autoCommit 失败: " + e.getMessage(), e);
                }
            }
            connection.close();
        } catch (SQLException e) {
            if (!swallow) {
                throw new IllegalStateException("关闭事务连接失败: " + e.getMessage(), e);
            }
        } finally {
            lock.unlock();
        }
    }
}
