package com.chua.starter.ai.support.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * AppKey DTO，字段命名与前端面板对齐。
 *
 * @since 2026-08-03
 * @author CH
 */
@Data
@Schema(name = "AppKey DTO")
public class AiAppKeyDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * AppKey 主键 ID
     */
    @Schema(description = "AppKey 主键 ID")
    /** ID */
    private Long id;

    /**
     * AppKey 名称
     */
    @Schema(description = "AppKey 名称")
    /** 名称 */
    private String name;

    /**
     * AppKey 密钥值
     */
    @Schema(description = "AppKey 密钥值")
    /** 密钥 */
    private String key;

    /**
     * 关联供应商 ID
     */
    @Schema(description = "关联供应商 ID")
    /** 提供者 */
    private Long provider;

    /**
     * 供应商名称
     */
    @Schema(description = "供应商名称")
    /** 提供者名称 */
    private String providerName;

    /**
     * 限定使用的模型
     */
    @Schema(description = "限定使用的模型")
    /** 模型 */
    private String model;

    /**
     * 绑定分组 ID
     */
    @Schema(description = "绑定分组 ID")
    /** 分组ID */
    private Long groupId;

    /**
     * 分组名称
     */
    @Schema(description = "分组名称")
    /** 分组名称 */
    private String groupName;

    /**
     * 已使用次数
     */
    @Schema(description = "已使用次数")
    /** USE数量 */
    private Long useCount;

    /**
     * 次数上限
     */
    @Schema(description = "次数上限")
    /** 最大值USE */
    private Long maxUse;

    /**
     * 过期时间
     */
    @Schema(description = "过期时间")
    /** Expire时间 */
    private String expireTime;

    /**
     * 是否启用
     */
    @Schema(description = "是否启用")
    /** 是否启用 */
    private Boolean enabled;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    /** 创建时间AT */
    private String createdAt;
}