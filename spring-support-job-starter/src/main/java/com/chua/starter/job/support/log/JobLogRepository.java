package com.chua.starter.job.support.log;

import com.chua.common.support.lang.code.PageResult;

import java.util.Collection;
import java.util.List;

/**
 * 任务执行日志的读取侧。
 *
 * <p>写入由 {@link JobLogSink} 负责 (可能是异步/聚合的), 本接口只服务查询与清理, 因此单独开口:
 * 页面上的「最近日志」「日志分页」「清理日志」都走这里, 不允许绕过 {@link JobLogSink} 直接写。</p>
 *
 * <p>默认实现全部返回空结果, 让不落库的部署 (stdout/文件 sink) 也能装配治理接口而不报错;
 * MyBatis-Plus 在 classpath 上时由自动配置换上真实实现。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface JobLogRepository {

    /**
     * 日志分页, 按触发时刻倒序。
     *
     * @param query 查询条件, 可为空
     * @return 分页结果, 无实现时为空页
     */
    default PageResult<JobLogRecord> page(JobLogQuery query) {
        return PageResult.<JobLogRecord>builder().total(0L).data(java.util.List.of()).build();
    }

    /**
     * 按条件计数, 供概览统计 (如「最近失败」) 使用。
     *
     * @param query 查询条件, 分页字段忽略
     * @return 条数, 无实现时为 0
     */
    default long count(JobLogQuery query) {
        return 0L;
    }

    /**
     * 清理过期日志。
     *
     * @param retentionDays 保留天数, 小于等于 0 表示清空全部
     * @return 删除条数, 无实现时为 0
     */
    default int cleanup(int retentionDays) {
        return 0;
    }

    /**
     * 取每个任务键最近的一条日志。
     *
     * <p>供治理接口把「最近一次执行结果」批量回填到任务运行态: 一次分页通常有几十行,
     * 逐行查日志会把 N 次往返放大成 N 倍, 这里按任务键批量取各自的最新一条。</p>
     *
     * @param jobKeys 任务键集合, 空集合直接返回空
     * @return 每个任务键的最新一条日志, 无实现时为空
     */
    default List<JobLogRecord> latestByJobKeys(Collection<String> jobKeys) {
        return List.of();
    }

    /**
     * 按小时聚合执行趋势 (总数 / 成功 / 失败 / 跳过)。
     *
     * <p>供总览页趋势图取数, 替代「把窗口内日志全量拉到前端再本地分桶」。
     * 条件只认时间窗与分组, 结果按桶起点升序; 窗口内没有日志的桶不在结果里,
     * 是否补零由调用方决定。</p>
     *
     * @param condition 查询条件 (时间窗 + 分组, 其余字段忽略), 可为空
     * @return 趋势点列表, 无实现时为空
     */
    default List<JobLogTrendPoint> hourlyTrend(JobLogQuery condition) {
        return List.of();
    }

    /**
     * 统计窗口内失败次数最多的任务 (Top N)。
     *
     * <p>只统计 {@code executeCode=FAILURE} 的日志行, 按失败次数降序; 次数相同时按
     * 最近失败时刻倒序, 让「刚坏的任务」排在前面。</p>
     *
     * @param condition 查询条件 (时间窗 + 分组, 其余字段忽略), 可为空
     * @param limit     最多返回的任务数
     * @return 失败排名列表, 无实现时为空
     */
    default List<JobLogFailureTop> failureTop(JobLogQuery condition, int limit) {
        return List.of();
    }

    /**
     * 统计窗口内执行耗时 (总数 / 平均 / 最大 / P95)。
     *
     * @param condition 查询条件 (时间窗 + 分组, 其余字段忽略), 可为空
     * @return 耗时统计, 无实现时为全空统计
     */
    default JobLogDurationStats durationStats(JobLogQuery condition) {
        return new JobLogDurationStats(0L, null, null, null);
    }

    /**
     * 预览按保留天数清理将删除的日志条数, 不执行删除。
     *
     * <p>供清理确认框展示「将会删掉多少条」; 口径与 {@link #cleanup(int)} 完全一致,
     * {@code retentionDays <= 0} 表示清空全部。</p>
     *
     * @param retentionDays 保留天数, 小于等于 0 表示清空全部
     * @return 将删除条数, 无实现时为 0
     */
    default long cleanupPreview(int retentionDays) {
        return 0L;
    }
}
