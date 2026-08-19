package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.configuration.protocol.AiProtocolServerManager;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 聚合协议服务控制器。
 *
 * <p>管理聚合 ChatClient 协议服务的启停与热更新。协议服务通过独立端口暴露
 * OpenAI / Claude / Gemini 三协议端点，端口与开关由 {@code runtime} 分组全局配置控制。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/protocol-server")
@RequiredArgsConstructor
@Tag(name = "AI 聚合协议服务", description = "聚合 ChatClient 协议服务启停、状态与热更新")
public class AiProtocolServiceController {

    /**
     * 聚合协议服务管理器
     */
    private final AiProtocolServerManager protocolServerManager;

    /**
     * 查询聚合协议服务状态。
     *
     * @return 状态（running / port / clientCount）
     */
    @Permission("admin")
    @Operation(summary = "查询聚合协议服务状态")
    @GetMapping("/status")
    public ReturnResult<Map<String, Object>> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("running", protocolServerManager.isRunning());
        data.put("port", protocolServerManager.getPort());
        data.put("clientCount", protocolServerManager.getAggregateClient() != null
                ? protocolServerManager.getAggregateClient().clientCount()
                : 0);
        return ReturnResult.ok(data);
    }

    /**
     * 启动聚合协议服务。
     *
     * @return 启动结果
     */
    @Permission("admin")
    @Operation(summary = "启动聚合协议服务")
    @PostMapping("/start")
    public ReturnResult<String> start() {
        protocolServerManager.start();
        return ReturnResult.ok("聚合协议服务启动完成，端口 " + protocolServerManager.getPort());
    }

    /**
     * 停止聚合协议服务。
     *
     * @return 停止结果
     */
    @Permission("admin")
    @Operation(summary = "停止聚合协议服务")
    @PostMapping("/stop")
    public ReturnResult<String> stop() {
        protocolServerManager.stop();
        return ReturnResult.ok("聚合协议服务已停止");
    }

    /**
     * 热更新聚合客户端（按厂商表增删底层 ChatClient）。
     *
     * @return 热更新结果
     */
    @Permission("admin")
    @Operation(summary = "热更新聚合客户端", description = "按 sys_ai_manufacturer 表实时增删底层 ChatClient，无需重启")
    @PostMapping("/reload")
    public ReturnResult<String> reload() {
        protocolServerManager.reloadClients();
        int count = protocolServerManager.getAggregateClient() != null
                ? protocolServerManager.getAggregateClient().clientCount()
                : 0;
        return ReturnResult.ok("聚合客户端热更新完成，当前 " + count + " 个底层客户端");
    }
}
