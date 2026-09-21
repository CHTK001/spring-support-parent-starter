package com.chua.starter.job.support.dispatch;

import com.chua.common.support.lang.code.PageResult;

import java.util.List;

/**
 * 任务仓库接口。
 *
 * <p>Leader 单秒循环通过 {@link #findDue(long, int)} 查询当前到期任务,
 * 非 铅 节点不会调用。生产建议基于 MyBatis-Plus / JDBC 替换默认实现。</p>
 *
 * <p>除调度读取外, 本接口还承担任务治理 (写入任意 {@link JobSpec}、改调度时间、启停、
 * 删除、查运行态)。这些能力一律给默认实现, 只读型仓库 (例如远端仓库) 不覆盖即视为不支持,
 * 返回 {@code false}/{@code null}, 不会破坏既有第三方的编译与行为。</p>
 *
 * @since 4.0.0.42
 * @author CH
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

    /**
     * 按任务键新建或更新一条任务 (业务模块声明任务的主入口)。
     *
     * @param spec 任务规格, {@link JobSpec#getJobKey()} 必填
     * @return 写入成功返回 true; 仓库不支持或任务键为空返回 false
     */
    default boolean save(JobSpec spec) {
        return false;
    }

    /**
     * 按任务键读取任务定义。
     *
     * @param jobKey 任务键
     * @return 任务定义, 不存在返回 {@code null}
     */
    default JobDefinition find(String jobKey) {
        return null;
    }

    /**
     * 按任务键读取运行态快照 (含现算的下次触发时刻)。
     *
     * @param jobKey 任务键
     * @return 快照, 不存在返回 {@code null}
     */
    default JobRuntime runtime(String jobKey) {
        return null;
    }

    /**
     * 任务运行态分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    default PageResult<JobRuntime> page(JobQuery query) {
        return PageResult.<JobRuntime>builder().total(0L).data(List.of()).build();
    }

    /**
     * 按分组列出任务运行态 (分组为空时列全部)。
     *
     * @param group 分组
     * @return 快照列表
     */
    default List<JobRuntime> listGroup(String group) {
        return List.of();
    }

    /**
     * 修改调度配置并让新配置立即生效。
     *
     * @param jobKey       任务键
     * @param scheduleType 调度类型, 见 {@link JobSpec#TYPE_CRON} 等常量
     * @param scheduleTime 调度配置 (cron 表达式或毫秒间隔)
     * @return 更新成功返回 true, 任务不存在返回 false
     */
    default boolean updateSchedule(String jobKey, String scheduleType, String scheduleTime) {
        return false;
    }

    /**
     * 启用/停用自动调度 (不注销处理器, 手动触发仍可用)。
     *
     * @param jobKey  任务键
     * @param enabled 启用
     * @return 更新成功返回 true, 任务不存在返回 false
     */
    default boolean changeStatus(String jobKey, boolean enabled) {
        return false;
    }

    /**
     * 逻辑删除任务 (调度侧立即不可见)。
     *
     * @param jobKey 任务键
     * @return 删除成功返回 true, 任务不存在返回 false
     */
    default boolean remove(String jobKey) {
        return false;
    }

    /**
     * 失效本节点为该任务缓存的触发器与已派发时刻。
     *
     * <p>调度配置改动后必须调用, 否则 {@code findDue} 会继续用旧触发器算下一拍,
     * 表现为"改了时间不重启不生效"。</p>
     *
     * @param jobKey 任务键
     */
    default void refresh(String jobKey) {
        // no-op: 无触发器缓存的实现无需处理
    }
}
