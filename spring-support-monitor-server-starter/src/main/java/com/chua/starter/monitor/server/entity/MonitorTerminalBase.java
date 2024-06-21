package com.chua.starter.monitor.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
 * @since 2024/6/20 
 * @author CH
 */
/**
 * 终端基本信息
 */
@ApiModel(description="终端基本信息")
@Schema(description="终端基本信息")
@Data
@TableName(value = "monitor_terminal_base")
public class MonitorTerminalBase implements Serializable {
    @TableId(value = "base_id", type = IdType.AUTO)
    @ApiModelProperty(value="")
    @Schema(description="")
    @NotNull(message = "不能为null")
    private Integer baseId;

    /**
     * 名称
     */
    @TableField(value = "base_name")
    @ApiModelProperty(value="名称")
    @Schema(description="名称")
    @Size(max = 255,message = "名称最大长度要小于 255")
    private String baseName;

    /**
     * 终端ID
     */
    @TableField(value = "terminal_id")
    @ApiModelProperty(value="终端ID")
    @Schema(description="终端ID")
    private Integer terminalId;

    /**
     * 描述
     */
    @TableField(value = "base_desc")
    @ApiModelProperty(value="描述")
    @Schema(description="描述")
    @Size(max = 255,message = "描述最大长度要小于 255")
    private String baseDesc;

    /**
     * 值
     */
    @TableField(value = "base_value")
    @ApiModelProperty(value="值")
    @Schema(description="值")
    @Size(max = 255,message = "值最大长度要小于 255")
    private String baseValue;

    /**
     * 创建人姓名
     */
    @TableField(value = "create_name")
    @ApiModelProperty(value="创建人姓名")
    @Schema(description="创建人姓名")
    @Size(max = 255,message = "创建人姓名最大长度要小于 255")
    private String createName;

    /**
     * 创建人
     */
    @TableField(value = "create_by")
    @ApiModelProperty(value="创建人")
    @Schema(description="创建人")
    private Integer createBy;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    @ApiModelProperty(value="创建时间")
    @Schema(description="创建时间")
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time")
    @ApiModelProperty(value="更新时间")
    @Schema(description="更新时间")
    private Date updateTime;

    /**
     * 更新人姓名
     */
    @TableField(value = "update_name")
    @ApiModelProperty(value="更新人姓名")
    @Schema(description="更新人姓名")
    @Size(max = 255,message = "更新人姓名最大长度要小于 255")
    private String updateName;

    /**
     * 更新人
     */
    @TableField(value = "update_by")
    @ApiModelProperty(value="更新人")
    @Schema(description="更新人")
    private Integer updateBy;

    private static final long serialVersionUID = 1L;
}