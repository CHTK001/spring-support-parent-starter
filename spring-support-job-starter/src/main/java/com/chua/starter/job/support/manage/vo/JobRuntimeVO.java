package com.chua.starter.job.support.manage.vo;

import com.chua.starter.job.support.dispatch.JobRuntime;

/**
 * 任务运行态出参。
 *
 * <p>把内部快照的哨兵值归一为 {@code null}, 前端不必再判断 -1/0:</p>
 * <ul>
 *   <li>{@code lastFireTime}: 0 → null, 表示从未派发;</li>
 *   <li>{@code nextFireTime}: -1 → null, 表示未配置调度或表达式非法。</li>
 * </ul>
 *
 * @param jobNo                业务编号
 * @param jobKey               任务键 (派发标识, 与处理器名一致)
 * @param jobName              任务显示名
 * @param desc                 任务描述
 * @param group                分组
 * @param scheduleType         调度类型: CRON / FIXED / DELAY / NONE
 * @param scheduleTime         调度配置: cron 表达式或毫秒间隔
 * @param enabled              是否启用自动调度
 * @param executeParam         执行参数
 * @param lastFireTime         最近一次派发时刻 (毫秒时间戳, 空表示无记录)
 * @param nextFireTime         下一次触发时刻 (毫秒时间戳, 空表示不参与自动调度)
 * @param handlerRegistered    当前节点是否已注册同名处理器
 * @param dispatchMode         分发模式: LOCAL-本节点执行 REMOTE-下发注册节点
 * @param remoteExecutorAddress 远程执行器地址
 * @param author               负责人
 * @param failRetry            失败重试次数
 * @param retryInterval        重试间隔秒数, 0 为立即重试
 * @param executeTimeout       执行超时秒数, 0 为不限
 * @param misfireStrategy      错过触发策略
 * @param concurrentPolicy     并发策略: SKIP=上一轮未结束则跳过本拍; ALLOW=允许并发重入
 * @param version              乐观锁版本: 编辑保存时原样回传, 后端据此判断台账是否已被他人修改
 * @param glueType             执行类型: BEAN=本机处理器; 其它值为脚本引擎名 (如 groovy)
 * @param glueSource           脚本正文, 执行类型为 BEAN 时为空
 * @param glueUpdatetimeMs     脚本版本时刻 (毫秒), 0 表示无
 * @param retryCallbackBean    重试前回调处理器名
 * @param exceptionCallbackBean 异常回调处理器名
 * @param alarmEmail           失败告警目标：邮箱（逗号分隔）或 webhook 地址
 * @param alarmType            失败告警类型：EMAIL / WEBHOOK
 * @since 4.0.0.42
 * @author CH
 */
public record JobRuntimeVO(
        String jobNo,
        String jobKey,
        String jobName,
        String desc,
        String group,
        String scheduleType,
        String scheduleTime,
        boolean enabled,
        String executeParam,
        Long lastFireTime,
        Long nextFireTime,
        boolean handlerRegistered,
        String dispatchMode,
        String remoteExecutorAddress,
        String author,
        Integer failRetry,
        Integer retryInterval,
        Integer executeTimeout,
        String misfireStrategy,
        String concurrentPolicy,
        Long version,
        String glueType,
        String glueSource,
        Long glueUpdatetimeMs,
        String retryCallbackBean,
        String exceptionCallbackBean,
        String alarmEmail,
        String alarmType) {

    /**
     * 由内部运行态快照构造出参。
     *
     * @param runtime 快照, 可为空
     * @return 出参, 快照为空时返回 {@code null}
     */
    public static JobRuntimeVO of(JobRuntime runtime) {
        if (runtime == null) {
            return null;
        }
        return new JobRuntimeVO(
                runtime.getJobNo(),
                runtime.getJobKey(),
                runtime.getJobName(),
                runtime.getDesc(),
                runtime.getGroup(),
                runtime.getScheduleType(),
                runtime.getScheduleTime(),
                runtime.isEnabled(),
                runtime.getExecuteParam(),
                runtime.getLastFireMs() > 0L ? runtime.getLastFireMs() : null,
                runtime.getNextFireMs() > 0L ? runtime.getNextFireMs() : null,
                runtime.isHandlerRegistered(),
                runtime.getDispatchMode(),
                runtime.getRemoteExecutorAddress(),
                runtime.getAuthor(),
                runtime.getFailRetry(),
                runtime.getRetryInterval(),
                runtime.getExecuteTimeout(),
                runtime.getMisfireStrategy(),
                runtime.getConcurrentPolicy(),
                runtime.getVersion(),
                runtime.getGlueType(),
                runtime.getGlueSource(),
                runtime.getGlueUpdatetimeMs(),
                runtime.getRetryCallbackBean(),
                runtime.getExceptionCallbackBean(),
                runtime.getAlarmEmail(),
                runtime.getAlarmType());
    }

    /**
     * 返回一个 {@code handlerRegistered} 被改写后的副本。
     *
     * <p>供调用方在合并「在线执行器上报的处理器」后修正该标记：
     * record 不可变，必须返回新实例而不是原地改写。</p>
     *
     * @param registered 是否已注册
     * @return 新的运行态出参
     */
    public JobRuntimeVO withHandlerRegistered(boolean registered) {
        return new JobRuntimeVO(jobNo, jobKey, jobName, desc, group, scheduleType, scheduleTime,
                enabled, executeParam, lastFireTime, nextFireTime, registered, dispatchMode,
                remoteExecutorAddress, author, failRetry, retryInterval, executeTimeout,
                misfireStrategy, concurrentPolicy, version, glueType, glueSource,
                glueUpdatetimeMs, retryCallbackBean, exceptionCallbackBean, alarmEmail, alarmType);
    }
}
