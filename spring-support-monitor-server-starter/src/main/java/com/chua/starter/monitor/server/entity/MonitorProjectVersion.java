package com.chua.starter.monitor.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Data;

/**
 * 版本控制-子版本
 */
@ApiModel(description="版本控制-子版本")
@Schema(description="版本控制-子版本")
@Data
@TableName(value = "monitor_project_version")
public class MonitorProjectVersion implements Serializable {
    @TableId(value = "version_item_id", type = IdType.AUTO)
    @ApiModelProperty(value="")
    @Schema(description="")
    @NotNull(message = "不能为null")
    private Integer versionItemId;

    /**
     * 版本ID
     */
    @TableField(value = "project_id")
    @ApiModelProperty(value="版本ID")
    @Schema(description="版本ID")
    private Integer projectId;

    /**
     * 名称
     */
    @TableField(value = "version_item_name")
    @ApiModelProperty(value="名称")
    @Schema(description="名称")
    @Size(max = 255,message = "名称最大长度要小于 255")
    private String versionItemName;

    /**
     * 启动脚本
     */
    @TableField(value = "version_item_run_script")
    @ApiModelProperty(value="启动脚本")
    @Schema(description="启动脚本")
    @Size(max = 255,message = "启动脚本最大长度要小于 255")
    private String versionItemRunScript;

    /**
     * 停止脚本
     */
    @TableField(value = "version_item_stop_script")
    @ApiModelProperty(value="停止脚本")
    @Schema(description="停止脚本")
    @Size(max = 255,message = "停止脚本最大长度要小于 255")
    private String versionItemStopScript;

    /**
     * 日志名称
     */
    @TableField(value = "version_item_log")
    @ApiModelProperty(value="日志名称")
    @Schema(description="日志名称")
    @Size(max = 255,message = "日志名称最大长度要小于 255")
    private String versionItemLog;

    private static final long serialVersionUID = 1L;
}