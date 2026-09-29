package com.chua.starter.eduai.model.dto;

/**
 * 手动组卷-卷头/纸张/模板更新DTO
 *
 * <p>字段为空表示不修改；header / footer 传空字符串表示清空。</p>
 *
 * @param name 试卷名称
 * @param subject 学科
 * @param gradeLevel 学段（码值或中文名，服务端归一化）
 * @param grade 年级
 * @param duration 考试时长（分钟）
 * @param pageSize 纸张规格: A4/A3/A5/B5/LETTER
 * @param pageOrientation 纸张方向: PORTRAIT/LANDSCAPE
 * @param template 首页模板标识
 * @param titleStyle 大标题风格: default/line/double/calligraphy
 * @param header 页头（富文本）
 * @param footer 页脚（富文本）
 * @param subjectLocked 学科/学段是否锁定: 0-否 1-是
 * @param difficulty 试卷整体难度 1-5
 * @param templateId 引用的组卷模板ID
 * @param totalScore 试卷满分（目标总分）；为空时新建卷按默认 100
 *
 * @since 4.0.0.42
 * @author CH
 */
public record PaperComposeUpdateDTO(
        String name,
        String subject,
        String gradeLevel,
        String grade,
        Integer duration,
        String pageSize,
        String pageOrientation,
        String template,
        String titleStyle,
        String header,
        String footer,
        Integer subjectLocked,
        Integer difficulty,
        Long templateId,
        Integer totalScore
) {
}
