package com.chua.starter.datasource.support;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
* 多数据源事务上下文
* <p>
* 管理事务期间的数据源上下文，提供以下功能：
* <ul>
*   <li><b>栈式数据源追踪</b> —— 支持嵌套 {@code @DS} 切换，方法结束后自动恢复上一级上下文</li>
*   <li><b>已用数据源记录</b> —— 记录当前事务中实际使用了哪些数据源</li>
*   <li><b>连接管理</b> —— 从事务中获取并管理连接的生命周期</li>
* </ul>
* </p>
*
* <h3>线程安全</h3>
* 每个线程通过 thread本地 持有自己的上下文实例。
*
* @since 2026/07/14
* @author CH
 */
@Slf4j
public class MultiDSTransactionContext {

 // ========== thread本地 管理 ==========

    /** 当前线程的 dstransactional 上下文 */
    /** 当前_上下文 */
    private static final ThreadLocal<MultiDSTransactionContext> CURRENT_CONTEXT = new ThreadLocal<>();

    /** 当前线程的嵌套深度（支持 @dstransactional 嵌套） */
    /** 嵌套_深度 */
    private static final ThreadLocal<Integer> NESTED_DEPTH = ThreadLocal.withInitial(() -> 0);

    /**
    * 获取当前线程的事务上下文
    *
    * @return 当前上下文，无事务时返回 空
    */
    public static MultiDSTransactionContext getCurrent() {
        return CURRENT_CONTEXT.get();
    }

    /**
    * 设置当前线程的事务上下文
    *
    * @param context 上下文
    */
    static void setCurrent(MultiDSTransactionContext context) {
        if (context == null) {
            CURRENT_CONTEXT.remove();
        } else {
            CURRENT_CONTEXT.set(context);
        }
    }

    /**
    * 当前线程是否处于 @dstransactional 事务中
    *
    * @return 是否在事务中
    */
    public static boolean isInTransaction() {
        return CURRENT_CONTEXT.get() != null;
    }

    /**
    * 增加嵌套深度
    *
    * @return 当前深度
    */
    static int incrementDepth() {
        int depth = NESTED_DEPTH.get() + 1;
        NESTED_DEPTH.set(depth);
        return depth;
    }

    /**
    * 减少嵌套深度
    *
    * @return 当前深度
    */
    static int decrementDepth() {
        int depth = Math.max(0, NESTED_DEPTH.get() - 1);
        if (depth == 0) {
            NESTED_DEPTH.remove();
        } else {
            NESTED_DEPTH.set(depth);
        }
        return depth;
    }

    /**
    * 获取当前嵌套深度
    *
    * @return 嵌套深度
    */
    public static int getDepth() {
        return NESTED_DEPTH.get();
    }

    // ========== 实例字段 ==========

    /** 数据源上下文栈（支持嵌套切换） */
    /** 数据来源栈 */
    private final Deque<String> dataSourceStack = new ArrayDeque<>();

    /** 事务中已使用的数据源名称（去重） */
    /** Used数据源 */
    private final Set<String> usedDataSources = new LinkedHashSet<>();

    /** 事务中已获取的连接（数据源名称 → Connection） */
    private final Map<String, Connection> connections = new LinkedHashMap<>();

    /** 事务名称 */
    /** Transaction名称 */
    private final String transactionName;

    /** 是否已提交或回滚 */
    /** Completed */
    private boolean completed = false;

    /** 开始时间 */
    private final long startTime;

    /**
    * multidstransaction上下文。
    * @param transactionName transaction名称
    */
    public MultiDSTransactionContext(String transactionName) {
        this.transactionName = transactionName;
        this.startTime = System.currentTimeMillis();
    }

    // ========== 数据源上下文栈管理 ==========

    /**
    * 推入数据源上下文
    *
    * @param dsName 数据源名称
    */
    public void pushDataSource(String dsName) {
        dataSourceStack.push(dsName);
        usedDataSources.add(dsName);
        log.debug("[DSTX:{}] 推送数据源上下文: {}, 栈深度: {}", transactionName, dsName, dataSourceStack.size());
    }

    /**
    * 弹出数据源上下文
    *
    * @return 上一级数据源名称，栈空时返回 空
    */
    public String popDataSource() {
        String popped = dataSourceStack.poll();
        String restored = dataSourceStack.peek();
        log.debug("[DSTX:{}] 弹出数据源上下文: {}, 恢复: {}, 栈深度: {}",
                transactionName, popped, restored, dataSourceStack.size());
        return restored;
    }

    /**
    * 获取当前数据源上下文
    *
    * @return 当前数据源名称
    */
    public String currentDataSource() {
        return dataSourceStack.peek();
    }

    /**
    * 获取已使用的数据源名称集合
    *
    * @return 数据源名称集合
    */
    public Set<String> getUsedDataSources() {
        return Collections.unmodifiableSet(usedDataSources);
    }

    // ========== 连接管理 ==========

    /**
    * 获取或创建某个数据源的连接（设置 autocommit=false）
    *
    * @param dsName     数据源名称
    * @param dataSource 数据源实例
    * @return 数据库连接
    * @throws SQLException 获取或初始化连接失败
    */
    public Connection getOrCreateConnection(String dsName, DataSource dataSource) throws SQLException {
        Connection conn = connections.get(dsName);
        if (conn == null) {
            conn = dataSource.getConnection();
            conn.setAutoCommit(false);
            connections.put(dsName, conn);
            log.debug("[DSTX:{}] 创建事务连接: {}", transactionName, dsName);
        }
        return conn;
    }

    /**
    * 获取已跟踪的连接
    *
    * @param dsName 数据源名称
    * @return 连接，不存在时返回 空
    */
    public Connection getConnection(String dsName) {
        return connections.get(dsName);
    }

    // ========== 事务生命周期 ==========

    /**
    * 提交所有连接
    */
    public void commit() {
        if (completed) {
            return;
        }
        completed = true;
        List<String> successCommit = new ArrayList<>();
        List<String> failedCommit = new ArrayList<>();
        long elapsed = System.currentTimeMillis() - startTime;

        for (Map.Entry<String, Connection> entry : connections.entrySet()) {
            String dsName = entry.getKey();
            Connection conn = entry.getValue();
            try {
                if (!conn.isClosed()) {
                    conn.commit();
                    successCommit.add(dsName);
                }
            } catch (SQLException e) {
                failedCommit.add(dsName);
                log.error("[DSTX:{}] 提交失败: {}", transactionName, dsName, e);
            }
        }

        log.info("[DSTX:{}] 事务提交完成 ({}ms), 成功: {}, 失败: {}",
                transactionName, elapsed, successCommit, failedCommit);

        if (!failedCommit.isEmpty()) {
            // 尝试回滚已提交失败的连接
            for (String dsName : failedCommit) {
                Connection conn = connections.get(dsName);
                if (conn != null) {
                    try {
                        conn.rollback();
                    } catch (SQLException ignored) {
                    }
                }
            }
            throw new IllegalStateException(
                    "[DSTX:" + transactionName + "] 部分数据源提交失败: " + failedCommit);
        }
    }

    /**
    * 回滚所有连接
    */
    public void rollback() {
        if (completed) {
            return;
        }
        completed = true;
        long elapsed = System.currentTimeMillis() - startTime;

        for (Map.Entry<String, Connection> entry : connections.entrySet()) {
            String dsName = entry.getKey();
            Connection conn = entry.getValue();
            try {
                if (!conn.isClosed()) {
                    conn.rollback();
                    log.debug("[DSTX:{}] 回滚成功: {}", transactionName, dsName);
                }
            } catch (SQLException e) {
                log.warn("[DSTX:{}] 回滚失败: {}", transactionName, dsName, e);
            }
        }

        log.info("[DSTX:{}] 事务回滚完成 ({}ms), 涉及数据源: {}", transactionName, elapsed, connections.keySet());
    }

    /**
    * 清理资源并释放
    */
    public void cleanup() {
        for (Map.Entry<String, Connection> entry : connections.entrySet()) {
            String dsName = entry.getKey();
            Connection conn = entry.getValue();
            try {
                if (!conn.isClosed()) {
                    conn.setAutoCommit(true);
                    conn.close();
                }
            } catch (SQLException e) {
                log.warn("[DSTX:{}] 关闭连接失败: {}", transactionName, dsName, e);
            }
        }
        connections.clear();
        dataSourceStack.clear();
        usedDataSources.clear();
    }

    /**
    * 获取事务持续时间（毫秒）
    *
    * @return 持续时间
    */
    public long getElapsed() {
        return System.currentTimeMillis() - startTime;
    }

    /**
    * 获取事务名称
    *
    * @return 事务名称
    */
    public String getTransactionName() {
        return transactionName;
    }

    /**
    * 是否已完成
    *
    * @return 是否已完成
    */
    public boolean isCompleted() {
        return completed;
    }

    @Override
    public String toString() {
        return "MultiDSTransactionContext{" +
                "name='" + transactionName + '\'' +
                ", used=" + usedDataSources +
                ", connections=" + connections.size() +
                ", depth=" + dataSourceStack.size() +
                ", elapsed=" + (System.currentTimeMillis() - startTime) + "ms" +
                '}';
    }
}
