package com.chua.starter.device.support.callback;

/**
 * 单个回调的执行结果。
 *
 * @param callback 回调名称
 * @param ok       是否成功
 * @param message  失败原因或成功说明
 * @param elapsedMs 耗时（毫秒）
 * @since 4.0.0.51
 * @author CH
 */
public record DeviceCallbackResult(
        String callback,
        boolean ok,
        String message,
        long elapsedMs
) {
}
