package com.chua.starter.sync.support.client;

import com.chua.starter.sync.support.spi.SyncMessageHandler;

import java.util.Map;

/**
* 同步客户端门面接口。
* <p>
* 在 {@link com.chua.common.support.network.sync.SyncClient} 基础上，
* 补充面向业务的批量主题订阅、SPI 处理器注册和 映射 数据发布能力，
* 供 {@code spring-support-report-client-starter} 等上层模块使用。
* </p>
*
* @since 2026/08/01
* @author CH
 */
public interface SyncClient extends com.chua.common.support.network.sync.SyncClient {

    /**
    * 批量订阅主题。
    *
    * @param topics 主题列表
    */
    void subscribe(String... topics);

    /**
    * 注册主题消息处理器（SPI 风格，携带 会话标识 与结构化数据）。
    *
    * @param topic   主题
    * @param handler 处理器
    */
    void registerHandler(String topic, SyncMessageHandler handler);

    /**
    * 发布结构化数据到指定主题。
    *
    * @param topic 主题
    * @param data  数据
    */
    void publish(String topic, Map<String, Object> data);
}
