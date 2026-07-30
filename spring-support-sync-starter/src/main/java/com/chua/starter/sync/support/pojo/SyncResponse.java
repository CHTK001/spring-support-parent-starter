package com.chua.starter.sync.support.pojo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 同步响应
 *
 * @author CH
 * @version 1.0.0
 * @since 2024/12/05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 请求ID
     */
    private String requestId;

    /**
     * 客户端ID
     */
    private String clientId;

    /**
     * 主题
     */
    private String topic;

    /**
     * 状态码 (200-成功, 400-参数错误, 500-内部错误)
     */
    private int code;

    /**
     * 消息
     */
    private String message;

    /**
     * 数据
     */
    private Object data;

    /**
     * 时间戳
     */
    private long timestamp;

    /**
     * 耗时(毫秒)
     */
    private long duration;

    public boolean isSuccess() {
        return code == 200;
    }

    public static SyncResponse success(String requestId, String clientId, Object data) {
        return SyncResponse.builder()
                .requestId(requestId)
                .clientId(clientId)
                .code(200)
                .message("SUCCESS")
                .data(data)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static SyncResponse fail(String requestId, String clientId, int code, String message) {
        return SyncResponse.builder()
                .requestId(requestId)
                .clientId(clientId)
                .code(code)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static SyncResponse error(String requestId, String clientId, String message) {
        return fail(requestId, clientId, 500, message);
    }

}
