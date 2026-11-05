// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.sync.support.pojo;

import java.io.Serializable;

/**
 * 同步响应
 *
 * @param requestId  请求标识
 * @param clientId   客户端标识
 * @param topic      主题
 * @param code       状态码 (200-成功, 400-参数错误, 500-内部错误)
 * @param message    消息
 * @param data       数据
 * @param timestamp  时间戳
 * @param duration   耗时(毫秒)
 * @since 2024/12/05
 * @author CH
 */
public record SyncResponse(
        String requestId,
        String clientId,
        String topic,
        int code,
        String message,
        Object data,
        long timestamp,
        long duration) implements Serializable {

    /**
     * 串行版本UID
     */
    private static final long serialVersionUID = 1L;

    /**
     * 是否成功
     *
     * @return 是否成功的结果
     */
    public boolean isSuccess() {
        return code == 200;
    }

    /**
     * 成功
     *
     * @param requestId 请求标识
     * @param clientId  客户端标识
     * @param data      数据
     * @return 成功的结果
     */
    public static SyncResponse success(String requestId, String clientId, Object data) {
        return new SyncResponse(requestId, clientId, null, 200,
                "SUCCESS", data, System.currentTimeMillis(), 0L);
    }

    /**
     * 失败
     *
     * @param requestId 请求标识
     * @param clientId  客户端标识
     * @param code      编码
     * @param message   消息
     * @return 失败的结果
     */
    public static SyncResponse fail(String requestId, String clientId, int code, String message) {
        return new SyncResponse(requestId, clientId, null, code,
                message, null, System.currentTimeMillis(), 0L);
    }

    /**
     * 记录错误
     *
     * @param requestId 请求标识
     * @param clientId  客户端标识
     * @param message   消息
     * @return 错误的结果
     */
    public static SyncResponse error(String requestId, String clientId, String message) {
        return fail(requestId, clientId, 500, message);
    }
}