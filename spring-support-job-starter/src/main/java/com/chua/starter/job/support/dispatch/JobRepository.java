package com.chua.starter.job.support.dispatch;

import java.util.List;

/**
 * 任务仓库接口。
 *
 * <p>Leader 单秒循环通过 {@link #findDue(long, int)} 查询当前到期任务,
 * 非 Leader 节点不会调用。生产建议基于 MyBatis-Plus / JDBC 替换默认实现。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface JobRepository {

    /**
     * 查询当前应当被触发执行的任务列表。
     *
     * @param now         当前时间戳 (毫秒)
     * @param preReadMs   预读时间窗 (毫秒)
     * @param preReadCount 最大返回数
     * @return 任务列表
     */
    List<JobDefinition> findDue(long now, long preReadMs, int preReadCount);

    /**
     * 将任务定义插入或更新到仓库。供 {@link com.chua.starter.job.support.handler.JobAnnotationScanner}
     * 在启动时把 {@link com.chua.starter.job.support.annotation.Job} 注解扫描出的任务同步到仓库。
     *
     * <p>默认实现为 no-op, 实现类根据自身存储能力决定是否支持 (InMemory 默认实现支持,
     * MyBatis 实现需写入持久化表)。</p>
     *
     * @param definition 任务定义
     */
    default void upsert(JobDefinition definition) {
        // no-op: 不支持 upsert 的实现 (例如只读远端仓库) 直接忽略
    }
}
