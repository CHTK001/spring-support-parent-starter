package com.chua.starter.eduai.model.dto;

import java.util.List;

/**
 * 批量阅卷操作请求体。
 *
 * <p>批量完成 / 批量撤销共用此 DTO：完成时可选带 {@code excludeQuestionIdsByTask}，
 * 表示每个任务下老师勾选剔除、不登记进错题本的题目。</p>
 *
 * @param taskIds                   任务标识列表（必填，1~50）
 * @param excludeQuestionIdsByTask  任务 → 剔除题目列表（可空；批量完成时生效，批量撤销忽略）
 * @since 4.0.0.42
 * @author CH
 */
public record BatchMarkingRequest(
        List<String> taskIds,
        java.util.Map<String, List<Long>> excludeQuestionIdsByTask) {

    /**
     * 构造便捷方法：只有任务列表时使用。
     *
     * @param taskIds 任务标识列表
     */
    public static BatchMarkingRequest of(List<String> taskIds) {
        return new BatchMarkingRequest(taskIds, null);
    }
}
