package com.chua.starter.monitor.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

@ApiModel(description = "monitor_limit")
@Schema
@Data
@TableName(value = "monitor_limit")
public class MonitorLimit extends SysBase implements Serializable {
    @TableId(value = "limit_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null", groups = UpdateGroup.class)
    private Integer limitId;

    /**
     * 限流名称
     */
    @TableField(value = "limit_name")
    @ApiModelProperty(value = "限流名称")
    @Schema(description = "限流名称")
    @Size(max = 255, message = "限流名称最大长度要小于 255")
    @NotNull(message = "限流名称不能为null", groups = AddGroup.class)
    private String limitName;

    /**
     * 限流地址
     */
    @TableField(value = "limit_mapping")
    @ApiModelProperty(value = "限流地址")
    @Schema(description = "限流地址")
    @Size(max = 255, message = "限流地址最大长度要小于 255")
    @NotNull(message = "限流地址不能为null", groups = AddGroup.class)
    private String limitMapping;

    /**
     * 每秒次数
     */
    @TableField(value = "limit_permits")
    @ApiModelProperty(value = "每秒次数")
    @Schema(description = "每秒次数")
    @Size(max = 255, message = "每秒次数最大长度要小于 255")
    @NotNull(message = "每秒次数不能为null", groups = AddGroup.class)
    private String limitPermits;

    /**
     * 是否开启; 0: 不开启
     */
    @TableField(value = "limit_status")
    @ApiModelProperty(value = "是否开启; 0: 不开启")
    @Schema(description = "是否开启; 0: 不开启")
    private Integer limitStatus;

    /**
     * 限流实现方式, guava
     */
    @TableField(value = "limit_type")
    @ApiModelProperty(value = "限流实现方式, guava")
    @Schema(description = "限流实现方式, guava")
    @Size(max = 255, message = "限流实现方式, guava最大长度要小于 255")
    @NotNull(message = "限流实现方式, guava不能为null", groups = AddGroup.class)
    private String limitType;

    /**
     * 限流模式,URL, REMOTE
     */
    @TableField(value = "limit_resolver")
    @ApiModelProperty(value = "限流模式,URL, REMOTE")
    @Schema(description = "限流模式,URL, REMOTE")
    @Size(max = 255, message = "限流模式,URL, REMOTE最大长度要小于 255")
    @NotNull(message = "限流模式只支持URL, REMOTE", groups = AddGroup.class)
    private String limitResolver;

    /**
     * 环境
     */
    @TableField(value = "limit_profile")
    @ApiModelProperty(value = "环境")
    @Schema(description = "环境")
    @Size(max = 255, message = "环境最大长度要小于 255")
    private String limitProfile;

    /**
     * 限流应用
     */
    @TableField(value = "limit_app")
    @ApiModelProperty(value = "限流应用")
    @Schema(description = "限流应用")
    @Size(max = 255, message = "限流应用最大长度要小于 255")
    @NotNull(message = "限流应用不能为null", groups = AddGroup.class)
    private String limitApp;

    /**
     * REMOTE模式下的黑名单
     */
    @TableField(value = "limit_remote_list")
    @ApiModelProperty(value = "REMOTE模式下的黑名单")
    @Schema(description = "REMOTE模式下的黑名单")
    @Size(max = 255, message = "REMOTE模式下的黑名单最大长度要小于 255")
    private String limitRemoteList;

    private static final long serialVersionUID = 1L;
}