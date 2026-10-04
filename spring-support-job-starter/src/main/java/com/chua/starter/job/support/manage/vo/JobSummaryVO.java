package com.chua.starter.job.support.manage.vo;

import java.util.List;

/**
 * 调度工作台概览: 本节点运行态 + 任务与日志的计数。
 *
 * <p>把「这一台机器在调度链路里扮演什么角色」和「台账里的任务会被谁执行」放在一起, 避免页面只报
 * 任务数量、却看不出停用的、未注册处理器的、以及本机不领取的远程任务。</p>
 *
 * @param role                    本节点角色: SINGLE / SCHEDULER / EXECUTOR
 * @param nodeId                  本节点标识
 * @param leader                  本节点当前是否为 Leader (只有 Leader 会捞取到期任务)
 * @param group                   所属调度分组
 * @param scanIntervalMs          扫描间隔毫秒
 * @param preReadMs               预读时间窗毫秒
 * @param totalJobs               任务总数 (未删除的台账行)
 * @param runningJobs             启用自动调度的任务数
 * @param stoppedJobs             停用自动调度的任务数
 * @param localJobs               本地分发的任务数
 * @param remoteJobs              远程分发的任务数
 * @param unregisteredJobs        启用了本地调度但处理器未注册的任务数 (每一拍都会被记成 SKIPPED);
 *                                远程分发的行不计入, 那类行的处理器本就在执行器节点上
 * @param unclaimedRemoteJobs     本节点当前发不出去、因而每一拍都会派发失败的远程任务数；
 *                                仅在「本节点是 Leader + 调度中心角色 + 当前零在线执行器」时为非 0
 * @param registeredHandlers      进程内已注册的执行器数量
 * @param recentFailures          近 24 小时执行失败次数
 * @param remoteDispatchOwnedByMe 远程分发是否由本节点下发 (仅调度中心角色为 true)
 * @param onlineExecutors         当前连接上的执行器节点标识 (调度中心角色下才有值)
 * @since 4.0.0.42
 * @author CH
 */
public record JobSummaryVO(
        String role,
        String nodeId,
        boolean leader,
        String group,
        long scanIntervalMs,
        long preReadMs,
        long totalJobs,
        long runningJobs,
        long stoppedJobs,
        long localJobs,
        long remoteJobs,
        long unregisteredJobs,
        long unclaimedRemoteJobs,
        int registeredHandlers,
        long recentFailures,
        boolean remoteDispatchOwnedByMe,
        List<String> onlineExecutors) {

    /**
     * 规范化集合组件，阻断外部别名写入；保留 null 语义。
     */
    public JobSummaryVO {
        onlineExecutors = onlineExecutors == null ? null : List.copyOf(onlineExecutors);
    }
}
