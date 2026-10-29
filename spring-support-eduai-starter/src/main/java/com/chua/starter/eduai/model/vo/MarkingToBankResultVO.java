package com.chua.starter.eduai.model.vo;

import java.util.List;

/**
 * 外来卷题目加入题库的结果。
 *
 * <p>逐条容错：单条失败不影响其余题目，失败原因随结果返回，前端可直接展示。</p>
 *
 * @param successCount   成功入库题数
 * @param duplicateCount 因题库已有同题干而跳过的题数（不算失败）
 * @param failedCount    失败题数
 * @param errors         逐条失败原因（题号 + 原因）
 * @param needAnswerNos  缺标准答案、未入库的题号（老师补写后可再次提交）
 * @since 4.0.0.42
 * @author CH
 */
public record MarkingToBankResultVO(
        int successCount,
        int duplicateCount,
        int failedCount,
        List<String> errors,
        List<String> needAnswerNos) {
}
