package com.chua.sync.support.spi;

import java.util.Map;

/**
 * 同步消息处理器 SPI 接口
 * <p>
 * 用于处理特定主题的消息，通过 SPI 机制加载实现类
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/04
 */
public interface SyncMessageHandler {

    /**
     * 获取处理器名称
     * <p>
     * 用于匹配配置中的 topics 映射
     * </p>
     *
     * @return 处理器名称
     */
    String getName();

    /**
     * 获取处理器优先级
     * <p>
     * 数值越小优先级越高，默认为 0
     * </p>
     *
     * @return 优先级
     */
    default int getOrder() {
        return 0;
    }

    /**
     * 处理消息
     *
     * @param topic     主题
     * @param sessionId 会话ID
     * @param data      消息数据
     * @return 处理结果，可为 null
     */
    Object handle(String topic, String sessionId, Map<String, Object> data);

    /**
     * 是否支持该主题
     *
     * @param topic 主题
     * @return 是否支持
     */
    default boolean supports(String topic) {
        return true;
    }
}
