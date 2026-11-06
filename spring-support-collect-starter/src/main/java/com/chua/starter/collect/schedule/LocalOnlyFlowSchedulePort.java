package com.chua.starter.collect.schedule;

import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Map;

/**
 * 无调度器时的排期兜底实现。
 *
 * <p>部署未引入 job-starter（或 {@code plugin.job.enable=false}）时生效：采集本体照常可用
 * ——流程设计、手动执行、结果与日志、变量/函数/通知全部不受影响，只是没有定时触发。</p>
 *
 * <p>刻意不做"假装排期成功"：写操作静默跳过、查询一律返回未登记，启用排期时由上层
 * 依据 {@link #schedulingAvailable()} 给出明确业务提示。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class LocalOnlyFlowSchedulePort implements CollectFlowSchedulePort {

    /**
     * 提示只打一次，避免列表接口每次调用都刷日志。
     */
    private volatile boolean warned = false;

    @Override
    public boolean schedulingAvailable() {
        return false;
    }

    @Override
    public void declare(CollectFlowScheduleSpec spec) {
        warnOnce();
    }

    @Override
    public void remove(String jobKey) {
        // 没有台账，无需注销
    }

    @Override
    public boolean changeStatus(String jobKey, boolean enabled) {
        return false;
    }

    @Override
    public CollectFlowScheduleState runtime(String jobKey) {
        return null;
    }

    @Override
    public Map<String, CollectFlowScheduleState> listRuntimes() {
        return Collections.emptyMap();
    }

    @Override
    public void removeLegacyTick() {
        // 没有台账，无需清理
    }

    /**
     * 首次写操作时提示一次"当前不支持定时"。
     */
    private void warnOnce() {
        if (warned) {
            return;
        }
        warned = true;
        log.info("[collect] 未引入 job-starter（或 plugin.job.enable=false），"
                + "流程排期不生效，仅支持手动执行");
    }
}
