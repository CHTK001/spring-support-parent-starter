package com.chua.starter.panel.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.panel.service.NginxPanelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * SSH 连接管理控制器（通用 SSH 抽象层，委托 NginxPanelService 实现）。
 *
 * @author CH
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/panel/ssh")
@Tag(name = "Panel SSH", description = "Panel SSH 连接管理接口")
public class PanelSshController {

    private final NginxPanelService nginxPanelService;

    @Operation(summary = "打开 SSH 连接")
    @PostMapping("/open")
    public ReturnResult<String> open(@RequestBody Map<String, Object> body) {
        String host = String.valueOf(body.getOrDefault("host", "127.0.0.1"));
        int port = Integer.parseInt(String.valueOf(body.getOrDefault("port", 22)));
        String username = String.valueOf(body.getOrDefault("username", "root"));
        String password = body.containsKey("password") ? String.valueOf(body.get("password")) : null;
        String privateKey = body.containsKey("privateKey") ? String.valueOf(body.get("privateKey")) : null;
        try {
            return ReturnResult.ok(nginxPanelService.open(host, port, username, password, privateKey));
        } catch (Exception e) {
            return ReturnResult.error("SSH 连接失败: " + e.getMessage());
        }
    }

    @Operation(summary = "关闭 SSH 连接")
    @DeleteMapping("/{connectionId}")
    public ReturnResult<Void> close(@PathVariable String connectionId) {
        try {
            nginxPanelService.close(connectionId);
            return ReturnResult.ok();
        } catch (Exception e) {
            return ReturnResult.error("关闭连接失败: " + e.getMessage());
        }
    }

    @Operation(summary = "在远程服务器上执行脚本")
    @PostMapping("/{connectionId}/execute-script")
    public ReturnResult<String> executeScript(
            @PathVariable String connectionId,
            @RequestBody Map<String, String> body) {
        String script = body.get("script");
        if (script == null || script.isBlank()) {
            return ReturnResult.error("脚本内容不能为空");
        }
        try {
            return ReturnResult.ok(nginxPanelService.executeScript(connectionId, script));
        } catch (Exception e) {
            return ReturnResult.error("脚本执行失败: " + e.getMessage());
        }
    }
}
