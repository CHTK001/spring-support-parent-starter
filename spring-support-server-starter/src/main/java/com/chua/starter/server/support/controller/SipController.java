package com.chua.starter.server.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.server.support.constants.ServerConstants;
import com.chua.starter.server.support.service.ServerConfigService;
import com.chua.starter.server.support.sip.SipServiceManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SIP 隧道配置与启停接口
 *
 * <p>提供 SIP 中继服务的配置读写、手动启停、部署命令生成等能力。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Tag(name = "服务器资产-SIP隧道", description = "SIP 内网穿透配置与 Agent 下载")
@RestController
@RequestMapping(ServerConstants.API_PREFIX + "/sip")
public class SipController {

    private static final Logger log = LoggerFactory.getLogger(SipController.class);

    /** 服务器配置服务 */
    private final ServerConfigService configService;
    /** SIP 中继服务管理器 */
    private final SipServiceManager sipManager;

    /**
     * 构造 SIP 控制器
     *
     * @param configService 服务器配置服务
     * @param sipManager    SIP 中继服务管理器
     */
    public SipController(ServerConfigService configService, SipServiceManager sipManager) {
        this.configService = configService;
        this.sipManager = sipManager;
    }

    /**
     * 获取 SIP 状态与当前生效配置
     *
     * @return 配置项与运行状态（sip.enabled / sip.control.port / sip.secret / sip.server.host / running）
     */
    @Operation(summary = "获取 SIP 状态与配置")
    @GetMapping("/config")
    public ReturnResult<Map<String, Object>> getConfig() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("sip.enabled", configService.getString("sip.enabled", "false"));
        cfg.put("sip.control.port", configService.getInt("sip.control.port", 7822));
        cfg.put("sip.secret", configService.getString("sip.secret", "lvzhou2011"));
        cfg.put("sip.server.host", configService.getString("sip.server.host", "124.221.230.112"));
        cfg.put("running", sipManager.isRunning());
        return ReturnResult.success(cfg);
    }

    /**
     * 保存 SIP 配置，并根据 sip.enabled 自动启停中继服务
     *
     * @param body 配置键值对（sip.enabled / sip.control.port / sip.secret 等）
     * @return 成功返回空数据；启停异常时返回错误信息
     */
    @Operation(summary = "保存 SIP 配置（自动启停）")
    @PostMapping("/config")
    public ReturnResult<Void> saveConfig(@RequestBody Map<String, Object> body) {
        configService.saveConfig(body);
        // 根据 enabled 自动启停
        boolean enabled = "true".equalsIgnoreCase(String.valueOf(body.getOrDefault("sip.enabled", "false")));
        int port = Integer.parseInt(String.valueOf(body.getOrDefault("sip.control.port", 7822)));
        String secret = String.valueOf(body.getOrDefault("sip.secret", "lvzhou2011"));
        try {
            if (enabled && !sipManager.isRunning()) {
                sipManager.start(port, secret);
            } else if (!enabled && sipManager.isRunning()) {
                sipManager.stop();
            }
        } catch (Exception e) {
            log.error("[SIP] 启停失败", e);
            return ReturnResult.error("启停失败: " + e.getMessage());
        }
        return ReturnResult.success(null);
    }

    /**
     * 手动启动 SIP 中继服务，启动成功后回写 sip.enabled=true
     *
     * @return 成功返回空数据；启动异常时返回错误信息
     */
    @Operation(summary = "手动启动 SIP")
    @PostMapping("/start")
    public ReturnResult<Void> start() {
        int port = configService.getInt("sip.control.port", 7822);
        String secret = configService.getString("sip.secret", "lvzhou2011");
        try {
            sipManager.start(port, secret);
            configService.saveConfig(Map.of("sip.enabled", "true"));
        } catch (Exception e) {
            return ReturnResult.error("启动失败: " + e.getMessage());
        }
        return ReturnResult.success(null);
    }

    /**
     * 手动停止 SIP 中继服务，并回写 sip.enabled=false
     *
     * @return 成功返回空数据
     */
    @Operation(summary = "手动停止 SIP")
    @PostMapping("/stop")
    public ReturnResult<Void> stop() {
        sipManager.stop();
        configService.saveConfig(Map.of("sip.enabled", "false"));
        return ReturnResult.success(null);
    }

    /**
     * 生成 server / provider / visit 三种角色的部署命令
     *
     * @return 角色名到启动命令的映射
     */
    @Operation(summary = "生成部署命令")
    @GetMapping("/deploy-cmds")
    public ReturnResult<Map<String, String>> deployCmds() {
        String host = configService.getString("sip.server.host", "124.221.230.112");
        int port = configService.getInt("sip.control.port", 7822);
        String secret = configService.getString("sip.secret", "lvzhou2011");

        Map<String, String> cmds = new LinkedHashMap<>();
        cmds.put("server", "java -cp sip.jar com.chua.starter.server.support.sip.SipTunnel server " + port + " " + secret);
        cmds.put("provider", "java -cp sip.jar com.chua.starter.server.support.sip.SipTunnel provider "
                + host + " " + port + " win-test-01 " + secret + " \"rdp:3389,ssh:22\"");
        cmds.put("visit", "java -cp sip.jar com.chua.starter.server.support.sip.SipTunnel visit "
                + host + " " + port + " win-test-01 rdp 13389 " + secret);
        return ReturnResult.success(cmds);
    }
}
