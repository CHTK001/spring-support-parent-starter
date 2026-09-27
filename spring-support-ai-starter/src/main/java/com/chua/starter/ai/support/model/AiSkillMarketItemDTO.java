package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 在线市场技能条目：由各市场源归一化后回给前端，字段全部来自源侧真实返回。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(name = "在线市场技能条目")
public class AiSkillMarketItemDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    @Schema(description = "市场内唯一标识（含源前缀）")
    private String id;

    @Schema(description = "市场源标识")
    private String source;

    @Schema(description = "本次搜索内的名次")
    private Integer rank;

    @Schema(description = "技能名称")
    private String name;

    @Schema(description = "技能描述，源侧无描述时为空")
    private String description;

    @Schema(description = "作者")
    private String author;

    @Schema(description = "技能来源地址，安装时按此地址下载")
    private String url;

    @Schema(description = "仓库内技能目录，供安装端定位目录")
    private String path;

    @Schema(description = "Star 数，源侧无该指标时为 0")
    private Long stars;

    @Schema(description = "Fork 数，源侧无该指标时为 0")
    private Long forks;

    @Schema(description = "安装次数，源侧无该指标时为 0")
    private Long installs;

    @Schema(description = "内容语言")
    private String contentLanguage;

    @Schema(description = "本地是否已存在同名技能")
    private Boolean installed;
}
