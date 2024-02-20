package com.chua.starter.monitor.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

@ApiModel(description = "monitor_patch")
@Schema
@Data
@TableName(value = "monitor_patch")
public class MonitorPatch extends SysBase implements Serializable {
    @TableId(value = "monitor_patch_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer monitorPatchId;

    /**
     * 补丁应用
     */
    @TableField(value = "monitor_patch_app")
    @ApiModelProperty(value = "补丁所属应用")
    @Schema(description = "补丁所属应用")
    @Size(max = 255, message = "补丁名称最大长度要小于 255")
    private String monitorPatchApp;

    /**
     * 补丁名称
     */
    @TableField(value = "monitor_patch_name")
    @ApiModelProperty(value = "补丁名称")
    @Schema(description = "补丁名称")
    @Size(max = 255, message = "补丁名称最大长度要小于 255")
    private String monitorPatchName;

    /**
     * 中文名称
     */
    @TableField(value = "monitor_patch_chinese_name")
    @ApiModelProperty(value = "中文名称")
    @Schema(description = "中文名称")
    @Size(max = 255, message = "中文名称最大长度要小于 255")
    private String monitorPatchChineseName;

    /**
     * 补丁包路径
     */
    @TableField(value = "monitor_patch_pack")
    @ApiModelProperty(value = "补丁包路径")
    @Schema(description = "补丁包路径")
    @Size(max = 255, message = "补丁包路径最大长度要小于 255")
    private String monitorPatchPack;

    /**
     * 描述
     */
    @TableField(value = "monitor_patch_desc")
    @ApiModelProperty(value = "描述")
    @Schema(description = "描述")
    @Size(max = 255, message = "描述最大长度要小于 255")
    private String monitorPatchDesc;

    /**
     * 版本
     */
    @TableField(value = "monitor_patch_version")
    @ApiModelProperty(value = "版本")
    @Schema(description = "版本")
    @Size(max = 255, message = "版本最大长度要小于 255")
    private String monitorPatchVersion;


    @TableField(exist = false)
    private String patchFile;

    private static final long serialVersionUID = 1L;
    /**
     * 执行器名称
     */
    @TableField(exist = false)
    private List<Integer> executorIds;
}