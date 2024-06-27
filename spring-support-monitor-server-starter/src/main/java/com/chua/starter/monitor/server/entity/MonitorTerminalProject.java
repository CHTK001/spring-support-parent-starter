package com.chua.starter.monitor.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.validator.group.AddGroup;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;

/**
 *
 *
 * @since 2024/6/19 
 * @author CH
 */
/**
 * 项目管理
 */
@ApiModel(description="项目管理")
@Schema(description="项目管理")
@Data
@TableName(value = "monitor_terminal_project")
public class MonitorTerminalProject implements Serializable {
    @TableId(value = "terminal_project_id", type = IdType.AUTO)
    @ApiModelProperty(value="")
    @Schema(description="")
    @NotNull(message = "不能为null")
    private Integer terminalProjectId;

    /**
     * 项目名称
     */
    @TableField(value = "terminal_id")
    @ApiModelProperty(value="終端ID")
    @Schema(description="終端ID")
    @NotNull(message = "终端不能为null", groups = {AddGroup.class})
    private Integer terminalId;
    /**
     * 项目名称
     */
    @TableField(value = "terminal_project_name")
    @ApiModelProperty(value="项目名称")
    @Schema(description="项目名称")
    @Size(max = 255,message = "项目名称最大长度要小于 255")
    private String terminalProjectName;

    /**
     * 项目描述
     */
    @TableField(value = "terminal_project_desc")
    @ApiModelProperty(value="项目描述")
    @Schema(description="项目描述")
    @Size(max = 255,message = "项目描述最大长度要小于 255")
    private String terminalProjectDesc;

    /**
     * 项目地址
     */
    @TableField(value = "terminal_project_path")
    @ApiModelProperty(value="项目地址")
    @Schema(description="项目地址")
    @Size(max = 255,message = "项目地址最大长度要小于 255")
    private String terminalProjectPath;

    /**
     * 项目启动脚本
     */
    @TableField(value = "terminal_project_start_script")
    @ApiModelProperty(value="项目启动脚本")
    @Schema(description="项目启动脚本")
    @Size(max = 255,message = "项目启动脚本最大长度要小于 255")
    private String terminalProjectStartScript;

    /**
     * 项目停止脚本
     */
    @TableField(value = "terminal_project_end_script")
    @ApiModelProperty(value="项目停止脚本")
    @Schema(description="项目停止脚本")
    @Size(max = 255,message = "项目停止脚本最大长度要小于 255")
    private String terminalProjectEndScript;

    /**
     * 日志文件地址
     */
    @TableField(value = "terminal_project_log")
    @ApiModelProperty(value="日志文件地址")
    @Schema(description="日志文件地址")
    @Size(max = 255,message = "日志文件地址最大长度要小于 255")
    private String terminalProjectLog;

    /**
     * 日志文件地址
     */
    @TableField(value = "terminal_project_status")
    @ApiModelProperty(value="是否开启;0：关闭")
    @Schema(description = "是否开启;0：关闭")
    private Integer terminalProjectStatus;

    /**
     * 创建地址
     */
    @TableField(value = "create_time")
    @ApiModelProperty(value="创建地址")
    @Schema(description="创建地址")
    private Date createTime;

    /**
     * 更新地址
     */
    @TableField(value = "update_time")
    @ApiModelProperty(value="更新地址")
    @Schema(description="更新地址")
    private Date updateTime;

    private static final long serialVersionUID = 1L;
}