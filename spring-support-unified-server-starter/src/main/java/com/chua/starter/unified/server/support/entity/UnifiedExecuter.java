package com.chua.starter.unified.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;

/**
 *    
 * @author CH
 */

/**
 * 执行器
 */
@Data
@TableName(value = "unified_executer")
public class UnifiedExecuter implements Serializable {
    @TableId(value = "unified_executer_id", type = IdType.AUTO)
    @NotNull(message = "不能为null")
    private Integer unifiedExecuterId;

    /**
     * 执行器名称
     */
    @TableField(value = "unified_executer_name")
    @Size(max = 255, message = "执行器名称最大长度要小于 255")
    private String unifiedExecuterName;

    /**
     * 执行器地址类型：0=自动注册、1=手动录入
     */
    @TableField(value = "unified_executer_type")
    @Size(max = 255, message = "执行器地址类型：0=自动注册、1=手动录入最大长度要小于 255")
    private String unifiedExecuterType;

    /**
     * 执行器应用名称
     */
    @TableField(value = "unified_appname")
    @Size(max = 255, message = "执行器应用名称最大长度要小于 255")
    private String unifiedAppname;

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

    private static final long serialVersionUID = 1L;
}