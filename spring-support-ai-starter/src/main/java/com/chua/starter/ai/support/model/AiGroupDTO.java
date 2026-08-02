package com.chua.starter.ai.support.model;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 模型分组 DTO，字段命名与前端面板对齐。
 *
 * @author CH
 * @since 2026-08-03
 */
@Data
@ApiModel("模型分组 DTO")
public class AiGroupDTO implements Serializable {

    /**
     * 分组主键 ID
     */
    @ApiModelProperty("分组主键 ID")
    private Long id;

    /**
     * 分组名称
     */
    @ApiModelProperty("分组名称")
    private String name;

    /**
     * 分组描述
     */
    @ApiModelProperty("分组描述")
    private String description;

    /**
     * 关联模型列表
     */
    @ApiModelProperty("关联模型列表")
    private List<String> models;

    /**
     * 绑定 AppKey ID 列表
     */
    @ApiModelProperty("绑定 AppKey ID 列表")
    private List<Long> appKeyIds;

    /**
     * 创建时间
     */
    @ApiModelProperty("创建时间")
    private String createdAt;
}
