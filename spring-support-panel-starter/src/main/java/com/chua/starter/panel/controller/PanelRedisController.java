package com.chua.starter.panel.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.panel.model.*;
import com.chua.starter.panel.service.RedisPanelService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Redis 面板控制器。
 *
 * @author CH
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/panel/redis")
public class PanelRedisController {

    private final RedisPanelService redisPanelService;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 打开连接 */
    @PostMapping("/open")
    public ReturnResult<String> open(@RequestBody Map<String, Object> body) {
        String host = String.valueOf(body.getOrDefault("host", "127.0.0.1"));
        int port = Integer.parseInt(String.valueOf(body.getOrDefault("port", 6379)));
        String password = body.containsKey("password") ? String.valueOf(body.get("password")) : null;
        int db = Integer.parseInt(String.valueOf(body.getOrDefault("db", 0)));
        try {
            return ReturnResult.ok(redisPanelService.open(host, port, password, db));
        } catch (Exception e) {
            return ReturnResult.error("Redis 连接失败: " + e.getMessage());
        }
    }

    /** 关闭连接 */
    @DeleteMapping("/{connectionId}")
    public ReturnResult<Void> close(@PathVariable String connectionId) {
        redisPanelService.close(connectionId);
        return ReturnResult.ok(null);
    }

    /** 服务器信息 */
    @GetMapping("/{connectionId}/info")
    public ReturnResult<PanelRedisInfoView> info(@PathVariable String connectionId) {
        return ReturnResult.ok(redisPanelService.info(connectionId));
    }

    /** 切换 DB */
    @PostMapping("/{connectionId}/select/{db}")
    public ReturnResult<Void> selectDb(@PathVariable String connectionId, @PathVariable int db) {
        redisPanelService.selectDb(connectionId, db);
        return ReturnResult.ok(null);
    }

    /** 扫描 keys */
    @GetMapping("/{connectionId}/scan")
    public ReturnResult<List<PanelRedisKeyView>> scan(
            @PathVariable String connectionId,
            @RequestParam(defaultValue = "*") String pattern,
            @RequestParam(defaultValue = "200") int count) {
        return ReturnResult.ok(redisPanelService.scan(connectionId, pattern, count));
    }

    /** 获取 key 值 */
    @GetMapping("/{connectionId}/key")
    public ReturnResult<PanelRedisValueView> get(
            @PathVariable String connectionId,
            @RequestParam String key) {
        return ReturnResult.ok(redisPanelService.get(connectionId, key));
    }

    /** SET 字符串值 */
    @PostMapping("/{connectionId}/set")
    public ReturnResult<Void> set(
            @PathVariable String connectionId,
            @RequestBody PanelRedisSetRequest request) {
        redisPanelService.set(connectionId, request);
        return ReturnResult.ok(null);
    }

    /** 删除 key */
    @DeleteMapping("/{connectionId}/key")
    public ReturnResult<Long> del(
            @PathVariable String connectionId,
            @RequestParam String key) {
        return ReturnResult.ok(redisPanelService.del(connectionId, key));
    }

    /** 设置 TTL */
    @PostMapping("/{connectionId}/expire")
    public ReturnResult<Boolean> expire(
            @PathVariable String connectionId,
            @RequestParam String key,
            @RequestParam long seconds) {
        return ReturnResult.ok(redisPanelService.expire(connectionId, key, seconds));
    }

    /**
     * 执行命令。
     * <p>
     * 兼容两种请求体格式：
     * 1) 纯文本命令：{@code HSET key field value}
     * 2) JSON 对象：{@code {"command":"HSET key field value"}}
     *
     * @param connectionId 连接标识
     * @param commandRaw 请求体原始内容
     * @return 执行结果
     */
    @PostMapping("/{connectionId}/execute")
    public ReturnResult<Object> execute(
            @PathVariable String connectionId,
            @RequestBody String commandRaw) {
        return ReturnResult.ok(redisPanelService.execute(connectionId, resolveCommand(commandRaw)));
    }

    /**
     * 解析 Redis 命令文本。
     *
     * @param commandRaw 请求体原始内容
     * @return 命令文本
     */
    private String resolveCommand(String commandRaw) {
        if (commandRaw == null) {
            return "";
        }
        String trimmed = commandRaw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                Map<String, Object> body = OBJECT_MAPPER.readValue(trimmed, new TypeReference<>() {
                });
                Object command = body.get("command");
                if (command != null) {
                    return String.valueOf(command).trim();
                }
            } catch (Exception ignored) {
                // JSON 解析失败时，回落为原始字符串，兼容文本请求体。
            }
        }
        return trimmed;
    }
}
