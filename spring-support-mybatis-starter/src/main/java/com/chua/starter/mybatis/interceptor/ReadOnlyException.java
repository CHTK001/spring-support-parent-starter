package com.chua.starter.mybatis.interceptor;

/**
 * 只读模式异常
 * 当数据库处于只读模式时，执行更新、插入、删除操作会抛出此异常
 *
 * @author CH
 * @since 2024/12/04
 */
public class ReadOnlyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 构造函数
     */
    public ReadOnlyException() {
        super("数据库当前处于只读模式，禁止执行写入操作");
    }

    /**
     * 构造函数
     *
     * @param message 异常信息
     */
    public ReadOnlyException(String message) {
        super(message);
    }

    /**
     * 构造函数
     *
     * @param message 异常信息
     * @param cause   原因
     */
    public ReadOnlyException(String message, Throwable cause) {
        super(message, cause);
    }
}
