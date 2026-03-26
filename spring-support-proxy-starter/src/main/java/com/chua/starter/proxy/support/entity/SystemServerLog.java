package com.chua.starter.proxy.support.entity;

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
 * 对应数据表：systemserverLog（按需求指定的表名）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("服务管理过滤器处理日志")
@Schema(description = "服务管理过滤器处理日志")
@TableName(value = "proxy_server_log")
public class SystemServerLog extends SysBase {

    @TableId(value = "id", type = IdType.AUTO)
    @ApiModelProperty("主键ID")
    @Schema(description = "主键ID")
    @NotNull(message = "不能为null")
    private Long id;

    /**
     * 关联系统服务器ID（可选，用于按服务器筛选）
     */
    @TableField("server_id")
    @ApiModelProperty("系统服务器ID")
    @Schema(description = "系统服务器ID")
    private Integer serverId;

    /**
     * 过滤器类型（SPI类型标识或类名）
     */
    @TableField("filter_type")
    @ApiModelProperty("过滤器类型")
    @Schema(description = "过滤器类型（SPI类型或类名）")
    @Size(max = 100, message = "过滤器类型最大长度要小于 100")
    private String filterType;

    /**
     * 处理状态（如：BLACKLIST_BLOCK, WHITELIST_PASS, IP_LIMIT, OK 等）
     */
    @TableField("process_status")
    @ApiModelProperty("处理状态")
    @Schema(description = "处理状态（例如：IP限流、黑名单拦截、白名单通过等）")
    @Size(max = 100, message = "处理状态最大长度要小于 100")
    private String processStatus;

    /**
     * 客户端IP
     */
    @TableField("client_ip")
    @ApiModelProperty("客户端IP")
    @Schema(description = "客户端IP")
    @Size(max = 64, message = "IP最大长度要小于 64")
    private String clientIp;

    /**
     * 客户端地理位置信息（简要文本）
     */
    @TableField("client_geo")
    @ApiModelProperty("客户端地理位置信息")
    @Schema(description = "客户端地理位置信息")
    @Size(max = 255, message = "地理位置信息最大长度要小于 255")
    private String clientGeo;

    /**
     * 访问时间戳
     */
    @TableField("access_time")
    @ApiModelProperty("访问时间")
    @Schema(description = "访问时间")
    private LocalDateTime accessTime;

    /**
     * 处理时长（毫秒）
     */
    @TableField("duration_ms")
    @ApiModelProperty("处理时长(毫秒)")
    @Schema(description = "处理时长(毫秒)")
    private Long durationMs;

    /**
     * 存储时间戳
     */
    @TableField("store_time")
    @ApiModelProperty("存储时间")
    @Schema(description = "存储时间")
    private LocalDateTime storeTime;
}





