package com.chua.starter.server.support.client;

import com.chua.starter.server.support.entity.MsServerHost;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Docker 操作通道解析与共享行为（CMD / API 双通道的公共契约）。
 *
 * <p>主机级 Docker 通道配置存放在 {@code server_host} 表：
 * {@code server_host_docker_mode}（CMD=SSH + docker CLI，默认；API=Docker Engine HTTP API）、
 * {@code server_host_docker_api_url}（API 通道地址，缺省按 {@code http://主机:2375} 推导）。</p>
 *
 * <p>主机工作台（{@code WorkbenchServiceImpl}）与独立 Docker 管理模块
 * （{@code DockerServiceImpl}）共用本类，避免通道判定与日志流推送逻辑出现两份实现。</p>
 *
 * @since 4.0.0.52
 * @author CH
 */
public final class DockerChannel {

    /**
     * CMD 通道（SSH + docker CLI，默认）
     */
    public static final String MODE_CMD = "CMD";

    /**
     * API 通道（Docker Engine HTTP API）
     */
    public static final String MODE_API = "API";

    /**
     * API 通道不支持交互式 exec（hijack + tty）时的统一提示
     */
    public static final String EXEC_UNSUPPORTED_MESSAGE =
            "当前主机 Docker 为 API 通道，暂不支持进入容器；请将该主机 Docker 通道切换为 CMD 后重试";

    /**
     * 工具类禁止实例化
     */
    private DockerChannel() {
    }

    /**
     * 判断主机 Docker 操作通道是否为 API（Docker Engine HTTP API）
     *
     * @param host 主机实体
     * @return true 表示 API 通道
     */
    public static boolean isApiMode(MsServerHost host) {
        return host != null && MODE_API.equalsIgnoreCase(host.getServerHostDockerMode());
    }

    /**
     * 创建主机对应的 Docker Engine API 客户端
     *
     * @param host 主机实体
     * @return API 客户端
     */
    public static DockerEngineClient client(MsServerHost host) {
        String url = host.getServerHostDockerApiUrl();
        if (url == null || url.isBlank()) {
            // 未配置时按 Docker 默认端口推导
            url = "http://" + host.getServerHostAddress() + ":2375";
        }
        return new DockerEngineClient(url);
    }

    /**
     * API 通道容器日志跟随流：经 Docker Engine API 拉取多路复用日志流，
     * 解包后按行推送到 SSE（等价 cmd 通道的 {@code docker logs -f}）。
     *
     * <p>事件协议：{@code session}（会话标识）、{@code log}（日志行）、
     * {@code error}（失败原因），结束时机由流关闭决定。</p>
     *
     * @param client      Docker Engine API 客户端
     * @param emitter     SSE 发射器
     * @param sessionId   会话标识
     * @param containerId 容器 ID 或名称
     * @param tail        起始尾部行数
     * @param errorLogger 失败日志入口（可空）
     */
    public static void streamLogsViaApi(DockerEngineClient client, SseEmitter emitter, String sessionId,
                                        String containerId, Integer tail, Consumer<String> errorLogger) {
        int lines = tail == null || tail < 1 ? 100 : tail;
        try {
            InputStream in = client.streamLogs(containerId, lines);
            emitter.send(SseEmitter.event().name("session").data(sessionId, MediaType.TEXT_PLAIN));
            Runnable cleanup = () -> {
                try {
                    in.close();
                } catch (Exception ignored) {
                    // 忽略：关闭失败（流已关闭或客户端已断开）
                }
            };
            emitter.onCompletion(cleanup);
            emitter.onTimeout(cleanup);
            emitter.onError(e -> cleanup.run());
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    emitter.send(SseEmitter.event().name("log").data(line, MediaType.TEXT_PLAIN));
                }
            }
            emitter.complete();
        } catch (Exception e) {
            if (errorLogger != null) {
                errorLogger.accept(e.getMessage());
            }
            try {
                emitter.send(SseEmitter.event().name("error").data("日志流启动失败: " + e.getMessage(), MediaType.TEXT_PLAIN));
            } catch (Exception ignored) {
                // 忽略：发送失败（客户端已断开，无法推送错误事件）
            }
            emitter.complete();
        }
    }
}
