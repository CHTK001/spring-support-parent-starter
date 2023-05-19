package com.chua.starter.config.server.entity;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 通知配置
 * @author CH
 * @since 2022/8/1 15:31
 */
@Data
@Accessors(chain = true)
public class NotifyConfig {
    /**
     * 地址
     */
    private String binderIp;
    /**
     * 端口
     */
    private String binderPort;
    /**
     * 配置
     */
    private String configItem;
    /**
     * 配置名称
     */
    private String configName;
    /**
     * 配置值
     */
    private String configValue;
}
