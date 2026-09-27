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
     * 分发模式: LOCAL-本节点执行 REMOTE-下发注册节点
     */
    private String dispatchMode;

    /**
     * 远程执行器地址
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
     * 执行类型: BEAN=本机处理器; 其它值为脚本引擎名 (如 groovy)
     */
    private String glueType;

    /**
     * 脚本正文, 执行类型为 BEAN 时为空
     */
    private String glueSource;

    /**
     * 脚本版本时刻 (毫秒), 0 表示无
     */
    private Long glueUpdatetimeMs;

    /**
     * 重试前回调处理器名
     */
    private String retryCallbackBean;

    /**
     * 异常回调处理器名
     */
    private String exceptionCallbackBean;

    /**
     * 失败告警目标：邮箱（逗号分隔）或 webhook 地址
     */
    private String alarmEmail;

    /**
     * 失败告警类型：EMAIL / WEBHOOK
     */
    private String alarmType;

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
        vo.setDispatchMode(runtime.getDispatchMode());
        vo.setRemoteExecutorAddress(runtime.getRemoteExecutorAddress());
        vo.setAuthor(runtime.getAuthor());
        vo.setFailRetry(runtime.getFailRetry());
        vo.setRetryInterval(runtime.getRetryInterval());
        vo.setExecuteTimeout(runtime.getExecuteTimeout());
        vo.setMisfireStrategy(runtime.getMisfireStrategy());
        vo.setConcurrentPolicy(runtime.getConcurrentPolicy());
        vo.setGlueType(runtime.getGlueType());
        vo.setGlueSource(runtime.getGlueSource());
        vo.setGlueUpdatetimeMs(runtime.getGlueUpdatetimeMs());
        vo.setRetryCallbackBean(runtime.getRetryCallbackBean());
        vo.setExceptionCallbackBean(runtime.getExceptionCallbackBean());
        vo.setAlarmEmail(runtime.getAlarmEmail());
        vo.setAlarmType(runtime.getAlarmType());
        return vo;
    }
}
