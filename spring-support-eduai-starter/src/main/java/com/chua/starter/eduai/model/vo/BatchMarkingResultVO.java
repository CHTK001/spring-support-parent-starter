package com.chua.starter.eduai.model.vo;

/**
 * 批量阅卷操作结果（逐任务）。
 *
 * <p>批量完成 / 批量撤销不整体回滚：单个任务失败只在该条目记录失败原因，
 * 其它任务照常执行，避免一个坏任务拖垮整批操作。</p>
 *
 * @param taskId       任务标识
 * @param success      是否成功
 * @param message      成功或失败原因
 * @param wrongCount   完成时本次登记的错题数（撤销时为 0）
 * @param removedCount 撤销时移除的错题数（完成时为 0）
 * @since 4.0.0.42
 * @author CH
 */
public record BatchMarkingResultVO(
        String taskId,
        boolean success,
        String message,
        int wrongCount,
        int removedCount) {
}
