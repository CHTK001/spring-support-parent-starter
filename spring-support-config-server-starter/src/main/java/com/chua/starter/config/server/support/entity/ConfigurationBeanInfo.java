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
@TableName(value = "configuration_bean_info")
public class ConfigurationBeanInfo {
    @TableId(value = "bean_id", type = IdType.AUTO)
    private Integer beanId;

    /**
     * 所属应用
     */
    @TableField(value = "bean_application_name")
    private String beanApplicationName;

    /**
     * 文件目录
     */
    @TableField(value = "bean_file_path")
    private String beanFilePath;

    /**
     * 描述
     */
    @TableField(value = "bean_marker")
    private String beanMarker;

    /**
     * 配置环境
     */
    @TableField(value = "bean_profile")
    private String beanProfile;

    /**
     * 类型
     */
    @TableField(value = "bean_type_name")
    private String beanTypeName;

    /**
     * 是否禁用, 0: 开启
     */
    @TableField(value = "`disable`")
    private Integer disable;

    /**
     * 额外定义
     */
    @TableField(value = "bean_definition")
    private String beanDefinition;

    /**
     * beanName
     */
    @TableField(value = "bean_name")
    private String beanName;
}