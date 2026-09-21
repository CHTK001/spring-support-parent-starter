package com.chua.starter.job.support.operate;

import com.chua.common.support.lang.code.PageResult;
import com.chua.starter.job.support.dispatch.JobQuery;
import com.chua.starter.job.support.dispatch.JobRepository;
import com.chua.starter.job.support.dispatch.JobRuntime;
import com.chua.starter.job.support.dispatch.JobSpec;

import java.util.List;

/**
 * 任务操作接口 (调度层的内部入口)。
 *
 * <p>业务模块通过本接口把一段自己的逻辑"声明"成一个可调度任务，并在运行时改时间、启停、
 * 立即执行、删除；不再只能依赖启动期扫描的 {@code @Job} 注解。对外 HTTP 接口
 * ({@code /v2/job/**}) 同样收敛到本接口，保证内外两套入口的行为一致。</p>
 *
 * <h3>与处理器的关系</h3>
 * <p>本接口只管调度侧。任务要真正跑起来，节点上必须有同名处理器
 * ({@link com.chua.starter.job.support.handler.JobHandlerFactory#register})；
 * 派发命令携带的就是处理器名，处理器缺失时执行器会记一条"handler 未注册"的跳过日志。
 * 因此业务侧应当在应用启动时按存储里的任务清单补齐处理器，并在增删任务时同步注册/注销。</p>
 *
 * @since 4.0.0.42
 * @author CH
 * @see JobRepository
 */
public interface JobOperator {

    /**
     * 登记或更新任务 (按 {@link JobSpec#getJobKey()} 幂等)。
     *
     * @param spec 任务规格
     * @return 成功返回 true
     */
    boolean declare(JobSpec spec);

    /**
     * 修改调度配置, 并让新配置在本节点立即生效 (无需重启)。
     *
     * @param jobKey       任务键
     * @param scheduleType 调度类型, 见 {@link JobSpec#TYPE_CRON} 等常量
     * @param scheduleTime 调度配置
     * @return 任务存在且更新成功返回 true
     */
    boolean updateSchedule(String jobKey, String scheduleType, String scheduleTime);

    /**
     * 启用/停用自动调度。
     *
     * @param jobKey  任务键
     * @param enabled 启用
     * @return 任务存在且更新成功返回 true
     */
    boolean changeStatus(String jobKey, boolean enabled);

    /**
     * 立即派发一次执行 (不改变启停状态, 也不影响后续调度序列)。
     *
     * @param jobKey 任务键
     * @return 命令已投递返回 true; 任务不存在返回 false
     */
    boolean trigger(String jobKey);

    /**
     * 删除任务 (逻辑删除, 调度侧立即不可见)。
     *
     * @param jobKey 任务键
     * @return 任务存在且删除成功返回 true
     */
    boolean remove(String jobKey);

    /**
     * 读取任务运行态。
     *
     * @param jobKey 任务键
     * @return 快照, 任务不存在返回 {@code null}
     */
    JobRuntime runtime(String jobKey);

    /**
     * 按分组列出任务运行态。
     *
     * @param group 分组, 为空列全部
     * @return 快照列表
     */
    List<JobRuntime> list(String group);

    /**
     * 任务运行态分页。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<JobRuntime> page(JobQuery query);
}
