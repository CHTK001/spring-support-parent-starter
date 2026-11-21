package com.chua.starter.osgi.support.realtime;

import com.chua.starter.logger.event.OsgiLogEvent;

/**
 * 插件操作日志发布器。
 *
 * <p>控制器只依赖这个接口，不关心日志最终去哪：默认实现（事件桥）把
 * {@code OsgiLogEvent} 作为 Spring 应用事件发布，事件契约定义在 logger-starter；
 * 宿主引入 socket-starter 时由 socket 侧的 SocketOsgiLogBridge 监听该事件并广播到
 * 全局连接。osgi 不直接依赖 socket，避免 {@code common -> osgi -> socket -> common}
 * 依赖环。</p>
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
