package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 已安装技能：技能根目录下真实存在 SKILL.md 的一个技能。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(name = "已安装技能")
public class AiInstalledSkillDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "技能名（目录名），builtin 与 user 下可能重名")
    private String name;

    @Schema(description = "限定标识：source:name（如 user:pdf、builtin:pdf），启停与卸载按此定位")
    private String id;

    @Schema(description = "技能描述，取 SKILL.md 首个非空行")
    private String description;

    @Schema(description = "图标（图片地址或图标名，可空）")
    private String icon;

    @Schema(description = "落盘位置")
    private String location;

    @Schema(description = "来源：custom=用户安装，builtin=内置")
    private String source;

    @Schema(description = "是否参与对话注入")
    private Boolean enabled;

    @Schema(description = "SKILL.md 正文字符数")
    private Integer contentLength;
}
