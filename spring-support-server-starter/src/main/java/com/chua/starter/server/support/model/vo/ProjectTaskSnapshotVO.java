package com.chua.starter.server.support.model.vo;

import java.util.List;

/**
 * 发布任务快照（断点续传）
 * <p>
 * 前端重连后一次拉齐当前进度与缓冲日志，再续接 SSE 推送；字段名与历史 Map 载荷完全一致，
 * 未结束时 {@code endTime} 为 0。
 * </p>
 *
 * @param taskId      任务标识
 * @param projectId   发布项目标识
 * @param operation   操作类型
 * @param phase       当前阶段
 * @param currentStep 当前步骤名
 * @param progress    进度百分比（0-100）
 * @param finished    是否已结束
 * @param success     是否成功（未结束时为 false）
 * @param message     结束说明或错误信息
 * @param startTime   任务开始时间戳（毫秒）
 * @param endTime     任务结束时间戳（毫秒），未结束为 0
 * @param logs        缓冲日志（上限内）
 * @author CH
 * @since 4.0.0.42
 */
public record ProjectTaskSnapshotVO(
        String taskId,
        Long projectId,
        String operation,
        String phase,
        String currentStep,
        int progress,
        boolean finished,
        boolean success,
        String message,
        long startTime,
        long endTime,
        List<String> logs
) {

    /**
     * 规范构造器：缓冲日志做防御性拷贝。
     *
     * <p>value class 前置条件——集合组件必须深不可变。唯一构造点传入的是 {@code state.logs}
     * 在同步块内的快照副本、非空且日志行已归一化为非 null，故可直接使用 {@code List.copyOf}。</p>
     *
     * @param taskId      任务标识
     * @param projectId   发布项目标识
     * @param operation   操作类型
     * @param phase       当前阶段
     * @param currentStep 当前步骤名
     * @param progress    进度百分比（0-100）
     * @param finished    是否已结束
     * @param success     是否成功（未结束时为 false）
     * @param message     结束说明或错误信息
     * @param startTime   任务开始时间戳（毫秒）
     * @param endTime     任务结束时间戳（毫秒），未结束为 0
     * @param logs        缓冲日志（上限内）
     */
    public ProjectTaskSnapshotVO {
        logs = List.copyOf(logs);
    }
}
