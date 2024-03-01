package com.chua.starter.monitor.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.constant.Position;
import com.chua.common.support.datasource.annotation.Column;
import com.chua.common.support.validator.group.AddGroup;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.hibernate.annotations.ColumnDefault;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 版本控制-子版本
 */
@ApiModel(description = "版本控制-子版本")
@Schema(description = "版本控制-子版本")
@Data
@TableName(value = "monitor_project_version")
public class MonitorProjectVersion implements Serializable {
    @TableId(value = "version_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer versionId;

    /**
     * 版本ID
     */
    @TableField(value = "project_id")
    @ApiModelProperty(value = "版本ID")
    @Schema(description = "版本ID")
    @NotNull(message = "项目信息不能为空", groups = AddGroup.class)
    private Integer projectId;

    /**
     * 名称
     */
    @TableField(value = "version_name")
    @ApiModelProperty(value = "名称")
    @Schema(description = "名称")
    @Size(max = 255, message = "名称最大长度要小于 255")
    private String versionName;

    /**
     * 启动脚本名称
     */
    @TableField(value = "version_run_script")
    @ApiModelProperty(value = "启动脚本名称")
    @Schema(description = "启动脚本名称")
    @Size(max = 255, message = "启动脚本名称最大长度要小于 255")
    private String versionRunScript;

    /**
     * 停止脚本名称
     */
    @TableField(value = "version_stop_script")
    @ApiModelProperty(value = "停止脚本名称")
    @Schema(description = "停止脚本名称")
    @Size(max = 255, message = "停止脚本名称最大长度要小于 255")
    private String versionStopScript;

    /**
     * 日志名称
     */
    @TableField(value = "version_log")
    @ApiModelProperty(value = "日志名称")
    @Schema(description = "日志名称")
    @Size(max = 255, message = "日志名称最大长度要小于 255")
    private String versionLog;

    /**
     * 版本说明
     */
    @TableField(value = "version_desc")
    @ApiModelProperty(value = "版本说明")
    @Schema(description = "版本说明")
    @Size(max = 255, message = "版本说明最大长度要小于 255")
    private String versionDesc;

    /**
     * 版本编号
     */
    @TableField(value = "version_code")
    @ApiModelProperty(value = "版本编号(系统生成)")
    @Schema(description = "版本编号(系统生成)")
    private String versionCode;

    /**
     * 版本状态
     */
    @TableField(value = "version_status")
    @ApiModelProperty(value = "版本状态;0: 未启动")
    @Schema(description = "版本状态;0: 未启动")
    @ColumnDefault("0")
    @Column(defaultValue = "0")
    private Integer versionStatus;
    /**
     * 版本状态
     */
    @TableField(value = "version_log_path_position")
    @ApiModelProperty(value = "版本状态;0: 未启动")
    @Schema(description = "版本状态;0: 未启动")
    @ColumnDefault("0")
    @Column(defaultValue = "0")
    private Position versionLogPathPosition;

    private static final long serialVersionUID = 1L;
}