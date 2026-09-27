package com.chua.starter.server.support.model.vo;

/**
 * 发布任务进度帧（SSE 推送）
 * <p>
 * 一帧对应一个阶段：STEP 带 {@code step}、LOG 带 {@code line}、COMPLETE/ERROR 带 {@code message}；
 * 未使用的可选项为 {@code null}，序列化时按全局包含策略省略。
 * 字段名与历史 Map 载荷完全一致。
 * </p>
 *
 * @param taskId      任务标识
 * @param projectId   发布项目标识
 * @param operation   操作类型
 * @param phase       当前阶段
 * @param currentStep 当前步骤名
 * @param progress    进度百分比（0-100）
 * @param startTime   任务开始时间戳（毫秒）
 * @param step        本帧进入的步骤名，非 STEP 帧为 {@code null}
 * @param line        本帧日志行，非 LOG 帧为 {@code null}
 * @param message     结束说明或错误信息，未结束时为 {@code null}
 * @param endTime     结束时间戳（毫秒），未结束时为 {@code null}
 * @param success     是否成功，未结束时为 {@code null}
 * @author CH
 * @since 4.0.0.42
 */
public record ProjectTaskEventVO(
        String taskId,
        Long projectId,
        String operation,
        String phase,
        String currentStep,
        int progress,
        long startTime,
        String step,
        String line,
        String message,
        Long endTime,
        Boolean success
) {
}
