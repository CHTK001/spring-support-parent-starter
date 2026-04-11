package com.chua.starter.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import com.chua.starter.mybatis.type.EncryptTypeHandler;
import com.chua.starter.server.support.model.ServerGuacamoleConfig;
import com.chua.starter.server.support.model.ServerMetricsSnapshot;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@ApiModel("服务器主机")
@TableName(value = "server_host", autoResultMap = true)
public class ServerHost extends SysBase {

    @TableId(value = "server_id", type = IdType.AUTO)
    @ApiModelProperty("服务器ID")
    private Integer serverId;

    @TableField("server_name")
    @ApiModelProperty("服务器名称")
    private String serverName;

    @TableField("server_code")
    @ApiModelProperty("服务器编码")
    private String serverCode;

    @TableField("server_type")
    @ApiModelProperty("接入类型")
    private String serverType;

    @TableField("server_os_type")
    @ApiModelProperty("操作系统")
    private String osType;

    @TableField("server_architecture")
    @ApiModelProperty("架构")
    private String architecture;

    @TableField("server_host")
    @ApiModelProperty("主机地址")
    private String host;

    @TableField("server_port")
    @ApiModelProperty("端口")
    private Integer port;

    @TableField("server_username")
    @ApiModelProperty("用户名")
    private String username;

    @TableField(value = "server_password", typeHandler = EncryptTypeHandler.class)
    @ApiModelProperty("密码")
    private String password;

    @TableField(value = "server_private_key", typeHandler = EncryptTypeHandler.class)
    @ApiModelProperty("私钥")
    private String privateKey;

    @TableField("server_base_directory")
    @ApiModelProperty("基础目录")
    private String baseDirectory;

    @TableField("server_tags")
    @ApiModelProperty("标签")
    private String tags;

    @TableField("server_enabled")
    @ApiModelProperty("是否启用")
    private Boolean enabled;

    @TableField("server_description")
    @ApiModelProperty("描述")
    private String description;

    @TableField("server_metadata_json")
    @ApiModelProperty("扩展元数据")
    private String metadataJson;

    @TableField(exist = false)
    @ApiModelProperty("标签列表")
    private List<String> tagsList;

    @TableField(exist = false)
    @ApiModelProperty("运行状态快照")
    private ServerMetricsSnapshot statusSnapshot;

    @TableField(exist = false)
    @ApiModelProperty("Guacamole 配置")
    private ServerGuacamoleConfig guacamoleConfig;

    @TableField(exist = false)
    @ApiModelProperty("远程控制配置")
    private ServerGuacamoleConfig remoteGatewayConfig;
}
