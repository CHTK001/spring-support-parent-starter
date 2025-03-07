package com.chua.report.server.starter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 守护进程
 */
@ApiModel(description = "守护进程")
@Schema(description = "守护进程")
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "monitor_protection")
public class MonitorProtection extends SysBase {
    @TableId(value = "monitor_protection_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    private Integer monitorProtectionId;

    /**
     * 名称
     */
    @TableField(value = "monitor_protection_name")
    @ApiModelProperty(value = "名称")
    @Schema(description = "名称")
    private String monitorProtectionName;

    /**
     * 监听的PID
     */
    @TableField(value = "monitor_protection_pid")
    @ApiModelProperty(value = "监听的PID")
    @Schema(description = "监听的PID")
    private Integer monitorProtectionPid;

    /**
     * 是否禁用; 0:启用
     */
    @TableField(value = "monitor_protection_status")
    @ApiModelProperty(value = "是否禁用; 0:启用")
    @Schema(description = "是否禁用; 0:启用")
    private Integer monitorProtectionStatus;

    /**
     * 备注
     */
    @TableField(value = "monitor_protection_remark")
    @ApiModelProperty(value = "备注")
    @Schema(description = "备注")
    private String monitorProtectionRemark;

    /**
     * 启动脚本
     */
    @TableField(value = "monitor_protection_shell")
    @ApiModelProperty(value = "启动脚本")
    @Schema(description = "启动脚本")
    private String monitorProtectionShell;
}