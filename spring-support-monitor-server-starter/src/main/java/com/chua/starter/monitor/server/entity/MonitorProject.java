package com.chua.starter.monitor.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Data;

/**
 * 版本管理
 */
@ApiModel(description="版本管理")
@Schema(description="版本管理")
@Data
@JsonIgnoreProperties("projectControlPassword")
@TableName(value = "monitor_project")
public class MonitorProject implements Serializable {
    @TableId(value = "project_id", type = IdType.AUTO)
    @ApiModelProperty(value="")
    @Schema(description="")
    @NotNull(message = "不能为null")
    private Integer projectId;

    /**
     * 版本名称
     */
    @TableField(value = "project_name")
    @ApiModelProperty(value="版本名称")
    @Schema(description="版本名称")
    @Size(max = 255,message = "版本名称最大长度要小于 255")
    private String projectName;

    /**
     * 描述
     */
    @TableField(value = "project_desc")
    @ApiModelProperty(value="描述")
    @Schema(description="描述")
    @Size(max = 255,message = "描述最大长度要小于 255")
    private String projectDesc;

    /**
     * 账号
     */
    @TableField(value = "project_control_user")
    @ApiModelProperty(value="账号")
    @Schema(description="账号")
    @Size(max = 255,message = "账号最大长度要小于 255")
    private String projectControlUser;

    /**
     * 密码
     */
    @TableField(value = "project_control_password")
    @ApiModelProperty(value="密码")
    @Schema(description="密码")
    @Size(max = 255,message = "密码最大长度要小于 255")
    private String projectControlPassword;

    /**
     * 主机
     */
    @TableField(value = "project_control_host")
    @ApiModelProperty(value="主机")
    @Schema(description="主机")
    @Size(max = 255,message = "主机最大长度要小于 255")
    private String projectControlHost;

    /**
     * 端口
     */
    @TableField(value = "project_control_port")
    @ApiModelProperty(value="端口")
    @Schema(description="端口")
    @Size(max = 255,message = "端口最大长度要小于 255")
    private String projectControlPort;

    /**
     * 项目位置
     */
    @TableField(value = "project_project_path")
    @ApiModelProperty(value="项目位置")
    @Schema(description="项目位置")
    @Size(max = 255,message = "项目位置最大长度要小于 255")
    private String projectProjectPath;

    /**
     * uid
     */
    @TableField(value = "project_control_uid")
    @ApiModelProperty(value="uid")
    @Schema(description="uid")
    @Size(max = 255,message = "uid最大长度要小于 255")
    private String projectControlUid;

    private static final long serialVersionUID = 1L;
}