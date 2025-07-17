package com.chua.starter.plugin.controller;

import com.chua.starter.plugin.entity.PluginXssAttackLog;
import com.chua.starter.plugin.entity.PluginXssConfig;
import com.chua.starter.plugin.service.XssAttackLogService;
import com.chua.starter.plugin.service.XssConfigService;
import com.chua.starter.plugin.store.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * XSS防护控制器
 * 
 * @author CH
 * @since 2025/1/16
 */
@Slf4j
@RestController
@RequestMapping("/api/plugin/xss")
@RequiredArgsConstructor
public class XssController {

    private final XssConfigService xssConfigService;
    private final XssAttackLogService xssAttackLogService;

    /**
     * 获取所有XSS配置
     */
    @GetMapping("/configs")
    public ResponseEntity<List<PluginXssConfig>> getAllConfigs() {
        try {
            List<PluginXssConfig> configs = xssConfigService.getAllConfigs();
            return ResponseEntity.ok(configs);
        } catch (Exception e) {
            log.error("Failed to get XSS configs", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 根据配置名称获取配置
     */
    @GetMapping("/configs/{configName}")
    public ResponseEntity<PluginXssConfig> getConfigByName(@PathVariable String configName) {
        try {
            Optional<PluginXssConfig> config = xssConfigService.getConfigByName(configName);
            return config.map(ResponseEntity::ok)
                        .orElse(ResponseEntity.notFound().build());
        } catch (Exception e) {
            log.error("Failed to get XSS config: {}", configName, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 创建或更新XSS配置
     */
    @PostMapping("/configs")
    public ResponseEntity<Map<String, Object>> saveConfig(
            @RequestBody PluginXssConfig config, 
            BindingResult bindingResult) {
        
        Map<String, Object> response = new HashMap<>();
        
        if (bindingResult.hasErrors()) {
            response.put("success", false);
            response.put("message", "参数验证失败");
            response.put("errors", bindingResult.getAllErrors());
            return ResponseEntity.badRequest().body(response);
        }

        try {
            PluginXssConfig savedConfig = xssConfigService.saveConfig(config);
            response.put("success", true);
            response.put("message", "XSS配置保存成功");
            response.put("data", savedConfig);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to save XSS config", e);
            response.put("success", false);
            response.put("message", "保存失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 删除XSS配置
     */
    @DeleteMapping("/configs/{configName}")
    public ResponseEntity<Map<String, Object>> deleteConfig(@PathVariable String configName) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean deleted = xssConfigService.deleteConfig(configName);
            if (deleted) {
                response.put("success", true);
                response.put("message", "XSS配置删除成功");
            } else {
                response.put("success", false);
                response.put("message", "配置不存在");
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to delete XSS config: {}", configName, e);
            response.put("success", false);
            response.put("message", "删除失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 启用或禁用XSS配置
     */
    @PutMapping("/configs/{configName}/enabled")
    public ResponseEntity<Map<String, Object>> setConfigEnabled(
            @PathVariable String configName,
            @RequestParam boolean enabled) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean updated = xssConfigService.setEnabled(configName, enabled);
            if (updated) {
                response.put("success", true);
                response.put("message", enabled ? "XSS配置已启用" : "XSS配置已禁用");
            } else {
                response.put("success", false);
                response.put("message", "配置不存在");
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to set XSS config enabled status: {}", configName, e);
            response.put("success", false);
            response.put("message", "操作失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 更新防护模式
     */
    @PutMapping("/configs/{configName}/protection-mode")
    public ResponseEntity<Map<String, Object>> updateProtectionMode(
            @PathVariable String configName,
            @RequestParam PluginXssConfig.ProtectionMode protectionMode) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            boolean updated = xssConfigService.updateProtectionMode(configName, protectionMode);
            if (updated) {
                response.put("success", true);
                response.put("message", "防护模式更新成功");
            } else {
                response.put("success", false);
                response.put("message", "配置不存在");
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to update protection mode for XSS config: {}", configName, e);
            response.put("success", false);
            response.put("message", "更新失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 创建默认配置
     */
    @PostMapping("/configs/default")
    public ResponseEntity<Map<String, Object>> createDefaultConfig() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            PluginXssConfig defaultConfig = xssConfigService.createDefaultConfig();
            response.put("success", true);
            response.put("message", "默认配置创建成功");
            response.put("data", defaultConfig);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to create default XSS config", e);
            response.put("success", false);
            response.put("message", "创建失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 重新加载配置缓存
     */
    @PostMapping("/configs/reload")
    public ResponseEntity<Map<String, Object>> reloadConfigCache() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            xssConfigService.reloadConfigCache();
            response.put("success", true);
            response.put("message", "配置缓存重新加载成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to reload XSS config cache", e);
            response.put("success", false);
            response.put("message", "重新加载失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 获取配置统计信息
     */
    @GetMapping("/configs/statistics")
    public ResponseEntity<XssConfigService.ConfigStatistics> getConfigStatistics() {
        try {
            XssConfigService.ConfigStatistics statistics = xssConfigService.getConfigStatistics();
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("Failed to get XSS config statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取攻击日志列表（分页）
     */
    @GetMapping("/attack-logs")
    public ResponseEntity<PageResult<PluginXssAttackLog>> getAttackLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String attackerIp,
            @RequestParam(required = false) PluginXssAttackLog.RiskLevel riskLevel) {
        
        try {
            PageResult<PluginXssAttackLog> result = xssAttackLogService.getAttackLogs(
                page, size, attackerIp, riskLevel);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to get XSS attack logs", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取攻击统计信息
     */
    @GetMapping("/attack-logs/statistics")
    public ResponseEntity<Map<String, Object>> getAttackStatistics() {
        try {
            Map<String, Object> statistics = xssAttackLogService.getAttackStatistics();
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            log.error("Failed to get XSS attack statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 清理过期攻击日志
     */
    @DeleteMapping("/attack-logs/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupExpiredLogs(
            @RequestParam(defaultValue = "30") int daysToKeep) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            int deletedCount = xssAttackLogService.cleanupExpiredLogs(daysToKeep);
            response.put("success", true);
            response.put("message", "清理完成");
            response.put("deletedCount", deletedCount);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Failed to cleanup expired XSS attack logs", e);
            response.put("success", false);
            response.put("message", "清理失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
