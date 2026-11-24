package com.chua.starter.osgi.support.realtime;

import com.chua.common.support.osgi.BundleLifecycleListener;
import com.chua.starter.logger.event.OsgiStateEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

/**
 * bundle 生命周期 → Spring 应用事件桥。
 *
 * <p>挂在框架级 {@code BundleLifecycleListener} 链上：无论状态变化来自控制台操作、
 * 热部署扫描器、shell 命令还是其他 bundle 的编程式调用，都会被转成
 * {@code OsgiStateEvent}（契约定义在 logger-starter）作为 Spring 应用事件发布。
 * 宿主引入 socket-starter 时由 socket 侧的 SocketOsgiStateBridge 监听该事件并
 * 广播到全局连接，前端实时刷新 bundle 状态墙；未引入 socket 时事件无人消费，
 * 天然 no-op，osgi 模块仍可独立启动。</p>
 *
 * <p><b>为什么走 Spring 事件而不是直接依赖 socket</b>：socket-starter 依赖 common，
 * 若 osgi 再依赖 socket（哪怕 optional），一旦 common 依赖 osgi（SPI 注册门面），
 * reactor 就会出现 {@code common -> osgi -> socket -> common} 依赖环。
 * 把事件契约下沉到 logger-starter 后，osgi 与 socket 互不可见，环解开。</p>
 *
 * <p><b>状态推送是旁路</b>：发布失败只降级为本地日志，绝不能影响插件生命周期操作本身；
 * 框架对监听器异常已有隔离（单个监听器抛异常不阻断其他监听器），这里再兜一层。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class OsgiStateEventBridge implements BundleLifecycleListener {

    /**
     * Spring 事件发布器。
     */
    private final ApplicationEventPublisher publisher;

    /**
     * 构造状态事件桥。
     *
     * @param publisher Spring 事件发布器
     */
    public OsgiStateEventBridge(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void onBundleInstalled(String symbolicName) {
        publish(OsgiStateEvent.installed(symbolicName));
    }

    @Override
    public void onBundleStarted(String symbolicName) {
        publish(OsgiStateEvent.started(symbolicName));
    }

    @Override
    public void onBundleStopped(String symbolicName) {
        publish(OsgiStateEvent.stopped(symbolicName));
    }

    @Override
    public void onBundleUpdated(String symbolicName, String newVersion) {
        publish(OsgiStateEvent.updated(symbolicName, newVersion));
    }

    @Override
    public void onBundleUninstalled(String symbolicName) {
        publish(OsgiStateEvent.uninstalled(symbolicName));
    }

    @Override
    public void onBundleStateChanged(String symbolicName, String oldState, String newState) {
        publish(OsgiStateEvent.stateChanged(symbolicName, oldState, newState));
    }

    @Override
    public void onBundleRemoved(String symbolicName, String location, String version) {
        publish(OsgiStateEvent.removed(symbolicName, version));
    }

    @Override
    public void onBundleResolveFailed(String symbolicName, String reason) {
        publish(OsgiStateEvent.resolveFailed(symbolicName, reason));
    }

    /**
     * 发布状态事件，失败降级为本地 debug 日志。
     *
     * @param event 状态事件
     */
    private void publish(OsgiStateEvent event) {
        try {
            publisher.publishEvent(event);
        } catch (RuntimeException ex) {
            log.debug("[osgi-state] 状态事件发布失败: action={}, symbolicName={}, reason={}",
                    event.action(), event.symbolicName(), ex.getMessage());
        }
    }
}
