package com.chua.starter.eduai.model.vo;

import java.util.List;

/**
 * 阅卷完成预览结果。
 *
 * <p>老师点「完成」前先调预览：按当前判分阈值把将登记的错题清单列出来，
 * 老师可勾选剔除部分题目再确认，避免把「刚及格」的题误收进错题本。</p>
 *
 * @param taskId      任务标识
 * @param studentId   错题归属学生
 * @param wrongCount  将登记的题目数
 * @param wrongRate   当前判错得分率阈值（0~1；0 表示仅 0 分判错）
 * @param questions   预览题目清单
 * @since 4.0.0.42
 * @author CH
 */
public record MarkingFinishPreviewVO(
        String taskId,
        String studentId,
        int wrongCount,
        double wrongRate,
        List<MarkingPreviewQuestionVO> questions) {
}
