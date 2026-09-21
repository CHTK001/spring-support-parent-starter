package com.chua.starter.job.support.manage.dto;

import com.chua.starter.job.support.dispatch.JobSpec;
import lombok.Data;

/**
 * 任务登记入参 (HTTP 外部接口专用)。
 *
 * <p>刻意不暴露业务编号：{@code sys_job_no} 由任务键推导，外部调用方无法塞入与任务键不一致的编号。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class JobSpecDTO {

    /**
     * 任务键, 与已注册处理器名一致
     */
    private String jobKey;

    /**
     * 任务显示名, 为空取任务键
     */
    private String jobName;

    /**
     * 任务描述
     */
    private String desc;

    /**
     * 分组, 为空取 default
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
     * 是否启用自动调度, 为空按启用
     */
    private Boolean enabled;

    /**
     * 执行参数
     */
    private String executeParam;

    /**
     * 转换为内部 {@link JobSpec} 任务规格。
     *
     * @return 任务规格
     */
    public JobSpec toSpec() {
        JobSpec spec = new JobSpec();
        spec.setJobKey(jobKey);
        spec.setJobName(jobName);
        spec.setDesc(desc);
        spec.setGroup(group);
        spec.setScheduleType(scheduleType);
        spec.setScheduleTime(scheduleTime);
        spec.setEnabled(enabled);
        spec.setExecuteParam(executeParam);
        return spec;
    }
}
