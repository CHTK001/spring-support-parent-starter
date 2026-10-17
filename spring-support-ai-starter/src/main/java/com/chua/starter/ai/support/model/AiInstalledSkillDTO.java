package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 已安装技能：技能根目录下真实存在 SKILL.md 的一个技能。
 *
 * @param name          技能名（目录名），builtin 与 user 下可能重名
 * @param id            限定标识：source:name（如 user:pdf、builtin:pdf），启停与卸载按此定位
 * @param description   技能描述，取 SKILL.md 首个非空行
 * @param icon          图标（图片地址或图标名，可空）
 * @param location      落盘位置
 * @param source        来源：custom=用户安装，builtin=内置
 * @param enabled       是否参与对话注入
 * @param contentLength SKILL.md 正文字符数
 * @author CH
 * @since 4.0.0.42
 */
@Schema(name = "已安装技能")
public record AiInstalledSkillDTO(
        @Schema(description = "技能名（目录名），builtin 与 user 下可能重名") String name,
        @Schema(description = "限定标识：source:name（如 user:pdf、builtin:pdf），启停与卸载按此定位") String id,
        @Schema(description = "技能描述，取 SKILL.md 首个非空行") String description,
        @Schema(description = "图标（图片地址或图标名，可空）") String icon,
        @Schema(description = "落盘位置") String location,
        @Schema(description = "来源：custom=用户安装，builtin=内置") String source,
        @Schema(description = "是否参与对话注入") Boolean enabled,
        @Schema(description = "SKILL.md 正文字符数") Integer contentLength) implements Serializable {

    private static final long serialVersionUID = 1L;
}
