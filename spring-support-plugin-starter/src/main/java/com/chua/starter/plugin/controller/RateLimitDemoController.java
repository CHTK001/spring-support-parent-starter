package com.chua.starter.plugin.controller;

import com.chua.starter.plugin.annotation.RateLimit;
import com.chua.starter.plugin.entity.BlackWhiteList;
import com.chua.starter.plugin.entity.RateLimitConfig;
import com.chua.starter.plugin.service.BlackWhiteListService;
import com.chua.starter.plugin.service.RateLimitConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 限流演示控制器
 * 
 * @author CH
 * @since 2025/1/16
 */
@RestController
@RequestMapping("/api/rate-limit")
@RequiredArgsConstructor
@RateLimit(qps = 1000, description = "限流演示API整体限制")
public class RateLimitDemoController {

    private final RateLimitConfigService configService;
    private final BlackWhiteListService blackWhiteListService;

    /**
     * 高频接口 - 每秒10次
     */
    @GetMapping("/high-frequency")
    @RateLimit(qps = 10, description = "高频接口限制")
    public Map<String, Object> highFrequency() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "高频接口调用成功");
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 中频接口 - 每秒50次
     */
    @GetMapping("/medium-frequency")
    @RateLimit(qps = 50, burstCapacity = 100, description = "中频接口限制")
    public Map<String, Object> mediumFrequency() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "中频接口调用成功");
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 低频接口 - 每秒100次，同时限制IP
     */
    @GetMapping("/low-frequency")
    @RateLimit(qps = 100, limitIp = true, ipQps = 10, description = "低频接口限制，同时限制IP")
    public Map<String, Object> lowFrequency() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "低频接口调用成功");
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 创建数据接口 - POST请求限制
     */
    @PostMapping("/create")
    @RateLimit(qps = 20, algorithm = RateLimitConfig.AlgorithmType.TOKEN_BUCKET, description = "创建数据接口限制")
    public Map<String, Object> createData(@RequestBody Map<String, Object> data) {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "数据创建成功");
        result.put("data", data);
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 获取所有限流配置
     */
    @GetMapping("/configs")
    public List<RateLimitConfig> getAllConfigs() {
        return configService.getAllConfigs();
    }

    /**
     * 获取启用的限流配置
     */
    @GetMapping("/configs/enabled")
    public List<RateLimitConfig> getEnabledConfigs() {
        return configService.getEnabledConfigs();
    }

    /**
     * 更新限流配置的QPS
     */
    @PutMapping("/configs/{limitType}/{limitKey}/qps")
    public Map<String, Object> updateQps(@PathVariable String limitType, @PathVariable String limitKey,
            @RequestParam Integer qps) {

        RateLimitConfig.LimitType type = RateLimitConfig.LimitType.valueOf(limitType.toUpperCase());
        boolean success = configService.updateQps(type, limitKey, qps);

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "QPS更新成功" : "QPS更新失败");
        return result;
    }

    /**
     * 启用或禁用限流配置
     */
    @PutMapping("/configs/{limitType}/{limitKey}/enabled")
    public Map<String, Object> setEnabled(@PathVariable String limitType, @PathVariable String limitKey,
            @RequestParam Boolean enabled) {

        RateLimitConfig.LimitType type = RateLimitConfig.LimitType.valueOf(limitType.toUpperCase());
        boolean success = configService.setEnabled(type, limitKey, enabled);

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "状态更新成功" : "状态更新失败");
        return result;
    }

    /**
     * 重新加载配置缓存
     */
    @PostMapping("/reload-cache")
    public Map<String, Object> reloadCache() {
        configService.reloadCache();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "缓存重新加载成功");
        result.put("stats", configService.getCacheStats());
        return result;
    }

    /**
     * 获取缓存统计信息
     */
    @GetMapping("/cache-stats")
    public Map<String, Object> getCacheStats() {
        Map<String, Object> result = new HashMap<>();
        result.put("stats", configService.getCacheStats());
        result.put("blackWhiteListStats", blackWhiteListService.getCacheStats());
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    // ==================== 黑白名单管理接口 ====================

    /**
     * 获取所有黑名单
     */
    @GetMapping("/blacklist")
    public List<BlackWhiteList> getAllBlacklist() {
        return blackWhiteListService.getAllBlacklist();
    }

    /**
     * 获取所有白名单
     */
    @GetMapping("/whitelist")
    public List<BlackWhiteList> getAllWhitelist() {
        return blackWhiteListService.getAllWhitelist();
    }

    /**
     * 添加到黑名单
     */
    @PostMapping("/blacklist")
    public Map<String, Object> addToBlacklist(@RequestParam String value,
            @RequestParam(defaultValue = "EXACT") String matchType,
            @RequestParam(required = false) String description) {

        BlackWhiteList.MatchType type = BlackWhiteList.MatchType.valueOf(matchType.toUpperCase());
        BlackWhiteList item = blackWhiteListService.addToBlacklist(value, type, description);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "已添加到黑名单");
        result.put("data", item);
        return result;
    }

    /**
     * 添加到白名单
     */
    @PostMapping("/whitelist")
    public Map<String, Object> addToWhitelist(@RequestParam String value,
            @RequestParam(defaultValue = "EXACT") String matchType,
            @RequestParam(required = false) String description) {

        BlackWhiteList.MatchType type = BlackWhiteList.MatchType.valueOf(matchType.toUpperCase());
        BlackWhiteList item = blackWhiteListService.addToWhitelist(value, type, description);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "已添加到白名单");
        result.put("data", item);
        return result;
    }

    /**
     * 从黑名单移除
     */
    @DeleteMapping("/blacklist")
    public Map<String, Object> removeFromBlacklist(@RequestParam String value) {
        boolean success = blackWhiteListService.removeFromBlacklist(value);

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "已从黑名单移除" : "黑名单中未找到该项");
        return result;
    }

    /**
     * 从白名单移除
     */
    @DeleteMapping("/whitelist")
    public Map<String, Object> removeFromWhitelist(@RequestParam String value) {
        boolean success = blackWhiteListService.removeFromWhitelist(value);

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "已从白名单移除" : "白名单中未找到该项");
        return result;
    }

    /**
     * 检查访问权限
     */
    @GetMapping("/check-access")
    public Map<String, Object> checkAccess(@RequestParam String value) {
        boolean isBlacklisted = blackWhiteListService.isBlacklisted(value);
        boolean isWhitelisted = blackWhiteListService.isWhitelisted(value);
        boolean isAllowed = blackWhiteListService.isAccessAllowed(value);

        Map<String, Object> result = new HashMap<>();
        result.put("value", value);
        result.put("isBlacklisted", isBlacklisted);
        result.put("isWhitelisted", isWhitelisted);
        result.put("isAllowed", isAllowed);
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 启用/禁用黑白名单条目
     */
    @PutMapping("/blackwhitelist/{id}/enabled")
    public Map<String, Object> setBlackWhiteListEnabled(@PathVariable Long id, @RequestParam Boolean enabled) {

        boolean success = blackWhiteListService.setEnabled(id, enabled);

        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("message", success ? "状态更新成功" : "状态更新失败");
        return result;
    }

    /**
     * 清理过期的黑白名单条目
     */
    @PostMapping("/blackwhitelist/cleanup")
    public Map<String, Object> cleanupExpiredBlackWhiteList() {
        int cleaned = blackWhiteListService.cleanupExpired();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "清理完成");
        result.put("cleanedCount", cleaned);
        return result;
    }

    /**
     * 重新加载黑白名单缓存
     */
    @PostMapping("/blackwhitelist/reload-cache")
    public Map<String, Object> reloadBlackWhiteListCache() {
        blackWhiteListService.reloadCache();

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "黑白名单缓存重新加载成功");
        result.put("stats", blackWhiteListService.getCacheStats());
        return result;
    }
}
