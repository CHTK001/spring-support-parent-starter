package com.chua.starter.monitor.support.config;

import com.chua.starter.monitor.support.service.impl.MonitorPushService;
import com.chua.starter.server.support.entity.MsServerHost;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * 主机检测推送桥接（服务端-启动 ↔ 监控-启动）
 * <p>
 * 提供延迟检测 / 在线状态翻转的推送回调，由
 * {@link HostRealtimePushConfiguration} 统一注册到 {@code HostOnlineScheduler}，
 * 经 {@link MonitorPushService} 与 Socket:push topic 广播到前端
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

    /**
     * 主机延迟与在线状态推送桥接
    */

    /**
     * 推送服务（监控-启动）
    */
    private final MonitorPushService pushService;

    /**
     * 延迟检测结果回调 → 广播实时延迟
     *
     * @param host 完成一次延迟探测的主机（服务端主机延迟ms 已回写，-1 表不可达）
     */
    void onDelayDetected(MsServerHost host) {
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
    void onOnlineChanged(MsServerHost host) {
        pushService.pushHostOnline(
                host.getId(),
                host.getServerHostName(),
                host.getServerHostOnline() != null && host.getServerHostOnline() == 1
        );
    }
}
