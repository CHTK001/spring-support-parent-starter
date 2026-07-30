package com.chua.starter.protocol.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 服务管理-过滤器处理日志
 * <p>
 * 表：system_server_log，列名与 system_server 系列一致带表语义前缀
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("服务管理过滤器处理日志")
@Schema(description = "服务管理过滤器处理日志")
@TableName(value = "system_server_log")
public class SystemServerLog extends SysBase {

    @TableId(value = "system_server_log_id", type = IdType.AUTO)
    @ApiModelProperty("主键ID")
    @Schema(description = "主键ID")
    @NotNull(message = "不能为null")
    private Long systemServerLogId;

    /**
     * 关联系统服务器ID（可选，用于按服务器筛选）
     */
    @TableField("system_server_log_server_id")
    @ApiModelProperty("系统服务器ID")
    @Schema(description = "系统服务器ID")
    private Integer serverId;

    /**
     * 过滤器类型（SPI类型标识或类名）
     */
    @TableField("system_server_log_filter_type")
    @ApiModelProperty("过滤器类型")
    @Schema(description = "过滤器类型（SPI类型或类名）")
    @Size(max = 100, message = "过滤器类型最大长度要小于 100")
    private String filterType;

    /**
     * 处理状态（如：BLACKLIST_BLOCK, WHITELIST_PASS, IP_LIMIT, OK 等）
     */
    @TableField("system_server_log_process_status")
    @ApiModelProperty("处理状态")
    @Schema(description = "处理状态（例如：IP限流、黑名单拦截、白名单通过等）")
    @Size(max = 100, message = "处理状态最大长度要小于 100")
    private String processStatus;

    /**
     * 通行/拒绝（PASS/DENY）
     */
    @TableField("system_server_log_action_status")
    @ApiModelProperty("通行/拒绝")
    @Schema(description = "通行/拒绝（PASS/DENY）")
    @Size(max = 16, message = "通行/拒绝最大长度要小于 16")
    private String actionStatus;

    /**
     * 客户端IP
     */
    @TableField("system_server_log_client_ip")
    @ApiModelProperty("客户端IP")
    @Schema(description = "客户端IP")
    @Size(max = 64, message = "IP最大长度要小于 64")
    private String clientIp;

    /**
     * 客户端地理位置信息（简要文本）
     */
    @TableField("system_server_log_client_geo")
    @ApiModelProperty("客户端地理位置信息")
    @Schema(description = "客户端地理位置信息")
    @Size(max = 255, message = "地理位置信息最大长度要小于 255")
    private String clientGeo;

    /**
     * User-Agent
     */
    @TableField("system_server_log_user_agent")
    @ApiModelProperty("User-Agent")
    @Schema(description = "User-Agent")
    @Size(max = 1000, message = "User-Agent最大长度要小于 1000")
    private String userAgent;

    /**
     * 请求ID/TraceId
     */
    @TableField("system_server_log_request_id")
    @ApiModelProperty("请求ID")
    @Schema(description = "请求ID/TraceId")
    @Size(max = 128, message = "请求ID最大长度要小于 128")
    private String requestId;

    /**
     * 请求方法/命令
     */
    @TableField("system_server_log_method")
    @ApiModelProperty("方法/命令")
    @Schema(description = "方法/命令")
    @Size(max = 32, message = "方法最大长度要小于 32")
    private String method;

    /**
     * 请求路径/资源
     */
    @TableField("system_server_log_path")
    @ApiModelProperty("路径/资源")
    @Schema(description = "路径/资源")
    @Size(max = 1000, message = "路径最大长度要小于 1000")
    private String path;

    /**
     * 受访域名/主机
     */
    @TableField(exist = false)
    @ApiModelProperty("受访域名/主机")
    @Schema(description = "受访域名/主机")
    @Size(max = 255, message = "域名最大长度要小于 255")
    private String host;

    /**
     * 来源页面/Referer
     */
    @TableField(exist = false)
    @ApiModelProperty("来源页面/Referer")
    @Schema(description = "来源页面/Referer")
    @Size(max = 2000, message = "Referer最大长度要小于 2000")
    private String referer;

    /**
     * 规则分类（用于前端 rail 精准分组）
     */
    @TableField("system_server_log_rule_category")
    @ApiModelProperty("规则分类")
    @Schema(description = "规则分类（RATE_LIMIT/BLACKLIST/WHITELIST/UNKNOWN）")
    @Size(max = 32, message = "规则分类最大长度要小于 32")
    private String ruleCategory;

    /**
     * 命中原因（人类可读）
     */
    @TableField("system_server_log_decision_reason")
    @ApiModelProperty("命中原因")
    @Schema(description = "命中原因（人类可读）")
    @Size(max = 2000, message = "命中原因最大长度要小于 2000")
    private String decisionReason;

    /**
     * 访问时间戳
     */
    @TableField("system_server_log_access_time")
    @ApiModelProperty("访问时间")
    @Schema(description = "访问时间")
    private LocalDateTime accessTime;

    /**
     * 处理时长（毫秒）
     */
    @TableField("system_server_log_duration_ms")
    @ApiModelProperty("处理时长(毫秒)")
    @Schema(description = "处理时长(毫秒)")
    private Long durationMs;

    /**
     * HTTP响应状态码
     */
    @TableField(exist = false)
    @ApiModelProperty("HTTP响应状态码")
    @Schema(description = "HTTP响应状态码")
    private Integer statusCode;

    /**
     * 存储时间戳
     */
    @TableField("system_server_log_store_time")
    @ApiModelProperty("存储时间")
    @Schema(description = "存储时间")
    private LocalDateTime storeTime;
}
