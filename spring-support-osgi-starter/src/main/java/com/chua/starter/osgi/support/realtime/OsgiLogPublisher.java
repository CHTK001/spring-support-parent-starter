package com.chua.starter.osgi.support.realtime;

/**
 * 插件操作日志发布器。
 *
 * <p>控制器只依赖这个接口，不关心日志最终去哪：宿主引入 socket starter 时由
 * {@link SocketOsgiLogPublisher} 广播到全局连接，未引入时由
 * {@link OsgiRealtimeConfiguration} 的空实现兜底。</p>
 *
 * <p><b>发布失败绝不能影响插件操作本身</b>：日志是旁路。实现方必须自行吞掉网络异常
 * 并降级为本地日志，否则「日志通道抖动 → 安装失败」是最难排查的一类故障。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface OsgiLogPublisher {

    /**
     * 发布一条插件操作日志。
     *
     * @param event 日志事件，{@code null} 时忽略
     */
    void publish(OsgiLogEvent event);
}
