package com.chua.starter.collect.schedule;

import java.util.Map;

/**
 * 采集流程的排期端口。
 *
 * <p>采集模块对"定时调度"的唯一出口：采集本体（流程设计 / 手动执行 / 结果落库）不依赖
 * job-starter，排期能力在本端口背后做成可替换实现——</p>
 *
 * <ul>
 * <li>类路径上有 job-starter 且其功能开启：由 {@code JobFlowSchedulePort} 落到 sys_job 台账；</li>
 * <li>没有 job-starter（或 {@code plugin.job.enable=false}）：由
 * {@code LocalOnlyFlowSchedulePort} 兜底，排期相关的写操作静默跳过、查询返回未登记，
 * 启用排期时给出明确业务提示，绝不假装排期成功。</li>
 * </ul>
 *
 * <p>端口的方法签名只用本包的中立类型，因此没有 job-starter 时实现类不会被加载。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface CollectFlowSchedulePort {

    /**
     * 当前部署是否具备定时调度能力。
     *
     * @return 有 job-starter 且已开启时返回 true
     */
    boolean schedulingAvailable();

    /**
     * 登记或更新一条流程排期（幂等）。
     *
     * @param spec 排期请求
     */
    void declare(CollectFlowScheduleSpec spec);

    /**
     * 注销一条排期。
     *
     * @param jobKey 调度行键
     */
    void remove(String jobKey);

    /**
     * 调整排期启停。
     *
     * @param jobKey  调度行键
     * @param enabled 是否启用
     * @return 台账里存在该行并调整成功返回 true
     */
    boolean changeStatus(String jobKey, boolean enabled);

    /**
     * 查询单条排期运行态。
     *
     * @param jobKey 调度行键
     * @return 运行态；未登记时返回 null
     */
    CollectFlowScheduleState runtime(String jobKey);

    /**
     * 查询本模块全部排期运行态。
     *
     * @return 调度行键到运行态的映射
     */
    Map<String, CollectFlowScheduleState> listRuntimes();

    /**
     * 清理历史遗留的单条轮询心跳行。
     *
     * <p>排期已改为"每流程一行"，残留行会让任务中心显示永不派发的僵尸任务。</p>
     */
    void removeLegacyTick();
}
