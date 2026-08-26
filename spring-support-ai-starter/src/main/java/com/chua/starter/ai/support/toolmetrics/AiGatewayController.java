package com.chua.starter.ai.support.toolmetrics;

import com.chua.common.support.lang.code.ReturnResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 网关端口管理。
 *
 * <p>在运行中的 Servlet 容器上动态添加/移除 Connector，
 * 使 /v2/** 端点同时监听主端口与独立 AI 端口。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/v2/ai/gateway")
@RequiredArgsConstructor
public class AiGatewayController {

    /** Spring 应用上下文 */
    private final ApplicationContext applicationContext;

    /** 当前 Connector 引用（跨请求保持） */
    private volatile Object activeConnector;

    /**
     * 启动独立 AI 端口。
     *
     * @param body 包含 port 字段
     * @return 操作结果
     */
    @PostMapping("/start")
    public ReturnResult<String> start(@RequestBody Map<String, Object> body) {
        int port = Integer.parseInt(String.valueOf(body.get("port")));
        if (activeConnector != null) {
            return ReturnResult.ok("已在运行");
        }
        try {
            Object webServer = applicationContext.getBean("webServer");
            Object tomcat = webServer.getClass().getMethod("getTomcat").invoke(webServer);
            Object service = tomcat.getClass().getMethod("getService").invoke(tomcat);
            Class<?> connClass = Class.forName("org.apache.catalina.connector.Connector");
            Object conn = connClass.getConstructor(int.class).newInstance(port);
            service.getClass().getMethod("addConnector", connClass).invoke(service, conn);
            conn.getClass().getMethod("start").invoke(conn);
            activeConnector = conn;
            log.info("[AiGateway] 独立端口 {} 已启动", port);
            return ReturnResult.ok("已启动端口 " + port);
        } catch (Exception e) {
            log.warn("[AiGateway] 启动失败", e);
            return ReturnResult.error("启动失败: " + e.getMessage());
        }
    }

    /**
     * 停止独立 AI 端口。
     *
     * @return 操作结果
     */
    @PostMapping("/stop")
    public ReturnResult<String> stop() {
        if (activeConnector == null) { return ReturnResult.ok("未在运行"); }
        try {
            activeConnector.getClass().getMethod("stop").invoke(activeConnector);
            Object tomcat = applicationContext.getBean("webServer")
                    .getClass().getMethod("getTomcat").invoke(
                            applicationContext.getBean("webServer"));
            Object service = tomcat.getClass().getMethod("getService").invoke(tomcat);
            service.getClass().getMethod("removeConnector",
                    Class.forName("org.apache.catalina.connector.Connector"))
                    .invoke(service, activeConnector);
            log.info("[AiGateway] 端口已停止");
        } catch (Exception e) {
            log.debug("[AiGateway] 停止异常: {}", e.getMessage());
        }
        activeConnector = null;
        return ReturnResult.ok("已停止");
    }

    /**
     * 查询网关状态。
     *
     * @return running 与 port 字段
     */
    @GetMapping("/status")
    public ReturnResult<Map<String, Object>> status() {
        boolean running = activeConnector != null;
        int port = -1;
        if (running) {
            try {
                port = (int) activeConnector.getClass()
                        .getMethod("getPort").invoke(activeConnector);
            } catch (Exception ignored) { }
        }
        return ReturnResult.ok(Map.of("running", running, "port", port));
    }
}
