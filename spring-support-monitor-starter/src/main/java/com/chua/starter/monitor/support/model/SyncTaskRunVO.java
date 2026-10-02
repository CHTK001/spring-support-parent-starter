package com.chua.starter.monitor.support.model;

import java.time.LocalDateTime;

/**
 * 数据同步的一次执行结果。
 *
 * <p>无论成功还是失败都返回本结构：失败时 {@code status} 为 {@code FAILED}、
 * {@code message} 带上可读原因，界面据此把「读了 0 行」和「读取报错」区分开，
 * 不至于把异常吞成一句「同步完成」。</p>
 *
 * @param status     执行状态：{@code SUCCESS} / {@code FAILED}
 * @param readCount  从源读取到的行数
 * @param writeCount 实际写入目标的行数（以 JDBC 批量提交的返回值为准，不是「提交了多少行」的估算）
 * @param costMs     总耗时（毫秒）
 * @param message    结果说明或错误信息
 * @param runTime    执行时刻
 * @author CH
 * @since 4.0.0.43
 */
public record SyncTaskRunVO(
        String status,
        Long readCount,
        Long writeCount,
        Long costMs,
        String message,
        LocalDateTime runTime) {

    /**
     * 成功状态码。
     */
    public static final String STATUS_SUCCESS = "SUCCESS";

    /**
     * 失败状态码。
     */
    public static final String STATUS_FAILED = "FAILED";

    /**
     * 构造成功结果。
     *
     * @param readCount  读取行数
     * @param writeCount 写入行数
     * @param costMs     耗时（毫秒）
     * @param runTime    执行时刻
     * @return 成功结果
     */
    public static SyncTaskRunVO success(long readCount, long writeCount, long costMs, LocalDateTime runTime) {
        return new SyncTaskRunVO(STATUS_SUCCESS, readCount, writeCount, costMs,
                "读取 " + readCount + " 行，写入 " + writeCount + " 行", runTime);
    }

    /**
     * 构造失败结果。
     *
     * @param readCount 读取行数（可能已读到一部分）
     * @param costMs    耗时（毫秒）
     * @param message   错误信息
     * @param runTime   执行时刻
     * @return 失败结果
     */
    public static SyncTaskRunVO failed(long readCount, long costMs, String message, LocalDateTime runTime) {
        return new SyncTaskRunVO(STATUS_FAILED, readCount, 0L, costMs, message, runTime);
    }
}