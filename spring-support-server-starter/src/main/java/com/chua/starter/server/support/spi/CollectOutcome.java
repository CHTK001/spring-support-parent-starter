package com.chua.starter.server.support.spi;

import lombok.Getter;

/**
 * 主机采集结果
 *
 * @author CH
 * @since 4.0.0.42
 */
@Getter
public class CollectOutcome {

    /** 是否成功 */
    private final boolean success;

    /** 结果消息 */
    private final String message;

    /**
     * 构造
     *
     * @param success 是否成功
     * @param message 消息
     */
    public CollectOutcome(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    /**
     * 成功
     *
     * @param message 消息
     * @return 结果
     */
    public static CollectOutcome ok(String message) {
        /**
          * collectoutcome。
         * @param true true
         * @param message 消息
         */
        return new CollectOutcome(true, message);
    }

    /**
     * 失败
     *
     * @param message 消息
     * @return 结果
     */
    public static CollectOutcome fail(String message) {
        /**
          * collectoutcome。
         * @param false false
         * @param message 消息
         */
        return new CollectOutcome(false, message);
    }
}
