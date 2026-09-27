package com.chua.starter.job.support.dispatch;

import java.util.Objects;

/**
 * 调度任务 (内存模型)。
 *
 * @since 4.0.0.42
 * @author CH
 */
public class JobDefinition {

    /**
     * 任务名称 (派发标识; 与 {@link com.chua.starter.job.support.annotation.Job#value()} 对齐,
     * 从持久化行还原时取执行器 Bean 名)
     */
    private String name;

    /**
     * 任务显示名 (对应 {@code sys_job_name}), 仅用于展示, 不参与派发
     */
    private String jobName;

    /**
     * 调度分组 (对应 {@code sys_job_group}); 空表示按 {@code default} 落库
     */
    private String group;

    /**
     * cron 表达式 (三选一; 优先级 cron > fixed延迟 > fixedrate)
     */
    private String cron;

    /**
     * 固定延迟, 单位毫秒 (前一次执行完成后等待该时长触发; cron 为空且本值 > 0 时生效)
     */
    private long fixedDelayMs;

    /**
     * 固定频率, 单位毫秒 (以固定间隔触发, 不等前一次完成; 仅 cron 与 fixed延迟 均未配置时生效)
     */
    private long fixedRateMs;

    /**
     * 是否启用
     */
    private boolean enabled;

    /**
     * 任务描述
     */
    private String desc;

    /**
     * 失败重试次数 (对应 {@code sys_job_fail_retry}), 空或 0 为不重试
     */
    private Integer failRetry;

    /**
     * 执行超时秒数 (对应 {@code sys_job_execute_timeout}), 空或 0 为不限
     */
    private Integer executeTimeoutSec;

    /**
     * 重试间隔秒数 (对应 {@code sys_job_retry_interval}), 空或 0 为立即重试
     */
    private Integer retryIntervalSec;

    /**
     * 执行类型 (对应 {@code sys_job_glue_type}): BEAN=本机处理器; 其它值为脚本引擎名
     */
    private String glueType;

    /**
     * 脚本正文 (对应 {@code sys_job_glue_source}), BEAN 时为空
     */
    private String glueSource;

    /**
     * 脚本版本时刻 (毫秒, 对应 {@code sys_job_glue_updatetime})
     */
    private long glueUpdatetimeMs;

    /**
     * 重试前回调处理器名 (对应 {@code sys_job_retry_callback_bean})
     */
    private String retryCallbackBean;

    /**
     * 异常回调处理器名 (对应 {@code sys_job_exception_callback_bean})
     */
    private String exceptionCallbackBean;

    /**
     * 失败告警目标 (对应 {@code sys_job_alarm_email})：邮箱（逗号分隔）或 webhook 地址
     */
    private String alarmEmail;

    /**
     * 失败告警类型 (对应 {@code sys_job_alarm_type})：EMAIL / WEBHOOK，空按 EMAIL
     */
    private String alarmType;

    /**
     * 执行参数 (对应 {@code sys_job_execute_param})：随命令下发，处理器经
     * {@code JobDispatchContext.param()} 读取；脚本任务注入为 {@code executeParam} 变量
     */
    private String executeParam;

    /**
     * 并发策略 (对应 {@code sys_job_concurrent_policy})：SKIP / ALLOW；随命令下发给执行器
     */
    private String concurrentPolicy;

    /**
     * 获取任务名称。
     *
     * @return 名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置任务名称。
     *
     * @param name 名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取任务显示名。
     *
     * @return 显示名, 注解任务可为空
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
     * 获取调度分组。
     *
     * @return 分组名, 未配置为 null
     */
    public String getGroup() {
        return group;
    }

    /**
     * 设置调度分组。
     *
     * @param group 分组名, 留空按 default 落库
     */
    public void setGroup(String group) {
        this.group = group;
    }

    /**
     * 获取 cron 表达式。
     *
     * @return cron
     */
    public String getCron() {
        return cron;
    }

    /**
     * 设置 cron 表达式。
     *
     * @param cron cron
     */
    public void setCron(String cron) {
        this.cron = cron;
    }

    /**
     * 是否启用。
     *
     * @return 启用返回 true
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置启用。
     *
     * @param enabled 启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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
     * 获取固定延迟 (毫秒)。
     *
     * @return 固定延迟毫秒数, 未配置为 0
     */
    public long getFixedDelayMs() {
        return fixedDelayMs;
    }

    /**
     * 设置固定延迟 (毫秒)。
     *
     * @param fixedDelayMs 固定延迟毫秒数
     */
    public void setFixedDelayMs(long fixedDelayMs) {
        this.fixedDelayMs = fixedDelayMs;
    }

    /**
     * 获取固定频率 (毫秒)。
     *
     * @return 固定频率毫秒数, 未配置为 0
     */
    public long getFixedRateMs() {
        return fixedRateMs;
    }

    /**
     * 获取失败重试次数。
     *
     * @return 重试次数, 未配置为 null
     */
    public Integer getFailRetry() {
        return failRetry;
    }

    /**
     * 设置失败重试次数。
     *
     * @param failRetry 重试次数
     */
    public void setFailRetry(Integer failRetry) {
        this.failRetry = failRetry;
    }

    /**
     * 获取执行超时秒数。
     *
     * @return 超时秒数, 未配置为 null
     */
    public Integer getExecuteTimeoutSec() {
        return executeTimeoutSec;
    }

    /**
     * 设置执行超时秒数。
     *
     * @param executeTimeoutSec 超时秒数
     */
    public void setExecuteTimeoutSec(Integer executeTimeoutSec) {
        this.executeTimeoutSec = executeTimeoutSec;
    }

    /**
     * 获取重试间隔秒数。
     *
     * @return 间隔秒数, 未配置为 null
     */
    public Integer getRetryIntervalSec() {
        return retryIntervalSec;
    }

    /**
     * 设置重试间隔秒数。
     *
     * @param retryIntervalSec 间隔秒数
     */
    public void setRetryIntervalSec(Integer retryIntervalSec) {
        this.retryIntervalSec = retryIntervalSec;
    }

    /**
     * 获取执行类型。
     *
     * @return BEAN 或脚本引擎名, 可为空
     */
    public String getGlueType() {
        return glueType;
    }

    /**
     * 设置执行类型。
     *
     * @param glueType BEAN 或脚本引擎名
     */
    public void setGlueType(String glueType) {
        this.glueType = glueType;
    }

    /**
     * 获取脚本正文。
     *
     * @return 脚本正文, BEAN 时为空
     */
    public String getGlueSource() {
        return glueSource;
    }

    /**
     * 设置脚本正文。
     *
     * @param glueSource 脚本正文
     */
    public void setGlueSource(String glueSource) {
        this.glueSource = glueSource;
    }

    /**
     * 获取脚本版本时刻。
     *
     * @return 毫秒时间戳, 0 表示无
     */
    public long getGlueUpdatetimeMs() {
        return glueUpdatetimeMs;
    }

    /**
     * 设置脚本版本时刻。
     *
     * @param glueUpdatetimeMs 毫秒时间戳
     */
    public void setGlueUpdatetimeMs(long glueUpdatetimeMs) {
        this.glueUpdatetimeMs = glueUpdatetimeMs;
    }

    /**
     * 获取重试前回调处理器名。
     *
     * @return 处理器名, 可为空
     */
    public String getRetryCallbackBean() {
        return retryCallbackBean;
    }

    /**
     * 设置重试前回调处理器名。
     *
     * @param retryCallbackBean 处理器名
     */
    public void setRetryCallbackBean(String retryCallbackBean) {
        this.retryCallbackBean = retryCallbackBean;
    }

    /**
     * 获取异常回调处理器名。
     *
     * @return 处理器名, 可为空
     */
    public String getExceptionCallbackBean() {
        return exceptionCallbackBean;
    }

    /**
     * 设置异常回调处理器名。
     *
     * @param exceptionCallbackBean 处理器名
     */
    public void setExceptionCallbackBean(String exceptionCallbackBean) {
        this.exceptionCallbackBean = exceptionCallbackBean;
    }

    /**
     * 获取失败告警目标。
     *
     * @return 邮箱或 webhook 地址, 可为空
     */
    public String getAlarmEmail() {
        return alarmEmail;
    }

    /**
     * 设置失败告警目标。
     *
     * @param alarmEmail 邮箱或 webhook 地址
     */
    public void setAlarmEmail(String alarmEmail) {
        this.alarmEmail = alarmEmail;
    }

    /**
     * 获取失败告警类型。
     *
     * @return EMAIL / WEBHOOK, 可为空
     */
    public String getAlarmType() {
        return alarmType;
    }

    /**
     * 设置失败告警类型。
     *
     * @param alarmType EMAIL / WEBHOOK
     */
    public void setAlarmType(String alarmType) {
        this.alarmType = alarmType;
    }

    /**
     * 获取并发策略。
     *
     * @return SKIP / ALLOW, 可为空
     */
    public String getConcurrentPolicy() {
        return concurrentPolicy;
    }

    /**
     * 设置并发策略。
     *
     * @param concurrentPolicy SKIP / ALLOW
     */
    public void setConcurrentPolicy(String concurrentPolicy) {
        this.concurrentPolicy = concurrentPolicy;
    }

    /**
     * 获取执行参数。
     *
     * @return 执行参数，可为空
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

    /**
     * 设置固定频率 (毫秒)。
     *
     * @param fixedRateMs 固定频率毫秒数
     */
    public void setFixedRateMs(long fixedRateMs) {
        this.fixedRateMs = fixedRateMs;
    }

    @Override
    /**
     * 判断相等
    */
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JobDefinition that)) {
            return false;
        }
        return Objects.equals(name, that.name);
    }

    @Override
    /**
     * 哈希编码
    */
    public int hashCode() {
        return Objects.hash(name);
    }
}
