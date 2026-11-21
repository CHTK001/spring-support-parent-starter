package com.chua.starter.osgi.support.realtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

/**
 * 插件操作实时日志通道配置。
 *
 * <p>默认装配<b>事件桥发布器</b>：把 {@code OsgiLogEvent}（契约定义在 logger-starter）
 * 作为 Spring 应用事件发布。宿主引入 socket-starter 后，socket 侧的
 * {@code SocketOsgiLogBridge} 监听该事件并把日志广播到全局 socket 连接；
 * 未引入 socket 时事件无人消费，天然 no-op，osgi 模块仍可独立启动。</p>
 *
 * <p><b>为什么走 Spring 事件而不是直接依赖 socket</b>：socket-starter 依赖 common，
 * 若 osgi 再依赖 socket（哪怕 optional），一旦 common 依赖 osgi（SPI 注册门面，P3），
 * reactor 就会出现 {@code common -> osgi -> socket -> common} 依赖环。把事件契约
 * 下沉到 logger-starter 后，osgi 与 socket 互不可见，环解开，前端契约（载荷字段、
 * {@code socket.subscribe("osgi", "operation-log")}）保持不变。</p>
 *
 * <p>业务方仍可提供自己的 {@code OsgiLogPublisher} Bean（如落库 + 推送双写），
 * 本配置会通过 {@code @ConditionalOnMissingBean} 让位。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@AutoConfiguration
public class OsgiRealtimeConfiguration {

    /**
     * 事件桥发布器：把插件操作日志事件发布到 Spring 事件总线。
     *
     * <p><b>发布失败绝不能影响插件操作本身</b>：{@code publishEvent} 对普通
     * {@code @EventListener} 是同步调用，监听器异常会沿调用栈抛回插件操作，
     * 这里必须整体兜住并降级为本地日志。</p>
     *
     * @param eventPublisher Spring 事件发布器
     * @return 事件桥发布器
     */
    @Bean
    @ConditionalOnMissingBean(OsgiLogPublisher.class)
    public OsgiLogPublisher osgiLogEventBridge(ApplicationEventPublisher eventPublisher) {
        return event -> {
            if (event == null) {
                return;
            }
            try {
                eventPublisher.publishEvent(event);
            } catch (Exception e) {
                log.debug("[OSGi][Log] 日志事件发布失败（旁路降级）: action={}, status={}, symbolicName={}, reason={}",
                        event.action(), event.status(), event.symbolicName(), e.getMessage());
            }
        };
    }
}
