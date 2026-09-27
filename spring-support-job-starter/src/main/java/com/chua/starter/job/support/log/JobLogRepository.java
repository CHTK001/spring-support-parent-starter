package com.chua.starter.job.support.log;

import com.chua.common.support.lang.code.PageResult;

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
}
