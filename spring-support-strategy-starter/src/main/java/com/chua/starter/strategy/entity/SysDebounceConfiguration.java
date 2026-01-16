package com.chua.starter.strategy.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 防抖配置实体
 * 
 * 用于存储防抖策略的配置信息，支持通过页面动态调整。
 * 数据库配置优先级高于注解配置。
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Data
@TableName("sys_debounce_configuration")
@Schema(description = "防抖配置")
public class SysDebounceConfiguration implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    @Schema(description = "主键ID")
    private Long sysDebounceId;

    /**
     * 防抖器名称
     */
    @Schema(description = "防抖器名称")
    private String sysDebounceName;

    /**
     * 服务/接口路径，支持Ant风格匹配
     */
    @Schema(description = "服务/接口路径，支持Ant风格匹配")
    private String sysDebouncePath;

    /**
     * 防抖时间间隔
     * 支持格式：1000, 1S, 1MIN, 1H
     */
    @Schema(description = "防抖时间间隔，支持格式：1000, 1S, 1MIN, 1H")
    private String sysDebounceDuration;

    /**
     * 防抖键表达式，支持SpEL
     */
    @Schema(description = "防抖键表达式，支持SpEL")
    private String sysDebounceKey;

    /**
     * 防抖模式
     * global: 全局防抖
     * ip: 基于IP防抖
     * user: 基于用户防抖
     * session: 基于会话防抖
     */
    @Schema(description = "防抖模式：global-全局, ip-基于IP, user-基于用户, session-基于会话")
    private String sysDebounceMode;

    /**
     * 防抖失败时的提示消息
     */
    @Schema(description = "防抖失败时的提示消息")
    private String sysDebounceMessage;

    /**
     * 降级方法名称
     */
    @Schema(description = "降级方法名称")
    private String fallbackMethod;

    /**
     * 降级返回值（JSON格式）
     */
    @Schema(description = "降级返回值（JSON格式）")
    private String fallbackValue;

    /**
     * 状态：0-禁用, 1-启用
     */
    @Schema(description = "状态：0-禁用, 1-启用")
    private Integer sysDebounceStatus;

    /**
     * 描述信息
     */
    @Schema(description = "描述信息")
    private String sysDebounceDescription;

    /**
     * 排序值，越小越优先
     */
    @Schema(description = "排序值，越小越优先")
    private Integer sysDebounceSort;

    /**
     * 创建人ID
     */
    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建人ID")
    private Long createBy;

    /**
     * 创建人姓名
     */
    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建人姓名")
    private String createName;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 更新人ID
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新人ID")
    private Long updateBy;

    /**
     * 更新人姓名
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新人姓名")
    private String updateName;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}
