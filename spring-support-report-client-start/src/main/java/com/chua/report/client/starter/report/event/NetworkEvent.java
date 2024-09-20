package com.chua.report.client.starter.report.event;

import lombok.Data;

/**
 * 网络存储信息
 * @author CH
 * @since 2024/9/19
 */
@Data
public class NetworkEvent {

    /**
     * 网卡名称
     */
    private String name;
    /**
     * 显示名称
     */
    private String displayName;

    /**
     * 读取的字节数
     */
    private long readBytes;

    /**
     * 写入的字节数
     */
    private long writeBytes;

}
