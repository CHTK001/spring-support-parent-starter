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
@TableName(value = "configuration_subscribe_info")
public class ConfigurationSubscribeInfo {
    @TableId(value = "subscribe_id", type = IdType.AUTO)
    private Integer subscribeId;

    /**
     * 订阅的应用
     */
    @TableField(value = "subscribe_application_name")
    private String subscribeApplicationName;

    /**
     * 订阅环境
     */
    @TableField(value = "subscribe_profile")
    private String subscribeProfile;

    /**
     * 订阅类型, config, bean
     */
    @TableField(value = "subscribe_type")
    private String subscribeType;
}