package com.chua.starter.job.support.dispatch;

/**
 * 任务规格 (写入 {@link JobRepository} 的入参)。
 *
 * <p>与只承载调度三要素的 {@link JobDefinition} 不同, 本对象带齐了落库所需的全部列:
 * 业务编号、执行器名、分组与执行参数。用于让业务模块 (采集、报表等)
 * 把一个任务"声明"成调度表里的一行, 而不是只能靠 {@code @Job} 注解在启动期注册。</p>
 *
 * <p>调度配置按 {@code scheduleType + scheduleTime} 表达, 取值与 {@code sys_job} 一致:
 * {@code CRON}(cron 表达式) / {@code FIXED}(固定频率毫秒) / {@code DELAY}(固定延迟毫秒)
 * / {@code NONE}(不参与自动调度, 只能手动触发)。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public class JobSpec {

    /**
     * 调度类型: cron 表达式
     */
    public static final String TYPE_CRON = "CRON";

    /**
     * 调度类型: 固定频率 (毫秒)
     */
    public static final String TYPE_FIXED = "FIXED";

    /**
     * 调度类型: 固定延迟 (毫秒)
     */
    public static final String TYPE_DELAY = "DELAY";

    /**
     * 调度类型: 不参与自动调度
     */
    public static final String TYPE_NONE = "NONE";

    /**
     * 业务编号 (对应 {@code sys_job_no}, 唯一); 为空时取任务键
     */
    private String jobNo;

    /**
     * 任务键 (对应 {@code sys_job_execute_bean}, 与 {@link com.chua.starter.job.support.handler.JobHandler#name()} 对齐)
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
     * 分组 (对应 {@code sys_job_group}), 为空取 {@code default}
     */
    private String group;

    /**
     * 调度类型, 见 {@link #TYPE_CRON} 等常量
     */
    private String scheduleType;

    /**
     * 调度配置: cron 表达式或毫秒间隔
     */
    private String scheduleTime;

    /**
     * 是否启用自动调度; 为空按启用处理
     */
    private Boolean enabled;

    /**
     * 执行参数 (对应 {@code sys_job_execute_param})
     */
    private String executeParam;

    /**
     * 获取业务编号。
     *
     * @return 编号, 可为空
     */
    public String getJobNo() {
        return jobNo;
    }

    /**
     * 设置业务编号。
     *
     * @param jobNo 编号
     */
    public void setJobNo(String jobNo) {
        this.jobNo = jobNo;
    }

    /**
     * 获取任务键。
     *
     * @return 任务键
     */
    public String getJobKey() {
        return jobKey;
    }

    /**
     * 设置任务键。
     *
     * @param jobKey 任务键
     */
    public void setJobKey(String jobKey) {
        this.jobKey = jobKey;
    }

    /**
     * 获取任务显示名。
     *
     * @return 显示名
     */
    public String getJobName() {
        return jobName;
    }

    /**
     * 设置任务显示名。
     *
     * @param jobName 显示名
     */
    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    /**
     * 获取任务描述。
     *
     * @return 描述
     */
    public String getDesc() {
        return desc;
    }

    /**
     * 设置任务描述。
     *
     * @param desc 描述
     */
    public void setDesc(String desc) {
        this.desc = desc;
    }

    /**
     * 获取分组。
     *
     * @return 分组
     */
    public String getGroup() {
        return group;
    }

    /**
     * 设置分组。
     *
     * @param group 分组
     */
    public void setGroup(String group) {
        this.group = group;
    }

    /**
     * 获取调度类型。
     *
     * @return 调度类型
     */
    public String getScheduleType() {
        return scheduleType;
    }

    /**
     * 设置调度类型。
     *
     * @param scheduleType 调度类型
     */
    public void setScheduleType(String scheduleType) {
        this.scheduleType = scheduleType;
    }

    /**
     * 获取调度配置。
     *
     * @return 调度配置
     */
    public String getScheduleTime() {
        return scheduleTime;
    }

    /**
     * 设置调度配置。
     *
     * @param scheduleTime 调度配置
     */
    public void setScheduleTime(String scheduleTime) {
        this.scheduleTime = scheduleTime;
    }

    /**
     * 是否启用自动调度。
     *
     * @return 启用返回 true, 未设置为 true
     */
    public Boolean getEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用自动调度。
     *
     * @param enabled 启用
     */
    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取执行参数。
     *
     * @return 执行参数
     */
    public String getExecuteParam() {
        return executeParam;
    }

    /**
     * 设置执行参数。
     *
     * @param executeParam 执行参数
     */
    public void setExecuteParam(String executeParam) {
        this.executeParam = executeParam;
    }
}
