package com.chua.starter.monitor.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.datasource.annotation.Column;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 脚本
 */
@ApiModel(description = "脚本")
@Schema(description = "脚本")
@Data
@TableName(value = "monitor_sys_gen_shell")
public class MonitorSysGenShell extends SysBase implements Serializable {
    @TableId(value = "shell_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer shellId;

    /**
     * 脚本名称
     */
    @TableField(value = "shell_name")
    @ApiModelProperty(value = "脚本名称")
    @Schema(description = "脚本名称")
    @NotNull(message = "脚本不能为null", groups = AddGroup.class)
    @Size(max = 255, message = "脚本名称最大长度要小于 255")
    private String shellName;

    /**
     * 脚本路径
     */
    @TableField(value = "shell_script_path")
    @ApiModelProperty(value = "脚本路径")
    @Schema(description = "脚本路径")
    @NotNull(message = "脚本路径不能为null", groups = AddGroup.class)
    @Size(max = 255, message = "脚本路径最大长度要小于 255")
    private String shellScriptPath;
    /**
     * 日志路径
     */
    @TableField(value = "shell_log_path")
    @ApiModelProperty(value = "日志路径")
    @Schema(description = "日志路径")
    @NotNull(message = "日志路径不能为null", groups = AddGroup.class)
    @Size(max = 255, message = "日志路径最大长度要小于 255")
    private String shellLogPath;

    /**
     * 脚本参数
     */
    @TableField(value = "shell_args")
    @ApiModelProperty(value = "脚本参数")
    @Schema(description = "脚本参数")
    @Size(max = 255, message = "脚本参数最大长度要小于 255")
    private String shellArgs;

    /**
     * 说明
     */
    @TableField(value = "shell_desc")
    @ApiModelProperty(value = "说明")
    @Schema(description = "说明")
    @Size(max = 255, message = "说明最大长度要小于 255")
    private String shellDesc;

    /**
     * 脚本内容
     */
    @TableField(value = "shell_content")
    @ApiModelProperty(value = "脚本内容")
    @Schema(description = "脚本内容")
    @Size(max = 255, message = "脚本内容最大长度要小于 255")
    private String shellContent;

    /**
     * gen表ID
     */
    @TableField(value = "gen_id")
    @ApiModelProperty(value = "gen表ID")
    @Schema(description = "gen表ID")
    @NotNull(message = "gen表ID不能为null", groups = AddGroup.class)
    private Integer genId;
    /**
     * 状态
     */
    @TableField(value = "shell_status")
    @ApiModelProperty(value = "状态;0: 待开启; 1:开启")
    @Schema(description = "状态;0: 待开启; 1:开启")
    @Column(defaultValue = "0")
    private Integer shellStatus;

    private static final long serialVersionUID = 1L;


    public String getOutName() {
        return shellLogPath;
    }
}