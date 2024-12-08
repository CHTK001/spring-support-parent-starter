package com.chua.report.server.starter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.discovery.Discovery;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import javax.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@ApiModel(description="monitor_application")
@Schema
@Data
@TableName(value = "monitor_application")
public class MonitorApplication extends SysBase implements Serializable {
    @TableId(value = "monitor_id", type = IdType.AUTO)
    @ApiModelProperty(value="")
    @Schema(description="")
    @NotNull(message = "不能为null")
    private Integer monitorId;

    /**
     * 监控名称
     */
    @TableField(value = "monitor_name")
    @ApiModelProperty(value="监控名称")
    @Schema(description="监控名称")
    @Size(max = 255,message = "监控名称最大长度要小于 255")
    private String monitorName;

    /**
     * 所属应用
     */
    @TableField(value = "monitor_application_name")
    @ApiModelProperty(value="所属应用")
    @Schema(description="所属应用")
    @Size(max = 255,message = "所属应用最大长度要小于 255")
    private String monitorApplicationName;

    @TableField(exist = false)
    @ApiModelProperty(value="绑定的应用")
    @Schema(description="绑定的应用")
    private List<Discovery> monitorRequests;

    private static final long serialVersionUID = 1L;
}