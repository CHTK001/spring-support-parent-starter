package com.chua.starter.panel.controller;

import com.chua.common.support.lang.code.ReturnResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 面板系统配置接口
 * <p>提供前端所需运行时配置（Socket.IO 连接地址等），
 * 避免前端硬编码端口</p>
 *
 * @author CH
 */
@Tag(name = "面板系统配置")
@RestController
@RequestMapping("/v1/panel/system/config")
public class PanelSystemConfigController {

    @Value("${plugin.socket.room[0].port:58082}")
    private int socketPort;

    @Value("${plugin.socket.room[0].context-path:/socket.io}")
    private String socketContextPath;

    @Value("${plugin.socket.room[0].host:}")
    private String socketHost;

    @Operation(summary = "获取 Socket.IO 配置")
    @GetMapping("/socket")
    public ReturnResult<List<Map<String, Object>>> getSocketConfig() {
        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("clientId", "panel");
        item.put("host", socketHost.isEmpty() ? "localhost" : socketHost);
        item.put("port", socketPort);
        item.put("contextPath", socketContextPath);
        item.put("enable", true);
        result.add(item);
        return ReturnResult.ok(result);
    }
}
