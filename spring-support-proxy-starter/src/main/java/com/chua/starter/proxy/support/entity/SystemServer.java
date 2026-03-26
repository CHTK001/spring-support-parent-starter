package com.chua.starter.proxy.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.base.validator.group.AddGroup;
import com.chua.common.support.base.validator.group.UpdateGroup;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 系统服务器配置实体类
 *
 * @author CH
 * @since 2025/01/07
 */
@EqualsAndHashCode(callSuper = true)
@ApiModel(description = "系统服务器配置")
@Schema(description = "系统服务器配置")
@Data
@TableName(value = "proxy_server")
public class SystemServer extends SysBase {

    /**
     * 系统服务器ID
     */
    @TableId(value = "proxy_server_id", type = IdType.AUTO)
    @ApiModelProperty(value = "系统服务器ID")
    @Schema(description = "系统服务器ID")
    @NotNull(message = "系统服务器ID不能为空", groups = {UpdateGroup.class})
    private Integer systemServerId;

    /**
     * 系统服务器名称
     */
    @TableField(value = "proxy_server_name")
    @ApiModelProperty(value = "系统服务器名称")
    @Schema(description = "系统服务器名称")
    @NotBlank(message = "系统服务器名称不能为空", groups = {AddGroup.class, UpdateGroup.class})
    @Size(max = 255, message = "系统服务器名称最大长度要小于 255")
    private String systemServerName;

    /**
     * 系统服务器主机
     */
    @TableField(value = "proxy_server_host")
    @ApiModelProperty(value = "系统服务器主机")
    @Schema(description = "系统服务器主机")
    private String systemServerHost;
    /**
     * 系统服务器端口
     */
    @TableField(value = "proxy_server_port")
    @ApiModelProperty(value = "系统服务器端口")
    @Schema(description = "系统服务器端口")
    @NotNull(message = "系统服务器端口不能为空", groups = {AddGroup.class, UpdateGroup.class})
    private Integer systemServerPort;

    /**
     * 系统服务器类型 (SPI使用)
     */
    @TableField(value = "proxy_server_type")
    @ApiModelProperty(value = "系统服务器类型")
    @Schema(description = "系统服务器类型")
    @NotBlank(message = "系统服务器类型不能为空", groups = {AddGroup.class, UpdateGroup.class})
    @Size(max = 100, message = "系统服务器类型最大长度要小于 100")
    private String systemServerType;
    /**
     * 系统服务器上下文路径
     */
    @TableField(value = "proxy_server_context_path")
    @ApiModelProperty(value = "系统服务器上下文路径")
    @Schema(description = "系统服务器上下文路径")
    private String systemServerContextPath;

    /**
     * 系统服务器启动状态
     */
    @TableField(value = "proxy_server_status")
    @ApiModelProperty(value = "系统服务器启动状态")
    @Schema(description = "系统服务器启动状态")
    private SystemServerStatus systemServerStatus;

    /**
     * 系统服务器描述
     */
    @TableField(value = "proxy_server_description")
    @ApiModelProperty(value = "系统服务器描述")
    @Schema(description = "系统服务器描述")
    @Size(max = 500, message = "系统服务器描述最大长度要小于 500")
    private String systemServerDescription;

    /**
     * 系统服务器配置 (JSON格式)
     */
    @TableField(value = "proxy_server_config")
    @ApiModelProperty(value = "系统服务器配置")
    @Schema(description = "系统服务器配置")
    private String systemServerConfig;

    /**
     * 系统服务器是否自动启动
     */
    @TableField(value = "proxy_server_auto_start")
    @ApiModelProperty(value = "系统服务器是否自动启动")
    @Schema(description = "系统服务器是否自动启动")
    private Boolean systemServerAutoStart;

    /**
     * 系统服务器最大连接数
     */
    @TableField(value = "proxy_server_max_connections")
    @ApiModelProperty(value = "系统服务器最大连接数")
    @Schema(description = "系统服务器最大连接数")
    private Integer systemServerMaxConnections;

    /**
     * 系统服务器超时时间(毫秒)
     */
    @TableField(value = "proxy_server_timeout")
    @ApiModelProperty(value = "系统服务器超时时间")
    @Schema(description = "系统服务器超时时间")
    private Integer systemServerTimeout;

    /**
     * 过滤器数量（非数据库字段，用于列表展示）
     */
    @TableField(exist = false)
    @ApiModelProperty(value = "过滤器数量")
    @Schema(description = "过滤器数量")
    private Integer filterCount;

    /**
     * 系统服务器启动状态枚举
     */
    @Getter
    public enum SystemServerStatus {
        STOPPED("STOPPED", "已停止"),
        RUNNING("RUNNING", "运行中"),
        STARTING("STARTING", "启动中"),
        STOPPING("STOPPING", "停止中"),
        ERROR("ERROR", "错误");

        private final String code;
        private final String description;

        SystemServerStatus(String code, String description) {
            this.code = code;
            this.description = description;
        }


    }
}




