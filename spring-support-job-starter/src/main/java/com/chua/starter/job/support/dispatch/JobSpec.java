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
     * 分发模式: 本节点领取执行
     */
    public static final String DISPATCH_LOCAL = "LOCAL";

    /**
     * 分发模式: 远程
     */
    public static final String DISPATCH_REMOTE = "REMOTE";

    /**
     * 执行类型: 本机处理器 (默认)
     */
    public static final String GLUE_BEAN = "BEAN";

    /**
     * 脚本任务自动标识前缀（用户不填任务键时：prefix + IdUtils snowflake）
     */
    public static final String SCRIPT_JOB_KEY_PREFIX = "script-";

    /**
     * 错过触发策略: 不补跑
     */
    public static final String MISFIRE_DO_NOTHING = "DO_NOTHING";

    /**
     * 错过触发策略: 补跑一次
     */
    public static final String MISFIRE_FIRE_ONCE_NOW = "FIRE_ONCE_NOW";

    /**
     * 并发策略: 上一轮未结束时跳过本次 (记 SKIPPED 日志, 不排队)
     */
    public static final String CONCURRENT_SKIP = "SKIP";

    /**
     * 并发策略: 允许并发重入 (保持历史行为; 周期短于耗时的任务会无限堆积)
     */
    public static final String CONCURRENT_ALLOW = "ALLOW";

    /**
     * 归一化并发策略: 空值或无法识别时回落到 {@link #CONCURRENT_SKIP}。
     *
     * <p>默认 SKIP 而不是历史行为 ALLOW: 允许并发意味着「周期短于耗时」的任务会每一拍
     * 再叠一份, 线程与外部资源被无上限地拖垮, 且没有任何告警。存量行该列缺失时同样按
     * ALLOW 兼容 (见 MybatisJobRepository 的列缺失降级), 但新建行一律走 SKIP。</p>
     *
     * @param policy 原始策略值, 可为空
     * @return 归一化后的策略 (SKIP / ALLOW)
     */
    public static String normalizeConcurrentPolicy(String policy) {
        if (policy == null) {
            return CONCURRENT_SKIP;
        }
        String trimmed = policy.trim();
        if (CONCURRENT_ALLOW.equalsIgnoreCase(trimmed)) {
            return CONCURRENT_ALLOW;
        }
        if (CONCURRENT_SKIP.equalsIgnoreCase(trimmed)) {
            return CONCURRENT_SKIP;
        }
        return CONCURRENT_SKIP;
    }

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
     * 分发模式 (对应 {@code sys_job_dispatch_mode}): {@link #DISPATCH_LOCAL} 与
     * {@link #DISPATCH_REMOTE} <b>都由本节点 Leader 领取并下发</b>，两者都不改变
     * 「能不能跑」——{@code findDue} 不按该字段过滤。区别只在落地方式：REMOTE 行经同步通道
     * 转发给在线执行器执行，因此依赖「本 group 至少有一个在线执行器」；没有接收方时命令被丢弃
     * （并记一条 SKIPPED 日志，见 {@code SchedulerSyncBinder#recordMissedDispatch}）。
     * 为空按 {@link #DISPATCH_LOCAL} 处理。
     */
    private String dispatchMode;

    /**
     * 远程执行器地址 (对应 {@code sys_job_remote_executor_address}), 仅远程分发有意义
     */
    private String remoteExecutorAddress;

    /**
     * 负责人 (对应 {@code sys_job_author}), 页面展示与告警归属用, 不参与调度
     */
    private String author;

    /**
     * 失败重试次数 (对应 {@code sys_job_fail_retry}), 空或 0 为不重试
     */
    private Integer failRetry;

    /**
     * 重试间隔秒数 (对应 {@code sys_job_retry_interval}), 空或 0 为立即重试
     */
    private Integer retryInterval;

    /**
     * 执行超时秒数 (对应 {@code sys_job_execute_timeout}), 0 或空为不限
     */
    private Integer executeTimeout;

    /**
     * 错过触发策略 (对应 {@code sys_job_execute_misfire_strategy}), 空为后端默认
     */
    private String misfireStrategy;

    /**
     * 并发策略 (对应 {@code sys_job_concurrent_policy}): SKIP=上一轮未结束则跳过; ALLOW=允许并发重入
     */
    private String concurrentPolicy;

    /**
     * 执行类型 (对应 {@code sys_job_glue_type}): BEAN=本机处理器; 其它值为脚本引擎名 (如 groovy)
     */
    private String glueType;

    /**
     * 脚本正文 (对应 {@code sys_job_glue_source}), 执行类型为 BEAN 时为空
     */
    private String glueSource;

    /**
     * 脚本版本时刻 (毫秒, 对应 {@code sys_job_glue_updatetime}); 内容变更时失效执行器缓存
     */
    private Long glueUpdatetime;

    /**
     * 重试前回调处理器名 (对应 {@code sys_job_retry_callback_bean}), 每次重试前调用
     */
    private String retryCallbackBean;

    /**
     * 异常回调处理器名 (对应 {@code sys_job_exception_callback_bean}), 最终失败时调用
     */
    private String exceptionCallbackBean;

    /**
     * 失败告警目标 (对应 {@code sys_job_alarm_email}), 最终失败时投递：邮箱（逗号分隔）或 webhook 地址
     */
    private String alarmEmail;

    /**
     * 失败告警类型 (对应 {@code sys_job_alarm_type}): EMAIL / WEBHOOK, 空按 EMAIL
     */
    private String alarmType;

    /**
     * 编辑前的任务键 (仅治理保存用): 与 {@link #jobKey} 不同时表示重命名而不是新建
     */
    private String originJobKey;

    /**
     * 编辑前读到的乐观锁版本 (仅治理保存用): 非空时按此版本做一次 CAS 更新，
     * 版本不一致说明台账已被他人改过，拒绝保存而不是静默覆盖
     */
    private Long version;

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

    /**
     * 获取分发模式。
     *
     * @return 分发模式, 可为空 (按本地分发处理)
     */
    public String getDispatchMode() {
        return dispatchMode;
    }

    /**
     * 设置分发模式。
     *
     * @param dispatchMode 分发模式
     */
    public void setDispatchMode(String dispatchMode) {
        this.dispatchMode = dispatchMode;
    }

    /**
     * 获取远程执行器地址。
     *
     * @return 地址, 可为空
     */
    public String getRemoteExecutorAddress() {
        return remoteExecutorAddress;
    }

    /**
     * 设置远程执行器地址。
     *
     * @param remoteExecutorAddress 地址
     */
    public void setRemoteExecutorAddress(String remoteExecutorAddress) {
        this.remoteExecutorAddress = remoteExecutorAddress;
    }

    /**
     * 获取负责人。
     *
     * @return 负责人
     */
    public String getAuthor() {
        return author;
    }

    /**
     * 设置负责人。
     *
     * @param author 负责人
     */
    public void setAuthor(String author) {
        this.author = author;
    }

    /**
     * 获取失败重试次数。
     *
     * @return 重试次数
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
     * 获取重试间隔秒数。
     *
     * @return 间隔秒数, 未配置为 null
     */
    public Integer getRetryInterval() {
        return retryInterval;
    }

    /**
     * 设置重试间隔秒数。
     *
     * @param retryInterval 间隔秒数, 0 为立即重试
     */
    public void setRetryInterval(Integer retryInterval) {
        this.retryInterval = retryInterval;
    }

    /**
     * 获取执行超时秒数。
     *
     * @return 超时秒数
     */
    public Integer getExecuteTimeout() {
        return executeTimeout;
    }

    /**
     * 设置执行超时秒数。
     *
     * @param executeTimeout 超时秒数, 0 为不限
     */
    public void setExecuteTimeout(Integer executeTimeout) {
        this.executeTimeout = executeTimeout;
    }

    /**
     * 获取错过触发策略。
     *
     * @return 策略名
     */
    public String getMisfireStrategy() {
        return misfireStrategy;
    }

    /**
     * 设置错过触发策略。
     *
     * @param misfireStrategy 策略名
     */
    public void setMisfireStrategy(String misfireStrategy) {
        this.misfireStrategy = misfireStrategy;
    }

    /**
     * 获取并发策略。
     *
     * @return SKIP / ALLOW, 未设置时为 null (由落库侧取安全默认)
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
     * 获取执行类型。
     *
     * @return BEAN 或脚本引擎名, 可为空 (按 BEAN 处理)
     */
    public String getGlueType() {
        return glueType;
    }

    /**
     * 设置执行类型。
     *
     * @param glueType BEAN 或脚本引擎名 (如 groovy)
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
     * @return 毫秒时间戳, 可为空
     */
    public Long getGlueUpdatetime() {
        return glueUpdatetime;
    }

    /**
     * 设置脚本版本时刻。
     *
     * @param glueUpdatetime 毫秒时间戳
     */
    public void setGlueUpdatetime(Long glueUpdatetime) {
        this.glueUpdatetime = glueUpdatetime;
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
     * 获取编辑前任务键。
     *
     * @return 编辑前任务键, 新建时为空
     */
    public String getOriginJobKey() {
        return originJobKey;
    }

    /**
     * 设置编辑前任务键。
     *
     * @param originJobKey 编辑前任务键
     */
    public void setOriginJobKey(String originJobKey) {
        this.originJobKey = originJobKey;
    }

    /**
     * 获取编辑前读到的乐观锁版本。
     *
     * @return 版本, 为空表示本次保存不做并发校验
     */
    public Long getVersion() {
        return version;
    }

    /**
     * 设置编辑前读到的乐观锁版本。
     *
     * @param version 版本
     */
    public void setVersion(Long version) {
        this.version = version;
    }
}
