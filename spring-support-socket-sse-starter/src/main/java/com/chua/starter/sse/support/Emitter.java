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
}
