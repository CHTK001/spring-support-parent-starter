package com.chua.starter.eduai.model.vo;

import java.util.List;

/**
 * 老师上传题目结果VO
 *
 * <p>与 {@link QuestionImportResultVO} 同口径的逐条容错结果，
 * 额外带回「是否自动通过」：老师上传的审核态由管理员配置
 * （老师上传自动通过）决定，前端据此提示「已直接通过」
 * 或「已进入待审核队列」。</p>
 *
 * @param successCount 成功入库条数
 * @param failedCount  失败条数
 * @param errors       逐条失败原因（第N条+原因；仅失败时非空）
 * @param autoApproved 是否自动通过（管理员配置开启时为 true）
 *
 * @since 4.0.0.42
 * @author CH
 */
public record TeacherQuestionUploadVO(
        int successCount,
        int failedCount,
        List<String> errors,
        boolean autoApproved
) {
}
