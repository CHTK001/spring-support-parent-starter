package com.chua.starter.datasource.monitor;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicLong;

/**
* SQL 执行统计
*
* <p>按数据源维度聚合 SQL 执行指标：总次数、成功/失败次数、慢 SQL 次数、耗时合计等。
* 线程安全，可在任意时刻安全读取。</p>
*
* @since 4.0.0.42
* @author CH
 */
@Slf4j
@Getter
public class SqlStatistics {

    /**
    * 数据源名称
    */
    private final String dataSourceName;

    /**
    * 总执行次数
    */
    private final AtomicLong totalCount = new AtomicLong();

    /**
    * 成功执行次数
    */
    private final AtomicLong successCount = new AtomicLong();

    /**
    * 失败执行次数
    */
    private final AtomicLong failCount = new AtomicLong();

    /**
    * 慢 SQL 次数
    */
    private final AtomicLong slowSqlCount = new AtomicLong();

    /**
    * 总耗时（毫秒）
    */
    private final AtomicLong totalCostMs = new AtomicLong();

    /**
    * 记录一次执行
    *
    * @param costMs  执行耗时（毫秒）
    * @param success 是否成功
    * @param slowSql 是否慢 SQL
    */
    public void record(long costMs, boolean success, boolean slowSql) {
        totalCount.incrementAndGet();
        totalCostMs.addAndGet(costMs);
        if (success) {
            successCount.incrementAndGet();
        } else {
            failCount.incrementAndGet();
        }
        if (slowSql) {
            slowSqlCount.incrementAndGet();
        }
    }

    /**
    * 平均耗时（毫秒）
    *
    * @return 平均耗时，无记录时返回 0
    */
    public long getAvgCostMs() {
        long total = totalCount.get();
        return total == 0 ? 0 : totalCostMs.get() / total;
    }

    /**
    * 重置统计
    */
    public void reset() {
        totalCount.set(0);
        successCount.set(0);
        failCount.set(0);
        slowSqlCount.set(0);
        totalCostMs.set(0);
    }

    /**
    * 统计构造器
    *
    * @param dataSourceName 数据源名称
    */
    public SqlStatistics(String dataSourceName) {
        this.dataSourceName = dataSourceName;
    }

    @Override
    /** 转为字符串 */
    public String toString() {
        return "SqlStatistics{" +
                "dataSource='" + dataSourceName + '\'' +
                ", total=" + totalCount.get() +
                ", success=" + successCount.get() +
                ", fail=" + failCount.get() +
                ", slowSql=" + slowSqlCount.get() +
                ", avgCostMs=" + getAvgCostMs() +
                '}';
    }
}
