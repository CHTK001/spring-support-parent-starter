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

@ApiModel(description = "monitor_mybatis")
@Schema
@Data
@TableName(value = "monitor_mybatis")
public class MonitorMybatis extends SysBase implements Serializable {
    @TableId(value = "monitor_mybatis_id", type = IdType.AUTO)
    @ApiModelProperty(value = "")
    @Schema(description = "")
    @NotNull(message = "不能为null")
    private Integer monitorMybatisId;

    /**
     * 名称
     */
    @TableField(value = "monitor_mybatis_name")
    @ApiModelProperty(value = "名称")
    @Schema(description = "名称")
    @Size(max = 255, message = "名称最大长度要小于 255")
    private String monitorMybatisName;

    /**
     * 描述
     */
    @TableField(value = "monitor_mybatis_desc")
    @ApiModelProperty(value = "描述")
    @Schema(description = "描述")
    @Size(max = 255, message = "描述最大长度要小于 255")
    private String monitorMybatisDesc;

    /**
     * sql
     */
    @TableField(value = "monitor_mybatis_sql")
    @ApiModelProperty(value = "sql")
    @Schema(description = "sql")
    @Size(max = 255, message = "sql最大长度要小于 255")
    private String monitorMybatisSql;

    /**
     * sql类型, xml, sql
     */
    @TableField(value = "monitor_mybatis_sql_type")
    @ApiModelProperty(value = "sql类型, xml, sql")
    @Schema(description = "sql类型, xml, sql")
    @Size(max = 255, message = "sql类型, xml, sql最大长度要小于 255")
    private String monitorMybatisSqlType;

    /**
     * model
     */
    @TableField(value = "monitor_mybatis_model_type")
    @ApiModelProperty(value = "model")
    @Schema(description = "model")
    @Size(max = 255, message = "model最大长度要小于 255")
    private String monitorMybatisModelType;
    /**
     * 0: 未开启；
     */
    @TableField(value = "monitor_mybatis_status")
    @ApiModelProperty(value="0: 未开启；")
    @Schema(description="0: 未开启；")
    private Integer monitorMybatisStatus;
    /**
     * mapper
     */
    @TableField(value = "monitor_mybatis_mapper_type")
    @ApiModelProperty(value = "mapper")
    @Schema(description = "mapper")
    @Size(max = 255, message = "mapper最大长度要小于 255")
    private String monitorMybatisMapperType;

    /**
     * 环境
     */
    @TableField(value = "monitor_mybatis_profile")
    @ApiModelProperty(value = "环境")
    @Schema(description = "环境")
    @Size(max = 255, message = "环境最大长度要小于 255")
    private String monitorMybatisProfile;

    /**
     * 应用
     */
    @TableField(value = "monitor_appname")
    @ApiModelProperty(value = "应用")
    @Schema(description = "应用")
    @Size(max = 255, message = "应用最大长度要小于 255")
    private String monitorAppname;


    private static final long serialVersionUID = 1L;
}