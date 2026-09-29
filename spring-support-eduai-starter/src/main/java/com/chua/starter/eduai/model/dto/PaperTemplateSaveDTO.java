package com.chua.starter.eduai.model.dto;

/**
 * 组卷模板保存DTO
 *
 * <p>新增与更新共用；更新时字段为空表示不修改，
 * 页眉/页脚传空字符串表示清空。</p>
 *
 * @param templateName 模板名称（新增必填）
 * @param templateStyle 大标题风格: classic/modern/simple/exam
 * @param templateHeader 页眉（富文本）
 * @param templateFooter 页脚（富文本）
 * @param templatePageSize 纸张规格: A4/A3/A5/B5/LETTER
 * @param templateOrientation 纸张方向: PORTRAIT/LANDSCAPE
 * @param templateStatus 状态: 0-停用 1-启用
 *
 * @since 4.0.0.42
 * @author CH
 */
public record PaperTemplateSaveDTO(
        String templateName,
        String templateStyle,
        String templateHeader,
        String templateFooter,
        String templatePageSize,
        String templateOrientation,
        Integer templateStatus
) {
}
