package com.chua.starter.config.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 *    
 * @author CH
 */     
@Data
@TableName(value = "configuration_center_info")
public class ConfigurationCenterInfo {
    @TableId(value = "config_id", type = IdType.AUTO)
    private Integer configId;

    /**
     * 所属配置模块
     */
    @TableField(value = "config_application_name")
    private String configApplicationName;

    /**
     * 处理条件
     */
    @TableField(value = "config_condition")
    private String configCondition;

    /**
     * 配置项描述
     */
    @TableField(value = "config_desc")
    private String configDesc;

    /**
     * 配置所在配置名称
     */
    @TableField(value = "config_mapping_name")
    private String configMappingName;

    /**
     * 配置项名称
     */
    @TableField(value = "config_name")
    private String configName;

    /**
     * 配置环境
     */
    @TableField(value = "config_profile")
    private String configProfile;

    /**
     * 配置项值
     */
    @TableField(value = "config_value")
    private String configValue;

    /**
     * 是否禁用, 0: 开启
     */
    @TableField(value = "`disable`")
    private Integer disable;
}