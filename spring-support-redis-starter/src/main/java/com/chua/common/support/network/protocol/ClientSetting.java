package com.chua.common.support.network.protocol;

import lombok.Builder;
import lombok.Data;

/**
 * Redis客户端配置类
 *
 * @author CH
 * @since 2024/12/25
 */
@Data
@Builder
public class ClientSetting {

    /**
     * 数据库索引
     */
    private String database;

    /**
     * 主机地址
     */
    private String host;

    /**
     * 端口号
     */
    private Integer port;

    /**
     * 密码
     */
    private String password;

    /**
     * 连接超时时间（毫秒）
     */
    private Long connectTimeoutMillis;
}

