package com.chua.report.server.starter.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.chua.common.support.datasource.annotation.ColumnDesc;
import com.chua.starter.mybatis.pojo.SysBase;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

@Data
@TableName(value = "monitor_sys_gen_template")
public class MonitorSysGenTemplate extends SysBase implements Serializable {
    @TableId(value = "template_id", type = IdType.AUTO)
    @NotNull(message = "不能为null")
    private Integer templateId;

    /**
     * 模板名称
     */
    @ColumnDesc("模板名称")
    @TableField(value = "template_name")
    @Size(max = 255, message = "模板名称最大长度要小于 255")
    private String templateName;

    @ColumnDesc("模板类型")
    @TableField("template_type")
    private String templateType;
    /**
     * 工具ID
     */
    @ColumnDesc("工具ID")
    @TableField(value = "gen_id")
    private Integer genId;

    /**
     * 目录
     */
    @ColumnDesc("目录")
    @TableField(value = "template_path")
    @Size(max = 255, message = "目录最大长度要小于 255")
    private String templatePath;


    /**
     * 模板
     */
    @ColumnDesc("模板")
    @TableField(value = "template_content")
    private String templateContent;

    private static final long serialVersionUID = 1L;
}