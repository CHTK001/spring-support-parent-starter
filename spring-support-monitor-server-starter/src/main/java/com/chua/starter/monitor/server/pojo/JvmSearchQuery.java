package com.chua.starter.monitor.server.pojo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 指标
 * @author CH
 * @since 2024/7/5
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class JvmSearchQuery extends IdQuery {
    /**
     * 指标类型，默认为"cpu-io"，用于标识指标的类别。
     */
    private String type;
    /**
     * 指标名称
     */
    private String appName;

    /**
     * 服务器主机
     */
    private String serverHost;

    /**
     * 服务器端口
     */
    private String serverPort;
    /**
     * keyword
     */
    private String keyword;

    /**
     * 数量
     */
    private int count = 1000;

    /**
     * 开始位置
     */
    private int offset = 0;
}
