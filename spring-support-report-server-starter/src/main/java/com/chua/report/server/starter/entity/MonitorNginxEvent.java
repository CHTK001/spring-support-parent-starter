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
 * nginx事件
 */
@ApiModel(description = "nginx事件")
@Schema(description = "nginx事件")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "monitor_nginx_event")
public class MonitorNginxEvent extends SysBase implements Serializable {
    @TableId(value = "monitor_nginx_event_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer monitorNginxEventId;

    /**
     * Nginx 的工作模式及连接数上限 worker_connections  1024;
     */
    @TableField(value = "monitor_nginx_event_worker_connections")
    @ApiModelProperty(value = " Nginx 的工作模式及连接数上限 worker_connections  1024;")
    @Schema(description = " Nginx 的工作模式及连接数上限 worker_connections  1024;")
    private Integer monitorNginxEventWorkerConnections;

    /**
     * use epoll;指定使用哪种事件模型。Nginx 支持多种事件模型，如 epoll（Linux）、kqueue（BSD）、select 和 poll 等。通常，Nginx 会根据操作系统自动选择最佳的事件模型，但也可以手动指定
     */
    @TableField(value = "monitor_nginx_event_use")
    @ApiModelProperty(value = "use epoll;指定使用哪种事件模型。Nginx 支持多种事件模型，如 epoll（Linux）、kqueue（BSD）、select 和 poll 等。通常，Nginx 会根据操作系统自动选择最佳的事件模型，但也可以手动指定")
    @Schema(description = "use epoll;指定使用哪种事件模型。Nginx 支持多种事件模型，如 epoll（Linux）、kqueue（BSD）、select 和 poll 等。通常，Nginx 会根据操作系统自动选择最佳的事件模型，但也可以手动指定")
    @Size(max = 255, message = "use epoll;指定使用哪种事件模型。Nginx 支持多种事件模型，如 epoll（Linux）、kqueue（BSD）、select 和 poll 等。通常，Nginx 会根据操作系统自动选择最佳的事件模型，但也可以手动指定最大长度要小于 255")
    private String monitorNginxEventUse;

    /**
     * multi_accept on; 设置是否允许服务器在单个监听事件中接受多个连接。这可以减少 I/O 等待时间，提高性能。
     */
    @TableField(value = "monitor_nginx_event_multi_accept")
    @ApiModelProperty(value = "multi_accept on; 设置是否允许服务器在单个监听事件中接受多个连接。这可以减少 I/O 等待时间，提高性能。")
    @Schema(description = "multi_accept on; 设置是否允许服务器在单个监听事件中接受多个连接。这可以减少 I/O 等待时间，提高性能。")
    @Size(max = 255, message = "multi_accept on; 设置是否允许服务器在单个监听事件中接受多个连接。这可以减少 I/O 等待时间，提高性能。最大长度要小于 255")
    private String monitorNginxEventMultiAccept;

    /**
     * accept_mutex on;在某些情况下，可以设置为 on 来允许多个工作进程同时监听相同的端口。默认情况下，它是关闭的，以避免多个进程间的端口竞争。
     */
    @TableField(value = "monitor_nginx_event_accept_mutex")
    @ApiModelProperty(value = "accept_mutex on;在某些情况下，可以设置为 on 来允许多个工作进程同时监听相同的端口。默认情况下，它是关闭的，以避免多个进程间的端口竞争。")
    @Schema(description = "accept_mutex on;在某些情况下，可以设置为 on 来允许多个工作进程同时监听相同的端口。默认情况下，它是关闭的，以避免多个进程间的端口竞争。")
    @Size(max = 255, message = "accept_mutex on;在某些情况下，可以设置为 on 来允许多个工作进程同时监听相同的端口。默认情况下，它是关闭的，以避免多个进程间的端口竞争。最大长度要小于 255")
    private String monitorNginxEventAcceptMutex;

    /**
     * 所属配置
     */
    @TableField(value = "monitor_nginx_config_id")
    @ApiModelProperty(value = "所属配置")
    @Schema(description = "所属配置")
    private Integer monitorNginxConfigId;
}