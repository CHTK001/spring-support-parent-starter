package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * AppKey DTO，字段命名与前端面板对齐。
 *
 * @author CH
 * @since 2026-08-03
 */
@Data
@Schema(name = "AppKey DTO")
public class AiAppKeyDTO implements Serializable {

    /**
     * AppKey 主键 ID
     */
    @Schema(description = "AppKey 主键 ID")
    private Long id;

    /**
     * AppKey 名称
     */
    @Schema(description = "AppKey 名称")
    private String name;

    /**
     * AppKey 密钥值
     */
    @Schema(description = "AppKey 密钥值")
    private String key;

    /**
     * 关联供应商 ID
     */
    @Schema(description = "关联供应商 ID")
    private Long provider;

    /**
     * 限定使用的模型
     */
    @Schema(description = "限定使用的模型")
    private String model;

    /**
     * 是否启用
     */
    @Schema(description = "是否启用")
    private Boolean enabled;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private String createdAt;
}
