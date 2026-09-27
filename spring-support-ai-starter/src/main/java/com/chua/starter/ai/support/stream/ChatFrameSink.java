package com.chua.starter.ai.support.stream;

import com.chua.common.support.ai.agent.AgentTrace;

import java.util.Map;

/**
 * 聊天帧输出槽（传输无关）。
 *
 * <p>同一份聊天编排（{@link ChatStreamOrchestrator}）通过本接口输出帧：
 * SSE 实现把帧写成裸 {@code data:} 字节，WebSocket 实现把帧作为 WS 文本消息发送。
 * 编排层不感知当前是哪种传输。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
public interface ChatFrameSink {

    /**
     * 发送一个普通事件帧（user_message / create / text / think / search / finish / turn_end）。
     *
     * @param event 事件结构，不允许为 null
     */
    void sendEvent(Map<String, Object> event);

    /**
     * 发送一个执行轨迹帧（trace）。
     *
     * @param trace 执行轨迹，不允许为 null
     */
    void sendTrace(AgentTrace trace);

    /**
     * 发送结束帧（[DONE]）。
     */
    void sendDone();

    /**
     * 当前轮次是否已被请求取消（facade 收到取消后停止产出）。
     *
     * @return 是否取消
     */
    boolean isCancelled();

    /**
     * 标记当前轮次取消（停止 facade 产出）。
     */
    void markCancelled();

    /**
     * 结束本轮传输：SSE 实现完成 emitter，WebSocket 实现为 no-op（共享长连接不能因一轮而关闭）。
     */
    void close();
}
