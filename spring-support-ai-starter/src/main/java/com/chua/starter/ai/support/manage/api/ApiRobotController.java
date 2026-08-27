package com.chua.starter.ai.support.manage.api;

import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.manage.store.SysAiConversationStore;
import com.chua.starter.ai.support.manage.ApiWebSocketPushService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 机器人（Robot）控制器，替代 nomicore 的 {@code /api/robots} 契约。
 *
 * <p>维护设备档案（{@code robot_id} 主键）与在线状态，关键状态变更推送
 * {@code robot.status} 事件。当前为进程内内存实现。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@RestController
@RequestMapping("/api/robots")
@RequiredArgsConstructor
@Slf4j
public class ApiRobotController {

    /**
     * 主键生成与基础存储工具
     */
    private final SysAiConversationStore conversationStore;

    /**
     * WS 事件推送服务
     */
    private final ApiWebSocketPushService pushService;

    /**
     * 设备档案：robot_id → 设备
     */
    private final Map<String, Map<String, Object>> robots = new ConcurrentHashMap<>();

    /**
     * 设备状态快照：robot_id → 状态
     */
    private final Map<String, Map<String, Object>> robotStatus = new ConcurrentHashMap<>();

    @GetMapping
    public ApiResult<Map<String, Object>> list() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("robots", new ArrayList<>(robots.values()));
        return ApiResult.ok(payload);
    }

    @PostMapping("/claim")
    public ApiResult<Map<String, Object>> claim(@RequestBody Map<String, Object> body) {
        String code = (String) body.get("code");
        String companionId = (String) body.get("companion_id");
        if (code == null || code.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_PARAM, "设备码不能为空");
        }
        String robotId = conversationStore.newId();
        Map<String, Object> robot = new LinkedHashMap<>();
        robot.put("robot_id", robotId);
        robot.put("name", (String) body.getOrDefault("name", "设备-" + code));
        robot.put("companion_id", companionId);
        robot.put("board", (String) body.getOrDefault("board", "esp32-s3n16r8-emoji"));
        robot.put("firmware_version", (String) body.getOrDefault("firmware_version", "1.0.0"));
        robot.put("last_seen", null);
        robot.put("created_at", System.currentTimeMillis());
        robots.put(robotId, robot);
        updateStatus(robotId, companionId, "claimed");
        return ApiResult.ok(robot);
    }

    @PatchMapping("/{robotId}")
    public ApiResult<Map<String, Object>> update(@PathVariable String robotId,
                                                 @RequestBody Map<String, Object> body) {
        Map<String, Object> robot = requireRobot(robotId);
        if (body.containsKey("name")) {
            robot.put("name", body.get("name"));
        }
        if (body.containsKey("companion_id")) {
            Object companionId = body.get("companion_id");
            robot.put("companion_id", companionId);
            updateStatus(robotId, companionId == null ? null : companionId.toString(), "updated");
        }
        return ApiResult.ok(robot);
    }

    @DeleteMapping("/{robotId}")
    public ApiResult<Void> remove(@PathVariable String robotId) {
        requireRobot(robotId);
        robots.remove(robotId);
        robotStatus.remove(robotId);
        return ApiResult.ok();
    }

    @GetMapping("/statuses")
    public ApiResult<Map<String, Object>> statuses() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("statuses", new ArrayList<>(robotStatus.values()));
        return ApiResult.ok(payload);
    }

    @GetMapping("/endpoints")
    public ApiResult<Map<String, Object>> endpoints() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ota_urls", new ArrayList<String>());
        payload.put("lan_enabled", false);
        return ApiResult.ok(payload);
    }

    private void updateStatus(String robotId, String companionId, String phase) {
        Map<String, Object> status = robotStatus.computeIfAbsent(robotId, k -> new LinkedHashMap<>());
        status.put("robot_id", robotId);
        status.put("companion_id", companionId);
        status.put("phase", phase);
        status.put("changed_at", System.currentTimeMillis());
        pushService.push("robot.status", status);
    }

    private Map<String, Object> requireRobot(String robotId) {
        Map<String, Object> robot = robots.get(robotId);
        if (robot == null) {
            throw new ApiException(ApiErrorCode.NOT_FOUND, "设备不存在: " + robotId);
        }
        return robot;
    }
}
