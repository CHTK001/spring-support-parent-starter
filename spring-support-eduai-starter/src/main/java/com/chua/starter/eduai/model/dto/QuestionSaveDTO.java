package com.chua.starter.eduai.model.dto;

/**
 * 题目保存DTO
 *
 * @param questionId             题目标识（更新时必填）
 * @param questionType           题型
 * @param questionSubject        学科
 * @param questionGradeLevel     年段
 * @param questionGrade          年级
 * @param questionYear           年份
 * @param questionDifficulty     难度（1-5）
 * @param questionScore          总分（单题分值）
 * @param questionKnowledgePointIds 知识点标识列表（逗号分隔）
 * @param questionStem           题干
 * @param questionStemImages     题干图片URL（逗号分隔）
 * @param questionOptions        选项JSON
 * @param questionAnswer         标准答案
 * @param questionAnalysis       解析
 * @param questionSource         来源
 * @param questionSourceType     来源类型（MANUAL/导入/CRAWL）
 * @param questionAuditStatus    审核状态（0-待审核 1-通过 2-驳回；save 接口不采信客户端传值，只能经专用审核接口流转）
 * @param questionAuditRemark    驳回理由（驳回必填；save 接口不采信，只能经专用审核接口写入）
 * @param auditOnSave            保存并审核：管理员新建时直接通过用。非管理员传 true 直接拒绝。
 * @param questionStatus         状态（0-停用 1-启用）
 * @param ownerUserId            归属用户标识（内部链路传递，如采集任务异步落题时继承任务归属；请求线程以登录态为准，客户端传值无效）
 * @since 4.0.0.42
 * @author CH
 */
public record QuestionSaveDTO(
        Long questionId,
        String questionType,
        String questionSubject,
        String questionGradeLevel,
        String questionGrade,
        String questionYear,
        Integer questionDifficulty,
        Integer questionScore,
        String questionKnowledgePointIds,
        String questionStem,
        String questionStemImages,
        String questionOptions,
        String questionAnswer,
        String questionAnalysis,
        String questionSource,
        String questionSourceType,
        Integer questionAuditStatus,
        String questionAuditRemark,
        Boolean auditOnSave,
        Integer questionStatus,
        String ownerUserId) {
}
