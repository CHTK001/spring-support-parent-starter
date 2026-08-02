package com.chua.starter.ai.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * AppKey DTO，字段命名与前端面板对齐。
 *
 * @author CH
 * @since 2026-08-03
 */
@Data
@ApiModel("AppKey DTO")
public class AiAppKeyDTO implements Serializable {

    /**
     * AppKey 主键 ID
     */
    @ApiModelProperty("AppKey 主键 ID")
    private Long id;

    /**
     * AppKey 名称
     */
    @ApiModelProperty("AppKey 名称")
    private String name;

    /**
     * AppKey 密钥值
     */
    @ApiModelProperty("AppKey 密钥值")
    private String key;

    /**
     * 关联供应商 ID
     */
    @ApiModelProperty("关联供应商 ID")
    private Long provider;

    /**
     * 限定使用的模型
     */
    @ApiModelProperty("限定使用的模型")
    private String model;

    /**
     * 是否启用
     */
    @ApiModelProperty("是否启用")
    private Boolean enabled;

    /**
     * 创建时间
     */
    @ApiModelProperty("创建时间")
    private String createdAt;
}
