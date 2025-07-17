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
import java.util.Optional;

/**
 * 日志配置控制器
 * 
 * @author CH
 * @since 2025/1/16
 */
@Slf4j
@RestController
@RequestMapping("/api/plugin/logger")
@RequiredArgsConstructor
public class LoggerConfigController {

    private final NodeLoggerConfigService nodeLoggerConfigService;

    /**
     * 获取节点的所有日志器配置
     */
    @GetMapping("/nodes/{nodeUrl}/loggers")
    public ResponseEntity<Map<String, Object>> getNodeLoggers(@PathVariable String nodeUrl) {
        try {
            // URL解码
            String decodedNodeUrl = java.net.URLDecoder.decode(nodeUrl, "UTF-8");

            List<PluginLoggerConfig> loggers = loggerConfigService.getNodeLoggers(decodedNodeUrl);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("nodeUrl", decodedNodeUrl);
            response.put("loggers", loggers);
            response.put("count", loggers.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get node loggers: {}", nodeUrl, e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取日志器配置失败: " + e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取指定日志器的详细配置
     */
    @GetMapping("/nodes/{nodeUrl}/loggers/{loggerName}")
    public ResponseEntity<Map<String, Object>> getLoggerConfig(@PathVariable String nodeUrl,
            @PathVariable String loggerName) {

        try {
            String decodedNodeUrl = java.net.URLDecoder.decode(nodeUrl, "UTF-8");
            String decodedLoggerName = java.net.URLDecoder.decode(loggerName, "UTF-8");

            Optional<PluginLoggerConfig> config = loggerConfigService.getLoggerConfig(decodedNodeUrl,
                    decodedLoggerName);
            Map<String, Object> details = loggerConfigService.getLoggerDetails(decodedNodeUrl, decodedLoggerName);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("nodeUrl", decodedNodeUrl);
            response.put("loggerName", decodedLoggerName);
            response.put("config", config.orElse(null));
            response.put("details", details);
            response.put("availableLevels", PluginLoggerConfig.getAllLogLevels());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get logger config: {} - {}", nodeUrl, loggerName, e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取日志器详细配置失败: " + e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 设置日志器等级
     */
    @PostMapping("/nodes/{nodeUrl}/loggers/{loggerName}/level")
    public ResponseEntity<Map<String, Object>> setLoggerLevel(@PathVariable String nodeUrl,
            @PathVariable String loggerName, @RequestParam PluginLoggerConfig.LogLevel level) {

        Map<String, Object> response = new HashMap<>();

        try {
            String decodedNodeUrl = java.net.URLDecoder.decode(nodeUrl, "UTF-8");
            String decodedLoggerName = java.net.URLDecoder.decode(loggerName, "UTF-8");

            boolean success = loggerConfigService.setLoggerLevel(decodedNodeUrl, decodedLoggerName, level);

            if (success) {
                response.put("success", true);
                response.put("message", "日志等级设置成功");
                response.put("nodeUrl", decodedNodeUrl);
                response.put("loggerName", decodedLoggerName);
                response.put("level", level);
            } else {
                response.put("success", false);
                response.put("message", "日志等级设置失败");
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to set logger level: {} - {} -> {}", nodeUrl, loggerName, level, e);

            response.put("success", false);
            response.put("message", "设置日志等级失败: " + e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 批量设置相同应用的所有节点日志等级
     */
    @PostMapping("/applications/{applicationName}/loggers/{loggerName}/level")
    public ResponseEntity<Map<String, Object>> setLoggerLevelForAllNodes(@PathVariable String applicationName,
            @PathVariable String loggerName, @RequestParam PluginLoggerConfig.LogLevel level) {

        Map<String, Object> response = new HashMap<>();

        try {
            String decodedAppName = java.net.URLDecoder.decode(applicationName, "UTF-8");
            String decodedLoggerName = java.net.URLDecoder.decode(loggerName, "UTF-8");

            Map<String, Boolean> results = loggerConfigService.setLoggerLevelForAllNodes(decodedAppName,
                    decodedLoggerName, level);

            long successCount = results.values().stream().mapToLong(b -> b ? 1 : 0).sum();

            response.put("success", true);
            response.put("message",
                    String.format("批量设置完成，成功: %d, 失败: %d", successCount, results.size() - successCount));
            response.put("applicationName", decodedAppName);
            response.put("loggerName", decodedLoggerName);
            response.put("level", level);
            response.put("results", results);
            response.put("totalNodes", results.size());
            response.put("successCount", successCount);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to set logger level for all nodes: {} - {} -> {}", applicationName, loggerName, level, e);

            response.put("success", false);
            response.put("message", "批量设置日志等级失败: " + e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取相同应用名称的所有节点
     */
    @GetMapping("/applications/{applicationName}/nodes")
    public ResponseEntity<Map<String, Object>> getNodesByApplicationName(@PathVariable String applicationName) {
        try {
            String decodedAppName = java.net.URLDecoder.decode(applicationName, "UTF-8");

            List<String> nodeUrls = loggerConfigService.getNodesByApplicationName(decodedAppName);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("applicationName", decodedAppName);
            response.put("nodeUrls", nodeUrls);
            response.put("count", nodeUrls.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get nodes by application name: {}", applicationName, e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取应用节点失败: " + e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 刷新节点的日志器配置
     */
    @PostMapping("/nodes/{nodeUrl}/refresh")
    public ResponseEntity<Map<String, Object>> refreshNodeLoggers(@PathVariable String nodeUrl) {
        Map<String, Object> response = new HashMap<>();

        try {
            String decodedNodeUrl = java.net.URLDecoder.decode(nodeUrl, "UTF-8");

            boolean success = loggerConfigService.refreshNodeLoggers(decodedNodeUrl);

            if (success) {
                response.put("success", true);
                response.put("message", "日志器配置刷新成功");
                response.put("nodeUrl", decodedNodeUrl);
            } else {
                response.put("success", false);
                response.put("message", "日志器配置刷新失败");
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to refresh node loggers: {}", nodeUrl, e);

            response.put("success", false);
            response.put("message", "刷新日志器配置失败: " + e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取所有应用名称
     */
    @GetMapping("/applications")
    public ResponseEntity<Map<String, Object>> getAllApplicationNames() {
        try {
            List<String> applicationNames = loggerConfigService.getAllApplicationNames();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("applicationNames", applicationNames);
            response.put("count", applicationNames.size());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get all application names", e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取应用名称列表失败: " + e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取所有可用的日志等级
     */
    @GetMapping("/levels")
    public ResponseEntity<Map<String, Object>> getAllLogLevels() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("allLevels", PluginLoggerConfig.getAllLogLevels());
        response.put("commonLevels", PluginLoggerConfig.getCommonLogLevels());

        return ResponseEntity.ok(response);
    }

    /**
     * 获取日志配置统计信息
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getLoggerStatistics() {
        try {
            // 这里可以添加统计逻辑
            Map<String, Object> statistics = new HashMap<>();
            statistics.put("totalApplications", loggerConfigService.getAllApplicationNames().size());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("statistics", statistics);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to get logger statistics", e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "获取统计信息失败: " + e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }
}
