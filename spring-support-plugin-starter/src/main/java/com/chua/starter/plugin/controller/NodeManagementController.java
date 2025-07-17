package com.chua.starter.plugin.controller;

import com.chua.starter.plugin.entity.PluginNodeLoggerConfig;
import com.chua.starter.plugin.service.NodeLoggerConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 节点管理控制器
 * 
 * @author CH
 * @since 2025/1/16
 */
@Slf4j
@RestController
@RequestMapping("/api/plugin/nodes")
@RequiredArgsConstructor
public class NodeManagementController {

    private final NodeLoggerConfigService nodeLoggerConfigService;

    /**
     * 获取在线节点列表（模拟数据）
     */
    @GetMapping("/online")
    public ResponseEntity<Map<String, Object>> getOnlineNodes() {
        Map<String, Object> response = new HashMap<>();

        // 模拟在线节点数据
        List<Map<String, Object>> nodes = List.of(
                createNodeInfo("node-1", "http://localhost:8081", "user-service", true, "1.0.0"),
                createNodeInfo("node-2", "http://localhost:8082", "user-service", true, "1.0.0"),
                createNodeInfo("node-3", "http://localhost:8083", "order-service", true, "1.1.0"),
                createNodeInfo("node-4", "http://localhost:8084", "order-service", false, "1.1.0"),
                createNodeInfo("node-5", "http://localhost:8085", "payment-service", true, "2.0.0"));

        response.put("success", true);
        response.put("nodes", nodes);
        response.put("totalCount", nodes.size());
        response.put("onlineCount", nodes.stream().mapToLong(n -> (Boolean) n.get("online") ? 1 : 0).sum());

        return ResponseEntity.ok(response);
    }

    /**
     * 获取节点详细信息
     */
    @GetMapping("/{nodeId}")
    public ResponseEntity<Map<String, Object>> getNodeDetails(@PathVariable String nodeId) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 根据nodeId获取节点信息（这里使用模拟数据）
            Map<String, Object> nodeInfo = getNodeInfoById(nodeId);

            if (nodeInfo != null) {
                response.put("success", true);
                response.put("node", nodeInfo);

                // 添加日志配置信息
                String nodeUrl = (String) nodeInfo.get("url");
                List<PluginNodeLoggerConfig> loggers = nodeLoggerConfigService.getNodeLoggers(nodeUrl);
                response.put("loggers", loggers);
                response.put("loggerCount", loggers.size());
            } else {
                response.put("success", false);
                response.put("message", "节点不存在");
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get node details: {}", nodeId, e);

            response.put("success", false);
            response.put("message", "获取节点详情失败: " + e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取节点的日志配置弹出框数据
     */
    @GetMapping("/{nodeId}/logger-config")
    public ResponseEntity<Map<String, Object>> getNodeLoggerConfig(@PathVariable String nodeId) {
        Map<String, Object> response = new HashMap<>();

        try {
            // 获取节点信息
            Map<String, Object> nodeInfo = getNodeInfoById(nodeId);
            if (nodeInfo == null) {
                response.put("success", false);
                response.put("message", "节点不存在");
                return ResponseEntity.notFound().build();
            }

            String nodeUrl = (String) nodeInfo.get("url");
            String applicationName = (String) nodeInfo.get("applicationName");

            // 获取日志器配置
            List<PluginNodeLoggerConfig> loggers = nodeLoggerConfigService.getNodeLoggers(nodeUrl);

            // 获取相同应用的其他节点
            List<String> sameAppNodes = nodeLoggerConfigService.getNodesByApplicationName(applicationName);

            // 获取可用的日志等级
            List<PluginNodeLoggerConfig.LogLevel> availableLevels = PluginNodeLoggerConfig.getAllLogLevels();
            List<PluginNodeLoggerConfig.LogLevel> commonLevels = PluginNodeLoggerConfig.getCommonLogLevels();

            response.put("success", true);
            response.put("nodeInfo", nodeInfo);
            response.put("loggers", loggers);
            response.put("sameAppNodes", sameAppNodes);
            response.put("availableLevels", availableLevels);
            response.put("commonLevels", commonLevels);
            response.put("loggerCount", loggers.size());
            response.put("sameAppNodeCount", sameAppNodes.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get node logger config: {}", nodeId, e);

            response.put("success", false);
            response.put("message", "获取节点日志配置失败: " + e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 切换到相同应用的其他节点
     */
    @GetMapping("/{nodeId}/switch-node")
    public ResponseEntity<Map<String, Object>> switchToSameAppNode(@PathVariable String nodeId,
            @RequestParam String targetNodeUrl) {

        Map<String, Object> response = new HashMap<>();

        try {
            // 获取目标节点的日志配置
            List<PluginNodeLoggerConfig> loggers = nodeLoggerConfigService.getNodeLoggers(targetNodeUrl);

            // 获取目标节点信息
            Map<String, Object> targetNodeInfo = getNodeInfoByUrl(targetNodeUrl);

            response.put("success", true);
            response.put("targetNode", targetNodeInfo);
            response.put("loggers", loggers);
            response.put("loggerCount", loggers.size());
            response.put("message", "切换节点成功");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to switch to node: {} -> {}", nodeId, targetNodeUrl, e);

            response.put("success", false);
            response.put("message", "切换节点失败: " + e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 刷新节点状态
     */
    @PostMapping("/{nodeId}/refresh")
    public ResponseEntity<Map<String, Object>> refreshNodeStatus(@PathVariable String nodeId) {
        Map<String, Object> response = new HashMap<>();

        try {
            Map<String, Object> nodeInfo = getNodeInfoById(nodeId);
            if (nodeInfo == null) {
                response.put("success", false);
                response.put("message", "节点不存在");
                return ResponseEntity.notFound().build();
            }

            String nodeUrl = (String) nodeInfo.get("url");

            // 刷新日志器配置
            boolean refreshSuccess = nodeLoggerConfigService.refreshNodeLoggers(nodeUrl);

            if (refreshSuccess) {
                // 获取最新的日志器配置
                List<PluginNodeLoggerConfig> loggers = nodeLoggerConfigService.getNodeLoggers(nodeUrl);

                response.put("success", true);
                response.put("message", "节点状态刷新成功");
                response.put("nodeInfo", nodeInfo);
                response.put("loggers", loggers);
                response.put("loggerCount", loggers.size());
            } else {
                response.put("success", false);
                response.put("message", "节点状态刷新失败");
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to refresh node status: {}", nodeId, e);

            response.put("success", false);
            response.put("message", "刷新节点状态失败: " + e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 创建节点信息
     */
    private Map<String, Object> createNodeInfo(String nodeId, String url, String applicationName, boolean online,
            String version) {
        Map<String, Object> nodeInfo = new HashMap<>();
        nodeInfo.put("nodeId", nodeId);
        nodeInfo.put("url", url);
        nodeInfo.put("applicationName", applicationName);
        nodeInfo.put("online", online);
        nodeInfo.put("version", version);
        nodeInfo.put("lastHeartbeat", System.currentTimeMillis());
        nodeInfo.put("startTime", System.currentTimeMillis() - 3600000); // 1小时前启动

        // 添加一些额外的节点信息
        nodeInfo.put("host", url.replace("http://", "").split(":")[0]);
        nodeInfo.put("port", url.split(":")[2]);
        nodeInfo.put("contextPath", "/");
        nodeInfo.put("managementPort", Integer.parseInt(url.split(":")[2]) + 1000);

        return nodeInfo;
    }

    /**
     * 根据节点ID获取节点信息
     */
    private Map<String, Object> getNodeInfoById(String nodeId) {
        // 模拟数据查找
        Map<String, Map<String, Object>> nodeMap = Map.of("node-1",
                createNodeInfo("node-1", "http://localhost:8081", "user-service", true, "1.0.0"), "node-2",
                createNodeInfo("node-2", "http://localhost:8082", "user-service", true, "1.0.0"), "node-3",
                createNodeInfo("node-3", "http://localhost:8083", "order-service", true, "1.1.0"), "node-4",
                createNodeInfo("node-4", "http://localhost:8084", "order-service", false, "1.1.0"), "node-5",
                createNodeInfo("node-5", "http://localhost:8085", "payment-service", true, "2.0.0"));

        return nodeMap.get(nodeId);
    }

    /**
     * 根据节点URL获取节点信息
     */
    private Map<String, Object> getNodeInfoByUrl(String nodeUrl) {
        // 模拟根据URL查找节点信息
        Map<String, Map<String, Object>> urlMap = Map.of("http://localhost:8081",
                createNodeInfo("node-1", "http://localhost:8081", "user-service", true, "1.0.0"),
                "http://localhost:8082",
                createNodeInfo("node-2", "http://localhost:8082", "user-service", true, "1.0.0"),
                "http://localhost:8083",
                createNodeInfo("node-3", "http://localhost:8083", "order-service", true, "1.1.0"),
                "http://localhost:8084",
                createNodeInfo("node-4", "http://localhost:8084", "order-service", false, "1.1.0"),
                "http://localhost:8085",
                createNodeInfo("node-5", "http://localhost:8085", "payment-service", true, "2.0.0"));

        return urlMap.get(nodeUrl);
    }
}
