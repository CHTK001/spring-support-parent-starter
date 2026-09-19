package com.chua.starter.mybatis.interceptor;

/**
 * 只读模式异常
 * 当数据库处于只读模式时，执行更新、插入、删除操作会抛出此异常
 *
 * @since 2024/12/04
 * @author CH
 */
public class ReadOnlyException extends RuntimeException {

    /** 串行版本UID */
    private static final long serialVersionUID = 1L;

    /**
    * 构造函数
    */
    public ReadOnlyException() {
        /**
         * 父。
         * @param "数据库当前处于只读模式，禁止执行写入操作" "数据库当前处于只读模式，禁止执行写入操作"
         */
        super("数据库当前处于只读模式，禁止执行写入操作");
    }

    /**
     * 构造函数
     *
     * @param message 异常信息
     */
    public ReadOnlyException(String message) {
        /**
         * 父。
         * @param message 消息
         */
        super(message);
    }

    /**
     * 构造函数
     *
     * @param message 异常信息
     * @param cause   原因
     */
    public ReadOnlyException(String message, Throwable cause) {
        /**
         * 父。
         * @param message 消息
         * @param cause cause
         */
        super(message, cause);
    }
}
