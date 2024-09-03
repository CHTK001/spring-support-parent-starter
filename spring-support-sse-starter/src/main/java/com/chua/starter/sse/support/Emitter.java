package com.chua.starter.sse.support;

import jakarta.servlet.http.HttpServletResponse;
import lombok.Builder;
import lombok.Data;
import lombok.Singular;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;


/**
 * 发射器
 *
 * @author CH
 * @since 2023/09/26
 */
@Data
@Builder
public class Emitter {

    /**
     * 客户端id
     */
    String clientId;
    /**
     * 事件
     */
    @Singular("event")
    Set<String> event;

    /**
     * 实体
     */
    Object entity;
    /**
     * 响应
     */
    HttpServletResponse response;
    /**
     * 创建时间
     */
    @Builder.Default
    long createTime = System.nanoTime();

    SseEmitter sseEmitter;
}
