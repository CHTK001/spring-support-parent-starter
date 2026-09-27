package com.chua.starter.job.support.manage.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 调度工作台概览: 本节点运行态 + 任务与日志的计数。
 *
 * <p>把「这一台机器在调度链路里扮演什么角色」和「台账里的任务会被谁执行」放在一起, 避免页面只报
 * 任务数量、却看不出停用的、未注册处理器的、以及本机不领取的远程任务。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class JobSummaryVO {

    /**
     * 本节点角色: SINGLE / SCHEDULER / EXECUTOR
     */
    private String role;

    /**
     * 本节点标识
     */
    private String nodeId;

    /**
     * 本节点当前是否为 Leader (只有 Leader 会捞取到期任务)
     */
    private boolean leader;

    /**
     * 所属调度分组
     */
    private String group;

    /**
     * 扫描间隔毫秒
     */
    private long scanIntervalMs;

    /**
     * 预读时间窗毫秒
     */
    private long preReadMs;

    /**
     * 任务总数 (未删除的台账行)
     */
    private long totalJobs;

    /**
     * 启用自动调度的任务数
     */
    private long runningJobs;

    /**
     * 停用自动调度的任务数
     */
    private long stoppedJobs;

    /**
     * 本地分发的任务数
     */
    private long localJobs;

    /**
     * 远程分发的任务数
     */
    private long remoteJobs;

    /**
     * 启用了本地调度但处理器未注册的任务数 (每一拍都会被记成 SKIPPED);
     * 远程分发的行不计入, 那类行的处理器本就在执行器节点上
     */
    private long unregisteredJobs;

    /**
     * 本节点当前发不出去、因而每一拍都会派发失败的远程任务数。
     *
     * <p>仅在「本节点是 Leader + 调度中心角色 + 当前零在线执行器」时为非 0：此时启用的 REMOTE 行
     * 会被本节点扫到并下发，但总线上没有接收方，命令被丢弃、不执行。单机角色不在此列，
     * 因为它的远程行由本机 JobExecutor 就地消费，并不依赖在线执行器。</p>
     *
     * <p>代码里有一条兜底痕迹（{@code SchedulerSyncBinder#recordMissedDispatch} 会按任务 60s 节流地
     * 尝试写一条 SKIPPED 丢单日志），但本字段不能因此省略：那条日志的写入被 try/catch 包住、
     * 任务名取自 {@code commandJobName(payload)} 而非台账 jobKey、且它是 SKIPPED 不进 FAILURE 口径，
     * 都使它无法充当权威信号。本字段是对台账直接求和的实时量。</p>
     */
    private long unclaimedRemoteJobs;

    /**
     * 进程内已注册的执行器数量
     */
    private int registeredHandlers;

    /**
     * 近 24 小时执行失败次数
     */
    private long recentFailures;

    /**
     * 远程分发是否由本节点下发 (仅调度中心角色为 true)
     */
    private boolean remoteDispatchOwnedByMe;

    /**
     * 当前连接上的执行器节点标识 (调度中心角色下才有值)
     */
    private List<String> onlineExecutors = new ArrayList<>();
}
