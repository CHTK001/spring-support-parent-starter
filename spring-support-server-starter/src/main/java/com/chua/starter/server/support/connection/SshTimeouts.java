package com.chua.starter.server.support.connection;

import com.chua.starter.server.support.event.ServerConfigChangedEvent;
import com.chua.starter.server.support.service.ServerConfigService;
import com.chua.starter.server.support.util.SshClients;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * SSH 超时配置（运行时可改）
 * <p>
 * 三级超时统一在这里解析，优先级：数据库系统配置（{@code /ms/server/config}）
 * → yaml / 环境变量（{@code plugin.monitor.ssh.*-timeout}）→ 内置默认值。
 * 保存系统配置后由 {@link ServerConfigChangedEvent} 触发热刷新，
 * 并同步给 {@link SshClients} 的进程级默认值，因此所有未显式传超时的
 * SSH 调用点（Windows 远程执行、主机探测、终端隧道等）都会立即使用新值，
 * 无需重启平台。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SshTimeouts {

    /**
     * 系统配置键：TCP 连接/握手超时（秒）
     */
    public static final String KEY_CONNECT = "ssh.connect.timeout";

    /**
     * 系统配置键：认证/会话建立超时（秒）
     */
    public static final String KEY_SESSION = "ssh.session.timeout";

    /**
     * 系统配置键：命令执行/输出读取超时（秒）
     */
    public static final String KEY_READ = "ssh.read.timeout";

    /**
     * 允许的最小值（秒）
     */
    private static final int MIN_SECONDS = 1;

    /**
     * 连接/会话超时的上界（秒）
     */
    private static final int MAX_HANDSHAKE_SECONDS = 600;

    /**
     * 读取超时的上界（秒）
     */
    private static final int MAX_READ_SECONDS = 86_400;

    /**
     * yaml：plugin.monitor.ssh.connect-timeout
     */
    @Value("${plugin.monitor.ssh.connect-timeout:15}")
    private int yamlConnectTimeout;

    /**
     * yaml：plugin.monitor.ssh.session-timeout（默认 30，避免高负载时 15s 认证超时）
     */
    @Value("${plugin.monitor.ssh.session-timeout:30}")
    private int yamlSessionTimeout;

    /**
     * yaml：plugin.monitor.ssh.read-timeout；未配置时沿用旧的 plugin.monitor.ssh.exec-timeout
     */
    @Value("${plugin.monitor.ssh.read-timeout:${plugin.monitor.ssh.exec-timeout:60}}")
    private int yamlReadTimeout;

    /**
     * 系统配置服务（数据库键值）
     */
    private final ServerConfigService serverConfigService;

    /**
     * 当前生效值：连接/握手超时（秒）
     */
    // [P3C A20 豁免]
    private volatile int connectTimeout = 15;

    /**
     * 当前生效值：认证/会话建立超时（秒）
     */
    // [P3C A20 豁免]
    private volatile int sessionTimeout = 30;

    /**
     * 当前生效值：命令执行/输出读取超时（秒）
     */
    // [P3C A20 豁免]
    private volatile int readTimeout = 60;

    /**
     * 启动时加载一次，并同步给 {@link SshClients}
     */
    @PostConstruct
    public void init() {
        refresh();
    }

    /**
     * 系统配置变更后热刷新
     *
     * <p>只认这三个键：{@code ServerConfigChangedEvent} 是「任意系统配置保存」都会发的
     * 通用事件，之前这里无条件 {@link #refresh()}，结果保存终端配色这类与 SSH 无关的
     * 配置也会重刷超时并打一行「[ssh] 超时生效」，日志里看着像 SSH 设置被改过。</p>
     *
     * @param event 变更事件
     */
    @EventListener(ServerConfigChangedEvent.class)
    public void onConfigChanged(ServerConfigChangedEvent event) {
        Set<String> keys = event == null ? null : event.keys();
        if (keys == null
                || keys.contains(KEY_CONNECT)
                || keys.contains(KEY_SESSION)
                || keys.contains(KEY_READ)) {
            refresh();
        }
    }

    /**
     * 重新解析三级超时并同步给 SSH 客户端工厂
     * <p>
     * 数据库不可用或取值非法时保留 yaml / 内置默认值，绝不因配置问题阻断启动。
     * </p>
     */
    public void refresh() {
        int connect = resolve(KEY_CONNECT, yamlConnectTimeout, MIN_SECONDS, MAX_HANDSHAKE_SECONDS);
        int session = resolve(KEY_SESSION, yamlSessionTimeout, MIN_SECONDS, MAX_HANDSHAKE_SECONDS);
        int read = resolve(KEY_READ, yamlReadTimeout, MIN_SECONDS, MAX_READ_SECONDS);
        connectTimeout = connect;
        sessionTimeout = session;
        readTimeout = read;
        SshClients.setDefaults(connect, session, read);
        log.info("[ssh] 超时生效 connect={}s session={}s read={}s", connect, session, read);
    }

    /**
     * 解析单项配置：数据库优先，非法值回退默认
     *
     * @param key          配置键
     * @param defaultValue 默认值（秒）
     * @param min          最小值
     * @param max          最大值
     * @return 生效值（秒）
     */
    private int resolve(String key, int defaultValue, int min, int max) {
        int fallback = clamp(defaultValue, min, max);
        try {
            return clamp(serverConfigService.getInt(key, fallback), min, max);
        } catch (Exception e) {
            log.warn("[ssh] 读取配置 {} 失败，沿用默认 {}s：{}", key, fallback, e.getMessage());
            return fallback;
        }
    }

    /**
     * 区间钳制
     *
     * @param value 原值
     * @param min   最小值
     * @param max   最大值
     * @return 钳制后的值
     */
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 连接/握手超时（秒）
     *
     * @return 秒
     */
    public int connectSeconds() {
        return connectTimeout;
    }

    /**
     * 认证/会话建立超时（秒）
     *
     * @return 秒
     */
    public int sessionSeconds() {
        return sessionTimeout;
    }

    /**
     * 命令执行/输出读取超时（秒）
     *
     * @return 秒
     */
    public int readSeconds() {
        return readTimeout;
    }
}
