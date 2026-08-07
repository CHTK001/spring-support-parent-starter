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
}
