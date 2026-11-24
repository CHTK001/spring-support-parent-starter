package com.chua.starter.docker.local;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.client.DockerChannel;
import com.chua.starter.server.support.entity.MsServerHost;
import com.chua.starter.server.support.service.ServerHostService;
import com.chua.starter.server.support.spi.RemoteExecResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * 本机 Docker 自注册器。
 *
 * <p>后端所在本机装有 Docker 时，应用就绪后自动把本机注册为 Docker 主机
 * （127.0.0.1、CMD 通道、免凭据），Docker 菜单即可直接对本机做容器/镜像/
 * 日志/终端操作，免去手工添加。已存在同地址主机记录时跳过（幂等）。</p>
 *
 * <p>探测与注册均为尽力而为：独立守护线程执行，任何失败只记日志，
 * 不影响应用启动，也不抛出到调用方。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalDockerHostRegistrar {

    /**
     * 本机在主机台账中的固定地址
     */
    private static final String LOCAL_ADDRESS = "127.0.0.1";

    /**
     * 主机服务（查询/注册走 server 模块契约，不直连其持久层）
     */
    private final ServerHostService serverHostService;

    /**
     * 本机 docker 命令执行器
     */
    private final LocalDockerExecutor localExecutor;

    /**
     * 应用就绪后异步探测本机 Docker 并自注册
     *
     * @param event 就绪事件
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        Thread thread = new Thread(this::registerSafely, "docker-local-host-registrar");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 探测 + 注册，失败只记日志
     */
    private void registerSafely() {
        try {
            register();
        } catch (Exception e) {
            log.warn("[docker] 本机 Docker 自注册失败（不影响启动）: {}", e.getMessage());
        }
    }

    /**
     * 探测本机 docker，可用且未注册时注册本机主机（CMD 通道、免凭据）
     *
     * @throws Exception 探测失败
     */
    private void register() throws Exception {
        RemoteExecResult probe;
        try {
            probe = localExecutor.exec("docker version --format '{{.Server.Version}}'", 15, null);
        } catch (Exception e) {
            log.info("[docker] 本机未检测到可用 Docker（{}），跳过自注册", e.getMessage());
            return;
        }
        if (probe.exitCode() != 0 || probe.stdout().isBlank()) {
            log.info("[docker] 本机未检测到可用 Docker（退出码 {}），跳过自注册", probe.exitCode());
            return;
        }
        if (!serverHostService.listByAddress(LOCAL_ADDRESS).isEmpty()) {
            log.info("[docker] 本机 {} 已存在主机记录，跳过 Docker 自注册", LOCAL_ADDRESS);
            return;
        }
        MsServerHost host = new MsServerHost();
        host.setServerHostName("本机");
        host.setServerHostAddress(LOCAL_ADDRESS);
        host.setServerHostPort(22);
        host.setServerHostOsType(localOsType());
        host.setServerHostDockerMode(DockerChannel.MODE_CMD);
        ReturnResult<MsServerHost> created = serverHostService.create(host);
        if (created != null && created.isOk()) {
            log.info("[docker] 已自动注册本机 Docker 主机 id={} address={} dockerMode=CMD",
                    created.getData() == null ? null : created.getData().getId(), LOCAL_ADDRESS);
        } else {
            log.warn("[docker] 本机 Docker 自注册未成功: {}", created == null ? "无返回" : created.getMsg());
        }
    }

    /**
     * 本机操作系统平台（与 {@code HostPlatforms} 常量口径一致）
     *
     * @return 平台编码
     */
    private String localOsType() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return "WINDOWS";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "MACOS";
        }
        return "LINUX";
    }
}
