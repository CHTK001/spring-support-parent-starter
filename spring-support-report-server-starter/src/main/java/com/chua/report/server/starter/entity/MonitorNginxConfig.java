package com.chua.report.server.starter.entity;

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

import java.io.Serializable;

/**
 *
 * @since 2024/12/29
 * @author CH    
 */

/**
 * nginx配置项
 */
@ApiModel(description = "nginx配置项")
@Schema(description = "nginx配置项")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "monitor_nginx_config")
public class MonitorNginxConfig extends SysBase implements Serializable {
    @TableId(value = "monitor_nginx_config_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer monitorNginxConfigId;

    /**
     * 配置名称
     */
    @TableField(value = "monitor_nginx_config_name")
    @ApiModelProperty(value = "配置名称")
    @Schema(description = "配置名称")
    @Size(max = 255, message = "配置名称最大长度要小于 255")
    private String monitorNginxConfigName;

    /**
     * 配置类型
     */
    @TableField(value = "monitor_nginx_config_type")
    @ApiModelProperty(value = "配置类型; 0:文件; 1:服务")
    private Integer monitorNginxConfigType = 0;

    /**
     * 服务名称
     */
    @TableField(value = "monitor_nginx_config_service_name")
    @ApiModelProperty(value = "服务名称")
    @Schema(description = "服务名称")
    private String monitorNginxConfigServiceName;

    /**
     * 配置文件路径
     */
    @TableField(value = "monitor_nginx_config_path")
    @ApiModelProperty(value = "配置文件路径")
    @Schema(description = "配置文件路径")
    @Size(max = 255, message = "配置文件路径最大长度要小于 255")
    private String monitorNginxConfigPath;

    /**
     * #允许进程数量，\n建议设置为cpu核心数或者auto自动检测，注意Windows服务器上虽然可以启动多个processes，但是实际只会用其中一个worker_processes  auto;
     */
    @TableField(value = "monitor_nginx_config_worker_processes")
    @ApiModelProperty(value = "#允许进程数量，\n建议设置为cpu核心数或者auto自动检测，注意Windows服务器上虽然可以启动多个processes，但是实际只会用其中一个worker_processes  auto;")
    @Schema(description = "#允许进程数量，\n建议设置为cpu核心数或者auto自动检测，注意Windows服务器上虽然可以启动多个processes，但是实际只会用其中一个worker_processes  auto;")
    @Size(max = 255, message = "#允许进程数量，\n建议设置为cpu核心数或者auto自动检测，注意Windows服务器上虽然可以启动多个processes，但是实际只会用其中一个worker_processes  auto;最大长度要小于 255")
    private String monitorNginxConfigWorkerProcesses;

    /**
     * #error日志存放位置\n error_log  logs/error.log; \n#error_log  logs/error.log  notice; \n#error_log  logs/error.log  info;
     */
    @TableField(value = "monitor_nginx_config_error_log")
    @ApiModelProperty(value = "#error日志存放位置\n error_log  logs/error.log; \n#error_log  logs/error.log  notice; \n#error_log  logs/error.log  info;")
    @Schema(description = "#error日志存放位置\n error_log  logs/error.log; \n#error_log  logs/error.log  notice; \n#error_log  logs/error.log  info;")
    @Size(max = 255, message = "#error日志存放位置\n error_log  logs/error.log; \n#error_log  logs/error.log  notice; \n#error_log  logs/error.log  info;最大长度要小于 255")
    private String monitorNginxConfigErrorLog;

    /**
     * #pid        logs/nginx.pid;#工作模式及连接数上限
     */
    @TableField(value = "monitor_nginx_config_pid")
    @ApiModelProperty(value = "#pid        logs/nginx.pid;#工作模式及连接数上限")
    @Schema(description = "#pid        logs/nginx.pid;#工作模式及连接数上限")
    @Size(max = 255, message = "#pid        logs/nginx.pid;#工作模式及连接数上限最大长度要小于 255")
    private String monitorNginxConfigPid;

    /**
     * 是否多文件存储
     */
    @TableField(value = "monitor_nginx_config_multipart")
    @ApiModelProperty(value = "是否多文件存储")
    @Schema(description = "是否多文件存储")
    private Integer monitorNginxConfigMultipart;

    /**
     * nginx脚本路径；服务方式无需配置
     */
    @TableField(value = "monitor_nginx_config_nginx_path")
    @ApiModelProperty(value = "nginx脚本路径；服务方式无需配置")
    @Schema(description = "nginx脚本路径；服务方式无需配置")
    @Size(max = 255, message = "nginx脚本路径；服务方式无需配置最大长度要小于 255")
    private String monitorNginxConfigNginxPath;


    /**
     * 是否运行中
     */
    @TableField(exist = false)
    @ApiModelProperty(value = "是否运行中")
    private Boolean running;
    /**
     * 事件
     */
    @TableField(exist = false)
    @ApiModelProperty(value = "事件")
    @Schema(description = "事件")
    private MonitorNginxEvent events;


}