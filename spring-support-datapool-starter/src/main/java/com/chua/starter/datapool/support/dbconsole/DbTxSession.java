package com.chua.starter.datapool.support.dbconsole;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    /** 连接回收失败时保留事务标识与原因。 */
    private static final Logger LOG = LoggerFactory.getLogger(DbTxSession.class);

    /** 事务标识 */
    private final String txId;

    /** 数据源 ID */
    private final Long datasourceId;

    /** 开启事务时选定的库（可为空，表示数据源默认库） */
    private final String database;

    /** 持有的连接，autoCommit=false */
    private final Connection connection;

    /** 借出连接时的自动提交状态，归还前恢复。 */
    private final boolean originalAutoCommit;

    /** 串行化同一事务上的并发请求 */
    private final ReentrantLock lock = new ReentrantLock();

    /** 开启时刻 */
    private final Instant openedAt = Instant.now();

    /** 最近一次被使用（或刷新）的时刻 */
    private volatile Instant lastUsedAt = Instant.now();

    /** 是否已经 commit / rollback 过；已结束的事务不再接受任何语句 */
    private volatile boolean finished;

    /** 是否已经进入释放流程；阻止等待锁的请求在超时后继续执行。 */
    private volatile boolean closing;

    /** 是否已执行连接释放，防止并发回收重复操作。 */
    private boolean closed;

    /**
     * 构造事务会话。
     *
     * @param txId         事务标识
     * @param datasourceId 数据源 ID
     * @param database     库名，可为空
     * @param connection   已置 autoCommit=false 的连接
     */
    public DbTxSession(String txId, Long datasourceId, String database, Connection connection) {
        this(txId, datasourceId, database, connection, true);
    }

    /**
     * 构造保留连接原始自动提交状态的事务会话。
     *
     * @param txId 事务标识
     * @param datasourceId 数据源 ID
     * @param database 库名，可为空
     * @param connection 已置 autoCommit=false 的连接
     * @param originalAutoCommit 借出连接时的自动提交状态
     */
    public DbTxSession(String txId, Long datasourceId, String database, Connection connection,
                       boolean originalAutoCommit) {
        this.txId = txId;
        this.datasourceId = datasourceId;
        this.database = database;
        this.connection = connection;
        this.originalAutoCommit = originalAutoCommit;
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
        if (finished || closing) {
            throw new IllegalStateException("事务已结束，请重新开启");
        }
        lock.lock();
        try {
            if (finished || closing) {
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
     * <p>提交成功后恢复借出连接时的自动提交状态；注册表随后关闭连接租约。
     * 不能固定设为 true，因为宿主连接池也可以配置默认 autoCommit=false。</p>
     *
     * @throws SQLException 提交失败
     */
    public void commit() throws SQLException {
        lock.lock();
        try {
            if (closing) {
                throw new IllegalStateException("事务正在释放，请重新开启");
            }
            if (finished) {
                return;
            }
            connection.commit();
            finished = true;
            if (!connection.isClosed()) {
                connection.setAutoCommit(originalAutoCommit);
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
            if (closing) {
                throw new IllegalStateException("事务正在释放，请重新开启");
            }
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
     * 关闭连接。先结束事务，再恢复借出时的自动提交状态，最后归还连接。
     *
     * <p>回滚失败时不能设置 autoCommit=true，否则 JDBC 驱动可能隐式提交未完成事务。
     * 回滚或状态复位失败时尝试终止底层连接，并始终执行 close；释放异常不覆盖原异常。</p>
     *
     * @param swallow 是否吞掉关闭期的异常（回收路径用；用户显式关闭不吞）
     */
    public void close(boolean swallow) {
        closing = true;
        lock.lock();
        try {
            closeLocked(swallow);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 空闲回收专用的非阻塞关闭：正在执行 SQL 的会话不会被超时回收打断。
     *
     * @param idleLimit 空闲上限
     * @return true 表示连接已关闭或已由本次调用关闭
     */
    public boolean closeIfIdle(Duration idleLimit) {
        if (!lock.tryLock()) {
            return false;
        }
        try {
            if (closed) {
                return true;
            }
            if (idleFor().compareTo(idleLimit) < 0) {
                return false;
            }
            closing = true;
            closeLocked(true);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /** 在持有会话锁时结束事务并释放连接。 */
    private void closeLocked(boolean swallow) {
        if (closed) {
            return;
        }
        SQLException failure = null;
        if (!finished) {
            try {
                connection.rollback();
            } catch (SQLException e) {
                failure = e;
            }
        }
        finished = true;
        if (failure == null) {
            try {
                if (!connection.isClosed()) {
                    connection.setAutoCommit(originalAutoCommit);
                }
            } catch (SQLException e) {
                failure = e;
            }
        }
        try {
            connection.close();
        } catch (SQLException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        if (failure != null) {
            try {
                connection.abort(Runnable::run);
            } catch (SQLException | RuntimeException | AbstractMethodError e) {
                failure.addSuppressed(e);
            }
        }
        closed = true;
        if (failure != null) {
            if (!swallow) {
                throw new IllegalStateException("释放事务连接失败: " + failure.getMessage(), failure);
            }
            LOG.warn("释放事务连接失败: txId={} datasourceId={} database={}",
                    txId, datasourceId, database, failure);
        }
    }
}
