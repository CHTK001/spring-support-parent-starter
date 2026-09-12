package com.chua.starter.monitor.support.config;

import com.chua.starter.monitor.support.service.impl.MonitorPushService;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.scheduler.HostOnlineScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
* 主机实时状态 → Socket 推送桥接
* <p>
* 向 server-starter 的 {@link HostOnlineScheduler} 注册延迟/在线 hooks，
* 使主机延迟变化、在线状态翻转实时推送 socket
* （前端 subscribe("monitor", "host-delay" / "host-online") 接收）。
* server-starter 无 socket 依赖，由 monitor 侧按需绑定，未装配 socket 时静默跳过。
* </p>
*
* @author CH
* @since 4.0.0.42
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class HostRealtimePushConfiguration {

    /** 实时推送服务 */
    private final MonitorPushService monitorPushService;

    /**
    * 注册主机实时状态 hooks（延迟/在线 → socket 推送）
    *
    * @param onlineSchedulerProvider 在离线/延迟调度器（server-starter，可能未装配）
    * @return 启动时执行的注册器
     */
    @Bean
    @ConditionalOnBean(HostOnlineScheduler.class)
    public ApplicationRunner hostRealtimePushBinder(ObjectProvider<HostOnlineScheduler> onlineSchedulerProvider) {
        return args -> {
            HostOnlineScheduler scheduler = onlineSchedulerProvider.getIfAvailable();
            if (scheduler == null) {
                log.debug("[monitor] HostOnlineScheduler 未装配，跳过主机实时推送绑定");
                return;
            }
            scheduler.setDelayHook(host -> {
                try {
                    monitorPushService.pushHostDelay(
                            host.getId(), host.getServerHostName(),
                            host.getServerHostAddress(), host.getServerHostPort(),
                            host.getServerHostDelayMs());
                } catch (Exception e) {
                    log.debug("[monitor] 延迟推送跳过 hostId={}: {}", host.getId(), e.getMessage());
                }
            });
            scheduler.setOnlineHook(host -> {
                try {
                    boolean online = host.getServerHostOnline() != null && host.getServerHostOnline() == 1;
                    monitorPushService.pushHostOnline(host.getId(), host.getServerHostName(), online);
                } catch (Exception e) {
                    log.debug("[monitor] 在线状态推送跳过 hostId={}: {}", host.getId(), e.getMessage());
                }
            });
            log.info("[monitor] 主机实时状态推送已绑定（延迟/在线 → socket）");
        };
    }
}