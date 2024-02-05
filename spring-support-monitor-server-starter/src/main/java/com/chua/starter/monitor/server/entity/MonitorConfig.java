package com.chua.starter.monitor.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.annotations.Group;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 配置参数
 */
@ApiModel(description="配置参数")
@Schema(description="配置参数")
@Data
@TableName(value = "monitor_config")
public class MonitorConfig extends SysBase implements Serializable {
    @TableId(value = "config_id", type = IdType.AUTO)
    @ApiModelProperty(value="")
    @Schema(description="")
    @NotNull(message = "不能为null", groups = Group.class)
    private Integer configId;

    /**
     * 名称
     */
    @TableField(value = "config_name")
    @ApiModelProperty(value="名称")
    @Schema(description="名称")
    @Size(max = 255,message = "名称最大长度要小于 255")
    private String configName;

    /**
     * 值
     */
    @TableField(value = "config_value")
    @ApiModelProperty(value="值")
    @Schema(description="值")
    @Size(max = 255,message = "值最大长度要小于 255")
    private String configValue;

    /**
     * 环境
     */
    @TableField(value = "config_desc")
    @ApiModelProperty(value="描述")
    @Schema(description="描述")
    @Size(max = 255,message = "环境最大长度要小于 255")
    private String configDesc;

    /**
     * 环境
     */
    @TableField(value = "config_profile")
    @ApiModelProperty(value="环境")
    @Schema(description="环境")
    @Size(max = 255,message = "环境最大长度要小于 255")
    private String configProfile;

    /**
     * 所属应用
     */
    @TableField(value = "config_appname")
    @ApiModelProperty(value="所属应用")
    @Schema(description="所属应用")
    @Size(max = 255,message = "所属应用最大长度要小于 255")
    private String configAppname;

    /**
     * 0: 未开启；
     */
    @TableField(value = "config_status")
    @ApiModelProperty(value="0: 未开启；")
    @Schema(description="0: 未开启；")
    private Integer configStatus;

    private static final long serialVersionUID = 1L;
}