package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 模型分组 DTO，字段命名与前端面板对齐。
 *
 * @since 2026-08-03
 * @author CH
 */
@Data
@Schema(name = "模型分组 DTO")
public class AiGroupDTO implements Serializable {

    /**
     * 分组主键 ID
     */
    @Schema(description = "分组主键 ID")
    /** ID */
    private Long id;

    /**
     * 分组名称
     */
    @Schema(description = "分组名称")
    /** 名称 */
    private String name;

    /**
     * 分组描述
     */
    @Schema(description = "分组描述")
    /** 描述 */
    private String description;

    /**
     * 关联模型列表
     */
    @Schema(description = "关联模型列表")
    /** Models */
    private List<String> models;

    /**
     * 绑定 AppKey ID 列表
     */
    @Schema(description = "绑定 AppKey ID 列表")
    /** APP密钥IDS */
    private List<Long> appKeyIds;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    /** 创建时间AT */
    private String createdAt;
}
