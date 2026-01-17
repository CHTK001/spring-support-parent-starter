package com.chua.starter.sse.support;

import jakarta.servlet.http.HttpServletResponse;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;

/**
 * 定义了一种发布者对象，用于SSE (Server-Sent Events) 功能
 * 该类使用了 Lombok 注解来简化代码
 */
@Data
@Builder
public class Emitter {

    /**
     * 发布者的唯一标识符
     */
    private String clientId;

    /**
     * 事件类型集合，一个发布者可以支持多种事件类型
     */
    @Singular("event")
    private Set<String> event;

    /**
     * 实体对象，具体事件的数据内容
     */
    private Object entity;

    /**
     * HTTP 响应对象，用于向客户端发送 SSE 事件
     */
    private HttpServletResponse response;

    /**
     * 创建时间，初始化为当前的纳秒数
     */
    @Builder.Default
    private long createTime = System.nanoTime();

    /**
     * SSE 发送器实例，用于实际的事件推送
     */
    private SseEmitter sseEmitter;

    /**
     * 获取客户端ID
     *
     * @return 客户端ID
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * 设置客户端ID
     *
     * @param clientId 客户端ID
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /**
     * 获取事件类型集合
     *
     * @return 事件类型集合
     */
    public Set<String> getEvent() {
        return event;
    }

    /**
     * 设置事件类型集合
     *
     * @param event 事件类型集合
     */
    public void setEvent(Set<String> event) {
        this.event = event;
    }

    /**
     * 获取实体对象
     *
     * @return 实体对象
     */
    public Object getEntity() {
        return entity;
    }

    /**
     * 设置实体对象
     *
     * @param entity 实体对象
     */
    public void setEntity(Object entity) {
        this.entity = entity;
    }

    /**
     * 获取HTTP响应对象
     *
     * @return HTTP响应对象
     */
    public HttpServletResponse getResponse() {
        return response;
    }

    /**
     * 设置HTTP响应对象
     *
     * @param response HTTP响应对象
     */
    public void setResponse(HttpServletResponse response) {
        this.response = response;
    }

    /**
     * 获取创建时间
     *
     * @return 创建时间
     */
    public long getCreateTime() {
        return createTime;
    }

    /**
     * 设置创建时间
     *
     * @param createTime 创建时间
     */
    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    /**
     * 获取SSE发送器实例
     *
     * @return SSE发送器实例
     */
    public SseEmitter getSseEmitter() {
        return sseEmitter;
    }

    /**
     * 设置SSE发送器实例
     *
     * @param sseEmitter SSE发送器实例
     */
    public void setSseEmitter(SseEmitter sseEmitter) {
        this.sseEmitter = sseEmitter;
    }
}
