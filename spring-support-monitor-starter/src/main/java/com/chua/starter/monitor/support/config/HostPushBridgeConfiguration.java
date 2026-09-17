package com.chua.starter.monitor.support.config;

import com.chua.starter.monitor.support.service.impl.MonitorPushService;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.scheduler.HostOnlineScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
* 主机检测推送桥接（服务端-启动 ↔ 监控-启动）
* <p>
* 将 {@link HostOnlineScheduler} 的延迟检测 / 在线状态翻转结果
* 桥接到 {@link MonitorPushService}，经 套接字:push topic 广播到前端
* （前端 订阅("监控", "主机-延迟") / ("监控", "主机-online") 接收），
* 实现资产表实时延迟列的秒级刷新。
* 仅当 服务端-启动 与 监控-启动 同时装配时生效。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class HostPushBridgeConfiguration {

    /** 检测器（服务端-启动） */
    private final HostOnlineScheduler onlineScheduler;

    /** 推送服务（监控-启动） */
    private final MonitorPushService pushService;

    /**
    * 装配推送钩子：延迟结果 / 在线状态翻转 → 套接字 广播。
    * <p>
    * 无入参；由容器在 Bean 初始化后（postconstruct）调用，
    * 将 {@link #onDelayDetected(MsServerHost)} / {@link #onOnlineChanged(MsServerHost)}
    * 注册到 {@link HostOnlineScheduler} 的检测钩子上。
    * </p>
    */
    @jakarta.annotation.PostConstruct
    public void bridge() {
        onlineScheduler.setDelayHook(this::onDelayDetected);
        onlineScheduler.setOnlineHook(this::onOnlineChanged);
        log.info("[monitor] 主机检测推送桥接已装配（host-delay / host-online）");
    }

    /**
    * 延迟检测结果回调 → 广播实时延迟
    *
    * @param host 完成一次延迟探测的主机（服务端主机延迟ms 已回写，-1 表不可达）
    */
    private void onDelayDetected(MsServerHost host) {
        pushService.pushHostDelay(
                host.getId(),
                host.getServerHostName(),
                host.getServerHostAddress(),
                host.getServerHostPort(),
                host.getServerHostDelayMs()
        );
    }

    /**
    * 在线状态翻转回调 → 广播在线状态
    *
    * @param host 在线状态发生翻转的主机（服务端主机online 已更新，1 在线 / 0 离线）
    */
    private void onOnlineChanged(MsServerHost host) {
        pushService.pushHostOnline(
                host.getId(),
                host.getServerHostName(),
                host.getServerHostOnline() != null && host.getServerHostOnline() == 1
        );
    }
}
