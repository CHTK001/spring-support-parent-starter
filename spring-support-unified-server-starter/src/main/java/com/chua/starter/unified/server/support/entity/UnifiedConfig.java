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
 * @author CH
 */
@Data
@TableName(value = "unified_config")
public class UnifiedConfig implements Serializable {
    @TableId(value = "unified_config_id", type = IdType.AUTO)
    @NotNull(message = "不能为null")
    private Integer unifiedConfigId;

    /**
     * 配置名称
     */
    @TableField(value = "unified_config_name")
    @Size(max = 255, message = "配置名称最大长度要小于 255")
    private String unifiedConfigName;

    /**
     * 配置值
     */
    @TableField(value = "unified_config_value")
    @Size(max = 255, message = "配置值最大长度要小于 255")
    private String unifiedConfigValue;

    /**
     * 描述
     */
    @TableField(value = "unified_config_desc")
    @Size(max = 255, message = "描述最大长度要小于 255")
    private String unifiedConfigDesc;

    /**
     * 配置环境
     */
    @TableField(value = "unified_config_profile")
    @Size(max = 255, message = "配置环境最大长度要小于 255")
    private String unifiedConfigProfile;

    /**
     * 应用名称
     */
    @TableField(value = "unified_appname")
    @Size(max = 255, message = "应用名称最大长度要小于 255")
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