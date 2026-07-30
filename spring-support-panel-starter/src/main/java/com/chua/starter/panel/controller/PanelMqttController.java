package com.chua.starter.panel.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.panel.service.MqttPanelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MQTT 面板控制器。
 *
 * @author CH
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/panel/mqtt")
public class PanelMqttController {

    private final MqttPanelService mqttPanelService;

    // ─── 连接管理 ────────────────────────────────────────────────────────

    @PostMapping("/open")
    public ReturnResult<String> open(@RequestBody Map<String, Object> body) {
        String host = String.valueOf(body.getOrDefault("host", "127.0.0.1"));
        int port = Integer.parseInt(String.valueOf(body.getOrDefault("port", 1883)));
        String username = body.containsKey("username") ? String.valueOf(body.get("username")) : null;
        String password = body.containsKey("password") ? String.valueOf(body.get("password")) : null;
        String clientId = body.containsKey("clientId") ? String.valueOf(body.get("clientId")) : null;
        try {
            return ReturnResult.ok(mqttPanelService.open(host, port, username, password, clientId));
        } catch (Exception e) {
            return ReturnResult.error("MQTT 连接失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{connectionId}")
    public ReturnResult<Void> close(@PathVariable String connectionId) {
        mqttPanelService.close(connectionId);
        return ReturnResult.ok(null);
    }

    // ─── Broker 信息 ─────────────────────────────────────────────────────

    @GetMapping("/{connectionId}/info")
    public ReturnResult<Map<String, Object>> brokerInfo(@PathVariable String connectionId) {
        try {
            return ReturnResult.ok(mqttPanelService.brokerInfo(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("查询 Broker 信息失败: " + e.getMessage());
        }
    }

    // ─── 发布消息 ─────────────────────────────────────────────────────────

    @PostMapping("/{connectionId}/publish")
    public ReturnResult<Boolean> publish(
            @PathVariable String connectionId,
            @RequestBody Map<String, Object> body) {
        String topic = String.valueOf(body.getOrDefault("topic", ""));
        String payload = String.valueOf(body.getOrDefault("payload", ""));
        int qos = Integer.parseInt(String.valueOf(body.getOrDefault("qos", 0)));
        try {
            return ReturnResult.ok(mqttPanelService.publish(connectionId, topic, payload, qos));
        } catch (Exception e) {
            return ReturnResult.error("发布消息失败: " + e.getMessage());
        }
    }

    // ─── 订阅管理 ─────────────────────────────────────────────────────────

    @PostMapping("/{connectionId}/subscribe")
    public ReturnResult<Boolean> subscribe(
            @PathVariable String connectionId,
            @RequestBody Map<String, Object> body) {
        String topic = String.valueOf(body.getOrDefault("topic", ""));
        int qos = Integer.parseInt(String.valueOf(body.getOrDefault("qos", 1)));
        try {
            return ReturnResult.ok(mqttPanelService.subscribe(connectionId, topic, qos));
        } catch (Exception e) {
            return ReturnResult.error("订阅失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{connectionId}/subscribe/{topic}")
    public ReturnResult<Boolean> unsubscribe(
            @PathVariable String connectionId,
            @PathVariable String topic) {
        try {
            return ReturnResult.ok(mqttPanelService.unsubscribe(connectionId, topic));
        } catch (Exception e) {
            return ReturnResult.error("取消订阅失败: " + e.getMessage());
        }
    }

    @GetMapping("/{connectionId}/subscriptions")
    public ReturnResult<List<Map<String, Object>>> listSubscriptions(@PathVariable String connectionId) {
        try {
            return ReturnResult.ok(mqttPanelService.listSubscriptions(connectionId));
        } catch (Exception e) {
            return ReturnResult.error("查询订阅列表失败: " + e.getMessage());
        }
    }

    // ─── 消息历史 ─────────────────────────────────────────────────────────

    @GetMapping("/{connectionId}/messages")
    public ReturnResult<List<Map<String, Object>>> messages(
            @PathVariable String connectionId,
            @RequestParam(required = false) String topic,
            @RequestParam(defaultValue = "50") int limit) {
        try {
            return ReturnResult.ok(mqttPanelService.messages(connectionId, topic, limit));
        } catch (Exception e) {
            return ReturnResult.error("查询消息失败: " + e.getMessage());
        }
    }
}
