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

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 *
 * @since 2024/12/29
 * @author CH    
 */

/**
 * # 负载均衡\nupstream api_upstream\n{\nserver 127.0.0.1:8080 max_fails=3 weight=1;\nserver 127.0.0.1:8081 max_fails=3 weight=1;\n}
 */
@ApiModel(description = "# 负载均衡\nupstream api_upstream\n{\nserver 127.0.0.1:8080 max_fails=3 weight=1;\nserver 127.0.0.1:8081 max_fails=3 weight=1;\n}")
@Schema(description = "# 负载均衡\nupstream api_upstream\n{\nserver 127.0.0.1:8080 max_fails=3 weight=1;\nserver 127.0.0.1:8081 max_fails=3 weight=1;\n}")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "monitor_nginx_upstream")
public class MonitorNginxUpstream extends SysBase implements Serializable {
    @TableId(value = "monitor_nginx_upstream_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer monitorNginxUpstreamId;

    /**
     * 名称
     */
    @TableField(value = "monitor_nginx_upstream_name")
    @ApiModelProperty(value = "名称")
    @Schema(description = "名称")
    @Size(max = 255, message = "名称最大长度要小于 255")
    private String monitorNginxUpstreamName;

    /**
     * 服务
     */
    @TableField(value = "monitor_nginx_upstream_server")
    @ApiModelProperty(value = "服务")
    @Schema(description = "服务")
    @Size(max = 255, message = "服务最大长度要小于 255")
    private String monitorNginxUpstreamServer;

    /**
     * 失败次数
     */
    @TableField(value = "monitor_nginx_upstream_max_fails")
    @ApiModelProperty(value = "失败次数")
    @Schema(description = "失败次数")
    private Integer monitorNginxUpstreamMaxFails;

    /**
     * 权重
     */
    @TableField(value = "monitor_nginx_upstream_weight")
    @ApiModelProperty(value = "权重")
    @Schema(description = "权重")
    private Integer monitorNginxUpstreamWeight;

    /**
     * 0:http
     */
    @TableField(value = "monitor_nginx_upstream_type")
    @ApiModelProperty(value = "0:http")
    @Schema(description = "0:http")
    private Integer monitorNginxUpstreamType;

    /**
     * 模式；\nip_hash： IP 哈希策略根据客户端的 IP 地址进行哈希运算，将相同的请求分配给同一个后端服务器。这种策略适用于需要保持会话（Session）的场景，因为同一个客户端的请求会被发送到同一个服务器，从而避免了会话信息的丢失；\nleast_conn：最少连接策略将新的请求分配给当前连接数最少的后端服务器。这种策略可以确保每个后端服务器的负载相对均衡，避免某个服务器过载而其他服务器空闲的情况。
     */
    @TableField(value = "monitor_nginx_upstream_mode")
    @ApiModelProperty(value = "模式；\nip_hash： IP 哈希策略根据客户端的 IP 地址进行哈希运算，将相同的请求分配给同一个后端服务器。这种策略适用于需要保持会话（Session）的场景，因为同一个客户端的请求会被发送到同一个服务器，从而避免了会话信息的丢失；\nleast_conn：最少连接策略将新的请求分配给当前连接数最少的后端服务器。这种策略可以确保每个后端服务器的负载相对均衡，避免某个服务器过载而其他服务器空闲的情况。")
    @Schema(description = "模式；\nip_hash： IP 哈希策略根据客户端的 IP 地址进行哈希运算，将相同的请求分配给同一个后端服务器。这种策略适用于需要保持会话（Session）的场景，因为同一个客户端的请求会被发送到同一个服务器，从而避免了会话信息的丢失；\nleast_conn：最少连接策略将新的请求分配给当前连接数最少的后端服务器。这种策略可以确保每个后端服务器的负载相对均衡，避免某个服务器过载而其他服务器空闲的情况。")
    @Size(max = 255, message = "模式；\nip_hash： IP 哈希策略根据客户端的 IP 地址进行哈希运算，将相同的请求分配给同一个后端服务器。这种策略适用于需要保持会话（Session）的场景，因为同一个客户端的请求会被发送到同一个服务器，从而避免了会话信息的丢失；\nleast_conn：最少连接策略将新的请求分配给当前连接数最少的后端服务器。这种策略可以确保每个后端服务器的负载相对均衡，避免某个服务器过载而其他服务器空闲的情况。最大长度要小于 255")
    private String monitorNginxUpstreamMode;

    /**
     * udp; server 192.168.1.13:53 udp;
     */
    @TableField(value = "monitor_nginx_upstream_udp")
    @ApiModelProperty(value = "udp; server 192.168.1.13:53 udp; ")
    @Schema(description = "udp; server 192.168.1.13:53 udp; ")
    @Size(max = 255, message = "udp; server 192.168.1.13:53 udp; 最大长度要小于 255")
    private String monitorNginxUpstreamUdp;

    /**
     * 所属服务
     */
    @TableField(value = "monitor_nginx_server_id")
    @ApiModelProperty(value = "所属服务")
    @Schema(description = "所属服务")
    private Integer monitorNginxServerId;
}