package com.chua.starter.monitor.server.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.starter.monitor.request.MonitorRequest;
import com.chua.starter.mybatis.pojo.SysBase;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

@ApiModel(description="monitor_app")
@Schema
@Data
@TableName(value = "monitor_app")
public class MonitorApp extends SysBase implements Serializable {
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
    @TableField(value = "monitor_appname")
    @ApiModelProperty(value="所属应用")
    @Schema(description="所属应用")
    @Size(max = 255,message = "所属应用最大长度要小于 255")
    private String monitorAppname;

    @TableField(exist = false)
    @ApiModelProperty(value="绑定的应用")
    @Schema(description="绑定的应用")
    private List<MonitorRequest> monitorRequests;

    private static final long serialVersionUID = 1L;
}