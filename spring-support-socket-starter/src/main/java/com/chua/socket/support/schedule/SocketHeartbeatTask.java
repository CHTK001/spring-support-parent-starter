package com.chua.socket.support.schedule;

import com.chua.socket.support.session.SocketServerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Socket 服务器心跳定时任务
 * <p>
 * 只做调度壳：周期触发 {@link SocketServerRegistry#heartbeat()}，
 * 刷新本实例在 Redis 中的注册心跳并续期。业务逻辑（enable 判断、
 * serverId、Redis 读写）全部保留在注册表内，本类不持有状态。
 * </p>
 * <p>
 * 原为 {@code SocketServerRegistry} 类内联 {@code @Scheduled} 任务，
 * 解耦搬入本包（com.chua.socket.support.schedule），触发参数与执行逻辑不变。
 * 注意：本模块自身没有 {@code @EnableScheduling}，需宿主开启调度注解后才会生效。
 * </p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Component
@RequiredArgsConstructor
public class SocketHeartbeatTask {

    /**
     * 心跳间隔（毫秒），与原 SocketServerRegistry 内联 @Scheduled 保持一致
     */
    private static final long HEARTBEAT_INTERVAL_MS = 30_000;

    /**
     * Socket 服务器注册表
     */
    private final SocketServerRegistry registry;

    /**
     * 心跳 tick：委托注册表刷新 Redis 心跳。
     */
    @Scheduled(fixedDelay = HEARTBEAT_INTERVAL_MS)
    public void tick() {
        registry.heartbeat();
    }
}
