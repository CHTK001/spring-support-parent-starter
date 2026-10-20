package com.chua.starter.device.support.realtime;

/**
 * 设备实时事件发布器
 *
 * <p>设备模块只依赖本接口，不直接依赖 SSE/WebSocket 实现：</p>
 * <ul>
 *   <li>未引入实时模块时，由 {@link DeviceRealtimeConfiguration} 装配空实现，模块可独立运行；</li>
 *   <li>引入 SSE 后，由业务方提供实现（或后续的 SseDeviceRealtimePublisher）覆盖默认 Bean。</li>
 * </ul>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface DeviceRealtimePublisher {

    /**
     * 发布事件
     *
     * @param event   事件名，例如 status / geo / event
     * @param payload 事件载荷（会被序列化为 JSON）
     */
    void publish(String event, Object payload);
}
