package com.chua.starter.config.server.support.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @author CH
 */
@Data
@TableName(value = "configuration_application_info")
public class ConfigurationApplicationInfo {
    @TableId(value = "app_id", type = IdType.AUTO)
    private Integer appId;

    /**
     * 注册的程序名称
     */
    @TableField(value = "app_name")
    private String appName;

    /**
     * 注册程序的主机
     */
    @TableField(value = "app_host")
    private String appHost;

    /**
     * 注册程序的端口
     */
    @TableField(value = "app_port")
    private Integer appPort;

    /**
     * 注册程序的环境
     */
    @TableField(value = "app_profile")
    private String appProfile;

    /**
     * spring端口
     */
    @TableField(value = "app_spring_port")
    private Integer appSpringPort;

    /**
     * 路径
     */
    @TableField(value = "app_context_path")
    private String appContextPath;

    /**
     * actuator路径
     */
    @TableField(value = "app_actuator")
    private String appActuator;
}