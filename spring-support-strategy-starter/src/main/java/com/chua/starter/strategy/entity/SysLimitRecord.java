package com.chua.starter.strategy.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 限流记录实体
 * 记录限流策略触发的日志信息
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Data
@EqualsAndHashCode(callSuper = false)
@TableName("sys_limit_record")
@Schema(description = "限流记录")
public class SysLimitRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "sys_limit_record_id", type = IdType.AUTO)
    @Schema(description = "限流记录ID")
    private Long sysLimitRecordId;

    /**
     * 关联的限流配置ID
     */
    @TableField("sys_limit_configuration_id")
    @Schema(description = "限流配置ID")
    private Long sysLimitConfigurationId;

    /**
     * 限流规则名称
     */
    @TableField("sys_limit_name")
    @Schema(description = "限流规则名称")
    private String sysLimitName;

    /**
     * 接口路径
     */
    @TableField("sys_limit_path")
    @Schema(description = "触发限流的接口路径")
    private String sysLimitPath;

    /**
     * 限流维度
     */
    @TableField("sys_limit_dimension")
    @Schema(description = "限流维度")
    private String sysLimitDimension;

    /**
     * 限流键值
     */
    @TableField("sys_limit_key")
    @Schema(description = "限流键值（如IP地址、用户ID等）")
    private String sysLimitKey;

    /**
     * 用户ID
     */
    @TableField("sys_user_id")
    @Schema(description = "触发限流的用户ID")
    private Long sysUserId;

    /**
     * 用户名
     */
    @TableField("sys_user_name")
    @Schema(description = "触发限流的用户名")
    private String sysUserName;

    /**
     * 客户端IP
     */
    @TableField("client_ip")
    @Schema(description = "客户端IP地址")
    private String clientIp;

    /**
     * 请求方法
     */
    @TableField("request_method")
    @Schema(description = "HTTP请求方法")
    private String requestMethod;

    /**
     * 请求参数
     */
    @TableField("request_params")
    @Schema(description = "请求参数（JSON格式）")
    private String requestParams;

    /**
     * 用户代理
     */
    @TableField("user_agent")
    @Schema(description = "用户代理信息")
    private String userAgent;

    /**
     * 限流时间
     */
    @TableField("sys_limit_time")
    @Schema(description = "限流触发时间")
    private LocalDateTime sysLimitTime;

    /**
     * 创建时间
     */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @Schema(description = "创建时间")
    private LocalDateTime createTime;
}
