package com.chua.starter.job.support.manage.vo;

import com.chua.starter.job.support.dispatch.JobRuntime;
import lombok.Data;

/**
 * 任务运行态出参。
 *
 * <p>把内部快照的哨兵值归一为 {@code null}, 前端不必再判断 -1/0:</p>
 * <ul>
 *   <li>{@code lastFireTime}: 0 → null, 表示从未派发;</li>
 *   <li>{@code nextFireTime}: -1 → null, 表示未配置调度或表达式非法。</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class JobRuntimeVO {

    /**
     * 业务编号
     */
    private String jobNo;

    /**
     * 任务键 (派发标识, 与处理器名一致)
     */
    private String jobKey;

    /**
     * 任务显示名
     */
    private String jobName;

    /**
     * 任务描述
     */
    private String desc;

    /**
     * 分组
     */
    private String group;

    /**
     * 调度类型: CRON / FIXED / DELAY / NONE
     */
    private String scheduleType;

    /**
     * 调度配置: cron 表达式或毫秒间隔
     */
    private String scheduleTime;

    /**
     * 是否启用自动调度
     */
    private boolean enabled;

    /**
     * 执行参数
     */
    private String executeParam;

    /**
     * 最近一次派发时刻 (毫秒时间戳, 空表示无记录)
     */
    private Long lastFireTime;

    /**
     * 下一次触发时刻 (毫秒时间戳, 空表示不参与自动调度)
     */
    private Long nextFireTime;

    /**
     * 当前节点是否已注册同名处理器
     */
    private boolean handlerRegistered;

    /**
     * 由内部运行态快照构造出参。
     *
     * @param runtime 快照, 可为空
     * @return 出参, 快照为空时返回 空
     */
    public static JobRuntimeVO of(JobRuntime runtime) {
        if (runtime == null) {
            return null;
        }
        JobRuntimeVO vo = new JobRuntimeVO();
        vo.setJobNo(runtime.getJobNo());
        vo.setJobKey(runtime.getJobKey());
        vo.setJobName(runtime.getJobName());
        vo.setDesc(runtime.getDesc());
        vo.setGroup(runtime.getGroup());
        vo.setScheduleType(runtime.getScheduleType());
        vo.setScheduleTime(runtime.getScheduleTime());
        vo.setEnabled(runtime.isEnabled());
        vo.setExecuteParam(runtime.getExecuteParam());
        vo.setLastFireTime(runtime.getLastFireMs() > 0L ? runtime.getLastFireMs() : null);
        vo.setNextFireTime(runtime.getNextFireMs() > 0L ? runtime.getNextFireMs() : null);
        vo.setHandlerRegistered(runtime.isHandlerRegistered());
        return vo;
    }
}
