package com.chua.starter.job.support.manage.dto;

import com.chua.starter.job.support.dispatch.JobSpec;
import lombok.Data;

/**
 * 任务登记入参。
 *
 * <p>与 {@link JobSpec} 一一对应, 单独一层是为了让 HTTP 契约与调度内部模型解耦:
 * 页面多传或漏传的字段在此收口, 调度侧看到的仍是完整规格。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class JobSpecDTO {

    /**
     * 任务键 (执行器 Bean 名), 必填
     */
    private String jobKey;

    /**
     * 业务编号, 留空由后端取任务键
     */
    private String jobNo;

    /**
     * 任务显示名
     */
    private String jobName;

    /**
     * 任务描述
     */
    private String desc;

    /**
     * 分组, 留空取 default
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
     * 是否启用自动调度, 留空按启用处理
     */
    private Boolean enabled;

    /**
     * 执行参数
     */
    private String executeParam;

    /**
     * 分发模式: LOCAL / REMOTE, 留空按 LOCAL
     */
    private String dispatchMode;

    /**
     * 远程执行器地址, 仅远程分发有意义
     */
    private String remoteExecutorAddress;

    /**
     * 负责人
     */
    private String author;

    /**
     * 失败重试次数
     */
    private Integer failRetry;

    /**
     * 重试间隔秒数, 0 为立即重试
     */
    private Integer retryInterval;

    /**
     * 执行超时秒数, 0 为不限
     */
    private Integer executeTimeout;

    /**
     * 错过触发策略
     */
    private String misfireStrategy;

    /**
     * 并发策略: SKIP=上一轮未结束则跳过本拍; ALLOW=允许并发重入
     */
    private String concurrentPolicy;

    /**
     * 执行类型: BEAN=本机处理器(默认); 其它值为脚本引擎名 (如 groovy)
     */
    private String glueType;

    /**
     * 脚本正文, 执行类型非 BEAN 时必填
     */
    private String glueSource;

    /**
     * 重试前回调处理器名, 每次重试前调用
     */
    private String retryCallbackBean;

    /**
     * 异常回调处理器名, 最终失败时调用
     */
    private String exceptionCallbackBean;

    /**
     * 失败告警目标：邮箱（逗号分隔）或 webhook 地址
     */
    private String alarmEmail;

    /**
     * 失败告警类型：EMAIL / WEBHOOK，空按 EMAIL
     */
    private String alarmType;

    /**
     * 编辑前的任务键（与 jobKey 不同时按重命名处理）
     */
    private String originJobKey;

    /**
     * 编辑前读到的乐观锁版本：非空时后端按此版本做 CAS 更新，
     * 版本不一致说明台账已被他人改过，保存会被拒绝
     */
    private Long version;

    /**
     * 转换为调度侧任务规格。
     *
     * @return 任务规格, 当前对象为空时返回 {@code null}
     */
    public JobSpec toSpec() {
        JobSpec spec = new JobSpec();
        spec.setJobKey(jobKey);
        spec.setJobNo(jobNo);
        spec.setJobName(jobName);
        spec.setDesc(desc);
        spec.setGroup(group);
        spec.setScheduleType(scheduleType);
        spec.setScheduleTime(scheduleTime);
        spec.setEnabled(enabled);
        spec.setExecuteParam(executeParam);
        spec.setDispatchMode(dispatchMode);
        spec.setRemoteExecutorAddress(remoteExecutorAddress);
        spec.setAuthor(author);
        spec.setFailRetry(failRetry);
        spec.setRetryInterval(retryInterval);
        spec.setExecuteTimeout(executeTimeout);
        spec.setMisfireStrategy(misfireStrategy);
        spec.setConcurrentPolicy(concurrentPolicy);
        spec.setGlueType(glueType);
        spec.setGlueSource(glueSource);
        spec.setRetryCallbackBean(retryCallbackBean);
        spec.setExceptionCallbackBean(exceptionCallbackBean);
        spec.setAlarmEmail(alarmEmail);
        spec.setAlarmType(alarmType);
        spec.setOriginJobKey(originJobKey);
        spec.setVersion(version);
        return spec;
    }
}
