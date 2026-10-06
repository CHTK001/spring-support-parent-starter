package com.chua.starter.datasourcehub.support.model;

/**
 * Nginx stub_status 实时状态。
 *
 * <p>目标未配置 stub_status（{@code /nginx_status}）或不可达时 {@code available} 为 false，
 * 其余计数字段为 null，并由 {@code message} 说明<strong>具体原因</strong>（试过哪些地址、
 * 各自失败原因），页面直接展示即可，不需要用户自己猜是端口不对还是没配模块。</p>
 *
 * @param available         是否成功取到 stub_status
 * @param source            实际取到数据的地址，未取到为 null
 * @param activeConnections 活跃连接数
 * @param accepts           总接受连接数
 * @param handled           总处理连接数
 * @param requests          总请求数
 * @param reading           正在读取请求头的连接数
 * @param writing           正在处理请求 / 返回响应的连接数
 * @param waiting           空闲等待中的连接数
 * @param raw               原始 stub_status 文本
 * @param message           面向用户的说明：成功时为「数据来源」，失败时为失败原因
 * @author CH
 * @since 4.0.0.42
 */
public record NginxStatusVO(
        boolean available,
        String source,
        Long activeConnections,
        Long accepts,
        Long handled,
        Long requests,
        Long reading,
        Long writing,
        Long waiting,
        String raw,
        String message
) {

    /**
     * 构造「不可用」状态。
     *
     * @param message 失败原因
     * @return 状态对象
     */
    public static NginxStatusVO unavailable(String message) {
        return new NginxStatusVO(false, null, null, null, null, null, null, null, null, null, message);
    }
}
