package com.chua.starter.monitor.support.enums;

import lombok.Getter;

/**
 * 服务类型枚举
 * <p>
 * 定义监控模块支持的所有服务类型，对应任务表 {@code ms_monitor_task.service_type} 字段
 * 。Redis/数据库等中间件由相应的 提供者 实现采集与操作逻辑。
 * </p>
 *
 * @since 2026/8/4
 * @author CH
 */
@Getter
public enum ServiceType {

    /**
     * 服务器(物理机/虚拟机/JVM本地)
     */
    SERVER("SERVER", "服务器"),
    /**
     * Redis 实例
     */
    REDIS("REDIS", "Redis"),
    /**
     * MySQL 实例
     */
    MYSQL("MYSQL", "MySQL"),
    /**
     * PostgreSQL 实例
     */
    PG("PG", "PostgreSQL"),
    /**
     * Oracle 实例
     */
    ORACLE("ORACLE", "Oracle"),
    /**
     * SQL 服务端 实例
     * @param Server" 服务端"
     */
    MSSQL("MSSQL", "SQL Server"),
    /**
     * 消息队列(Kafka / RabbitMQ 等)
     */
    MQ("MQ", "消息队列");

    /**
     * 类型编码
     */
    private final String code;
    /**
     * 中文描述
     */
    private final String desc;

    /**
     * 构造方法
     *
     * @param code 类型编码
     * @param desc 描述
     */
    ServiceType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 通过编码获取枚举
     *
     * @param code 编码
     * @return 枚举值, 找不到时返回 {@link #SERVER}
     */
    public static ServiceType of(String code) {
        if (code == null) {
            return SERVER;
        }
        for (ServiceType value : values()) {
            if (value.code.equalsIgnoreCase(code)) {
                return value;
            }
        }
        return SERVER;
    }
}
