package com.chua.starter.monitor.support.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
* 监控模块时序存储配置属性
*
* @author CH
* @since 4.0.0.42
 */
@Data
@ConfigurationProperties(prefix = "monitor.influx")
public class MonitorInfluxProperties {

    /**
    * 是否启用 influxdb 时序写入与查询
     */
    private boolean enabled = false;

    /**
    * influxdb 地址（v1 兼容端点，如 http://127.0.0.1:8086）
     */
    private String url = "http://127.0.0.1:8086";

    /**
    * 数据库名
     */
    private String database = "monitor";
}
