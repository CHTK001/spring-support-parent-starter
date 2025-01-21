package com.chua.report.server.starter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;

/**
 *
 * @since 2024/12/29
 * @author CH    
 */

/**
 * nginx消息头
 */
@ApiModel(description = "nginx消息头")
@Schema(description = "nginx消息头")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "monitor_nginx_http_server_location_header")
public class MonitorNginxHttpServerLocationHeader extends SysBase implements Serializable {
    @TableId(value = "monitor_nginx_http_server_location_header_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer monitorNginxHttpServerLocationHeaderId;

    /**
     * monitor_nginx_http_server_location表ID
     */
    @TableField(value = "monitor_nginx_http_server_location_id")
    @ApiModelProperty(value = "monitor_nginx_http_server_location表ID")
    @Schema(description = "monitor_nginx_http_server_location表ID")
    private Integer monitorNginxHttpServerLocationId;

    /**
     * 名称
     */
    @TableField(value = "monitor_nginx_http_server_location_header_name")
    @ApiModelProperty(value = "名称")
    @Schema(description = "名称")
    @Size(max = 255, message = "名称最大长度要小于 255")
    private String monitorNginxHttpServerLocationHeaderName;

    /**
     * 值
     */
    @TableField(value = "monitor_nginx_http_server_location_header_value")
    @ApiModelProperty(value = "值")
    @Schema(description = "值")
    @Size(max = 255, message = "值最大长度要小于 255")
    private String monitorNginxHttpServerLocationHeaderValue;

    /**
     * 类型; add, set
     */
    @TableField(value = "monitor_nginx_http_server_location_header_type")
    @ApiModelProperty(value = "类型; add, set")
    @Schema(description = "类型; add, set")
    @Size(max = 255, message = "类型; add, set最大长度要小于 255")
    private String monitorNginxHttpServerLocationHeaderType;
}