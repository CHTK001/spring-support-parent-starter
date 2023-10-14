package com.chua.starter.gen.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import lombok.Data;

@Data
@TableName(value = "sys_gen_template")
public class SysGenTemplate implements Serializable {
    @TableId(value = "template_id", type = IdType.AUTO)
    @NotNull(message = "不能为null")
    private Integer templateId;

    /**
     * 模板名称
     */
    @TableField(value = "template_name")
    @Size(max = 255, message = "模板名称最大长度要小于 255")
    private String templateName;

    /**
     * 工具ID
     */
    @TableField(value = "gen_id")
    private Integer genId;

    /**
     * 目录
     */
    @TableField(value = "template_path")
    @Size(max = 255, message = "目录最大长度要小于 255")
    private String templatePath;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    private Date createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time")
    private Date updateTime;

    /**
     * 模板
     */
    @TableField(value = "template_content")
    private String templateContent;

    private static final long serialVersionUID = 1L;
}