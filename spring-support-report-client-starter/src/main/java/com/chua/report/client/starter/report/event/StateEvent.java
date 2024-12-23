package com.chua.report.client.starter.report.event;


import lombok.Data;

import java.util.List;

/**
 * 进程事件类，用于描述进程的相关信息和状态
 *
 * @author CH
 * @since 2024/9/21
 */
@Data
public class StateEvent {

    /**
     * 本地地址
     */
    private String localAddress;

    /**
     * 外部地址
     */
    private String foreignAddress;

    /**
     * 连接状态
     */
    private String state;
}
