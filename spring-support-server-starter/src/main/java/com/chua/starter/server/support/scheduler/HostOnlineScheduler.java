package com.chua.starter.server.support.scheduler;

import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.mapper.MsServerHostMapper;
import com.chua.starter.server.support.service.ServerConfigService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 主机在离线定时检测器
 * <p>
 * 按系统配置的间隔周期探测所有启用主机的 TCP 连通性，
 * 更新在线状态与最近在线时间。探测开关/间隔均可通过系统设置配置。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HostOnlineScheduler {

    /** 主机 Mapper */
    private final MsServerHostMapper hostMapper;

    /** 系统配置服务 */
    private final ServerConfigService configService;

    /** TCP 连接超时(毫秒) */
    private static final int CONNECT_TIMEOUT_MS = 3000;

    /**
     * 定时探测（初始延迟 30s，之后每 30s 检查一次是否需要执行）
     */
    @Scheduled(fixedDelay = 30_000L, initialDelay = 30_000L)
    public void tick() {
        try {
            if (!configService.getBoolean("host.online.enabled", true)) {
                return;
            }
            int interval = configService.getInt("host.online.interval", 60);
            if (interval < 10) {
                interval = 10;
            }
            List<MsServerHost> hosts = hostMapper.selectList(
                    new LambdaQueryWrapper<MsServerHost>().eq(MsServerHost::getServerHostStatus, 1));
            int online = 0;
            for (MsServerHost host : hosts) {
                boolean reachable = isReachable(host);
                if (reachable) {
                    online++;
                }
            log.info("[online] 在离线检测完成: {} 台启用主机, {} 台在线, 间隔 {}s", hosts.size(), online, interval);
        } catch (Exception e) {
            log.warn("[online] 在离线检测异常: {}", e.getMessage());
        }
    }

    /**
     * TCP 探测主机端口连通性
     *
     * @param host 主机
     * @return 是否可达
     */
    private boolean isReachable(MsServerHost host) {
        int port = host.getServerHostPort() == null || host.getServerHostPort() <= 0 ? 22 : host.getServerHostPort();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host.getServerHostAddress(), port), CONNECT_TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
