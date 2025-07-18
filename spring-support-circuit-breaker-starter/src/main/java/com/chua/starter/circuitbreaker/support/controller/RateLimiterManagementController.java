package com.chua.starter.circuitbreaker.support.controller;

import com.chua.starter.circuitbreaker.support.metrics.RateLimiterMetrics;
import com.chua.starter.circuitbreaker.support.properties.CircuitBreakerProperties;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.oauth.CurrentUser;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 限流管理控制器
 *
 * 提供限流器的动态管理接口，包括查看状态、创建、删除、重置等功能。
 * 同时提供Web管理界面，集成用户认证服务进行权限验证和操作记录。
 *
 * @author CH
 * @since 2024/12/20
 */
@Slf4j
@RestController
@RequestMapping("/actuator/rate-limiter")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.circuit-breaker", name = "enable", havingValue = "true", matchIfMissing = true)
public class RateLimiterManagementController {

    private final RateLimiterRegistry rateLimiterRegistry;
    private final CircuitBreakerProperties properties;

    @Autowired(required = false)
    private RateLimiterMetrics rateLimiterMetrics;

    /**
     * 认证服务 - 用于获取当前登录用户的账号信息
     * 提供用户身份验证和权限检查功能，在限流器管理操作中记录操作用户和进行权限验证
     */
    @Autowired(required = false)
    private AuthService authService;

    /**
     * 获取当前用户信息的辅助方法
     *
     * @return 包含用户名和用户ID的数组，[username, userId]
     */
    private String[] getCurrentUserInfo() {
        String username = "系统";
        String userId = "unknown";

        if (authService != null) {
            try {
                CurrentUser currentUser = authService.getCurrentUser();
                if (null != currentUser) {
                    String authUsername = currentUser.getUsername();
                    String authUserId = currentUser.getUserId();
                    username = authUsername != null ? authUsername : "匿名用户";
                    userId = authUserId != null ? authUserId : "unknown";
                }
            } catch (Exception e) {
                log.warn("获取当前用户信息失败: {}", e.getMessage());
            }
        }

        return new String[]{username, userId};
    }

    /**
     * 检查用户权限的辅助方法
     *
     * @param operation 操作类型
     * @return 是否有权限
     */
    private boolean checkPermission(String operation) {
        if (authService == null) {
            return true; // 如果没有认证服务，默认允许
        }

        try {
            // 检查是否已认证
            if (!authService.isAuthenticated()) {
                log.warn("用户未认证，拒绝执行操作: {}", operation);
                return false;
            }

            // 可以根据需要添加更细粒度的权限检查
            // 例如：检查是否有管理员角色
            // return authService.hasRole("ADMIN") || authService.hasPermission("RATE_LIMITER_MANAGE");

            return true;
        } catch (Exception e) {
            log.warn("权限检查失败，操作: {}, 错误: {}", operation, e.getMessage());
            return false;
        }
    }

    /**
     * 获取管理页面
     */
    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public String getManagementPage() {
        return generateManagementPage();
    }

    /**
     * 获取所有限流器状态
     */
    @GetMapping("/status")
    public Map<String, Object> getAllRateLimitersStatus() {
        Map<String, Object> result = new HashMap<>();

        Set<RateLimiter> rateLimiters = rateLimiterRegistry.getAllRateLimiters();
        result.put("rateLimiters", rateLimiters);
        result.put("totalCount", rateLimiters.size());
        result.put("timestamp", System.currentTimeMillis());

        // 添加指标统计信息
        if (rateLimiterMetrics != null) {
            RateLimiterMetrics.MetricsStatistics statistics = rateLimiterMetrics.getStatistics();
            result.put("metrics", statistics);
        }

        return result;
    }

    /**
     * 获取指定限流器状态
     */
    @GetMapping("/{name}/status")
    public Map<String, Object> getRateLimiterStatus(@PathVariable String name) {
        RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(name);
        return getRateLimiterInfo(rateLimiter);
    }

    /**
     * 创建新的限流器
     */
    @PostMapping("/{name}")
    public Map<String, Object> createRateLimiter(
            @PathVariable String name,
            @RequestBody Map<String, Object> config) {

        String[] userInfo = getCurrentUserInfo();
        Map<String, Object> result = new HashMap<>();

        // 权限检查
        if (!checkPermission("CREATE_RATE_LIMITER")) {
            result.put("success", false);
            result.put("message", "权限不足，无法创建限流器");
            result.put("operator", userInfo[0]);
            log.warn("创建限流器权限不足 - 限流器: {}, 操作用户: {} (ID: {})", name, userInfo[0], userInfo[1]);
            return result;
        }

        try {
            RateLimiterConfig rateLimiterConfig = buildRateLimiterConfig(config);
            RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(name, rateLimiterConfig);

            result.put("success", true);
            result.put("message", "限流器创建成功");
            result.put("rateLimiter", getRateLimiterInfo(rateLimiter));
            result.put("operator", userInfo[0]);

            log.info("创建限流器成功 - 限流器: {}, 配置: {}, 操作用户: {} (ID: {})",
                    name, config, userInfo[0], userInfo[1]);

            return result;

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "创建限流器失败: " + e.getMessage());
            result.put("operator", userInfo[0]);

            log.error("创建限流器失败 - 限流器: {}, 配置: {}, 操作用户: {} (ID: {}), 错误: {}",
                     name, config, userInfo[0], userInfo[1], e.getMessage(), e);

            return result;
        }
    }

    /**
     * 更新限流器配置
     */
    @PutMapping("/{name}")
    public Map<String, Object> updateRateLimiter(
            @PathVariable String name,
            @RequestBody Map<String, Object> config) {

        String[] userInfo = getCurrentUserInfo();
        Map<String, Object> result = new HashMap<>();

        // 权限检查
        if (!checkPermission("UPDATE_RATE_LIMITER")) {
            result.put("success", false);
            result.put("message", "权限不足，无法更新限流器");
            result.put("operator", userInfo[0]);
            log.warn("更新限流器权限不足 - 限流器: {}, 操作用户: {} (ID: {})", name, userInfo[0], userInfo[1]);
            return result;
        }

        try {
            // 记录更新前的配置
            RateLimiter oldRateLimiter = rateLimiterRegistry.find(name).orElse(null);
            Map<String, Object> oldConfig = oldRateLimiter != null ? getRateLimiterInfo(oldRateLimiter) : null;

            // 删除旧的限流器
            rateLimiterRegistry.remove(name);

            // 创建新的限流器配置
            RateLimiterConfig rateLimiterConfig = buildRateLimiterConfig(config);
            RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(name, rateLimiterConfig);

            result.put("success", true);
            result.put("message", "限流器更新成功");
            result.put("rateLimiter", getRateLimiterInfo(rateLimiter));
            result.put("operator", userInfo[0]);

            log.info("更新限流器成功 - 限流器: {}, 新配置: {}, 操作用户: {} (ID: {})",
                    name, config, userInfo[0], userInfo[1]);
            log.debug("限流器更新详情 - 限流器: {}, 原配置: {}, 新配置: {}, 操作用户: {} (ID: {})",
                     name, oldConfig, config, userInfo[0], userInfo[1]);

            return result;

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "更新限流器失败: " + e.getMessage());
            result.put("operator", userInfo[0]);

            log.error("更新限流器失败 - 限流器: {}, 配置: {}, 操作用户: {} (ID: {}), 错误: {}",
                     name, config, userInfo[0], userInfo[1], e.getMessage(), e);

            return result;
        }
    }

    /**
     * 删除限流器
     */
    @DeleteMapping("/{name}")
    public Map<String, Object> removeRateLimiter(@PathVariable String name) {
        String[] userInfo = getCurrentUserInfo();
        Map<String, Object> result = new HashMap<>();

        // 权限检查
        if (!checkPermission("DELETE_RATE_LIMITER")) {
            result.put("success", false);
            result.put("message", "权限不足，无法删除限流器");
            result.put("operator", userInfo[0]);
            log.warn("删除限流器权限不足 - 限流器: {}, 操作用户: {} (ID: {})", name, userInfo[0], userInfo[1]);
            return result;
        }

        try {
            // 记录删除前的配置信息
            RateLimiter rateLimiter = rateLimiterRegistry.find(name).orElse(null);
            Map<String, Object> rateLimiterInfo = rateLimiter != null ? getRateLimiterInfo(rateLimiter) : null;

            rateLimiterRegistry.remove(name);

            // 清理相关指标
            if (rateLimiterMetrics != null) {
                rateLimiterMetrics.clearMetrics(name);
            }

            result.put("success", true);
            result.put("message", "限流器删除成功");
            result.put("operator", userInfo[0]);

            log.info("删除限流器成功 - 限流器: {}, 操作用户: {} (ID: {})", name, userInfo[0], userInfo[1]);
            log.debug("删除限流器详情 - 限流器: {}, 原配置: {}, 操作用户: {} (ID: {})",
                     name, rateLimiterInfo, userInfo[0], userInfo[1]);

            return result;

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除限流器失败: " + e.getMessage());
            result.put("operator", userInfo[0]);

            log.error("删除限流器失败 - 限流器: {}, 操作用户: {} (ID: {}), 错误: {}",
                     name, userInfo[0], userInfo[1], e.getMessage(), e);

            return result;
        }
    }

    /**
     * 获取指标统计信息
     */
    @GetMapping("/metrics")
    public Map<String, Object> getMetrics() {
        Map<String, Object> result = new HashMap<>();

        if (rateLimiterMetrics != null) {
            RateLimiterMetrics.MetricsStatistics statistics = rateLimiterMetrics.getStatistics();
            result.put("success", true);
            result.put("metrics", statistics);
        } else {
            result.put("success", false);
            result.put("message", "指标收集器未启用");
        }

        return result;
    }

    /**
     * 获取限流器详细信息
     */
    private Map<String, Object> getRateLimiterInfo(RateLimiter rateLimiter) {
        Map<String, Object> info = new HashMap<>();
        RateLimiterConfig config = rateLimiter.getRateLimiterConfig();
        
        info.put("name", rateLimiter.getName());
        info.put("availablePermissions", rateLimiter.getMetrics().getAvailablePermissions());
        info.put("numberOfWaitingThreads", rateLimiter.getMetrics().getNumberOfWaitingThreads());
        
        // 配置信息
        Map<String, Object> configInfo = new HashMap<>();
        configInfo.put("limitForPeriod", config.getLimitForPeriod());
        configInfo.put("limitRefreshPeriod", config.getLimitRefreshPeriod().toString());
        configInfo.put("timeoutDuration", config.getTimeoutDuration().toString());
        info.put("config", configInfo);
        
        return info;
    }

    /**
     * 构建限流器配置
     */
    private RateLimiterConfig buildRateLimiterConfig(Map<String, Object> config) {
        RateLimiterConfig.Builder builder = RateLimiterConfig.custom();
        
        if (config.containsKey("limitForPeriod")) {
            builder.limitForPeriod((Integer) config.get("limitForPeriod"));
        }
        
        if (config.containsKey("limitRefreshPeriodSeconds")) {
            builder.limitRefreshPeriod(Duration.ofSeconds(((Number) config.get("limitRefreshPeriodSeconds")).longValue()));
        }
        
        if (config.containsKey("timeoutDurationMillis")) {
            builder.timeoutDuration(Duration.ofMillis(((Number) config.get("timeoutDurationMillis")).longValue()));
        }
        
        return builder.build();
    }

    /**
     * 生成管理页面HTML
     */
    private String generateManagementPage() {
        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>限流器管理</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }
                    .container { max-width: 1200px; margin: 0 auto; background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                    h1 { color: #333; text-align: center; margin-bottom: 30px; }
                    .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; margin-bottom: 30px; }
                    .stat-card { background: #f8f9fa; padding: 15px; border-radius: 6px; text-align: center; }
                    .stat-value { font-size: 24px; font-weight: bold; color: #007bff; }
                    .stat-label { color: #666; margin-top: 5px; }
                    .rate-limiters { margin-top: 20px; }
                    .rate-limiter { background: #fff; border: 1px solid #ddd; border-radius: 6px; margin-bottom: 15px; padding: 15px; }
                    .rate-limiter-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
                    .rate-limiter-name { font-weight: bold; color: #333; }
                    .rate-limiter-status { padding: 4px 8px; border-radius: 4px; font-size: 12px; }
                    .status-active { background: #d4edda; color: #155724; }
                    .rate-limiter-details { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 10px; }
                    .detail-item { text-align: center; }
                    .detail-value { font-weight: bold; color: #007bff; }
                    .detail-label { color: #666; font-size: 12px; }
                    .actions { margin-top: 20px; text-align: center; }
                    .btn { padding: 8px 16px; margin: 0 5px; border: none; border-radius: 4px; cursor: pointer; }
                    .btn-primary { background: #007bff; color: white; }
                    .btn-danger { background: #dc3545; color: white; }
                    .btn:hover { opacity: 0.8; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>🚦 限流器管理</h1>

                    <!-- 当前用户信息区域 -->
                    <div class="stats">
                        <div class="stat-card">
                            <div class="stat-value" id="currentUsername">-</div>
                            <div class="stat-label">👤 当前用户</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-value" id="currentUserId">-</div>
                            <div class="stat-label">🆔 用户ID</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-value" id="authStatus">-</div>
                            <div class="stat-label">🔐 认证状态</div>
                        </div>
                    </div>

                    <div class="stats">
                        <div class="stat-card">
                            <div class="stat-value" id="totalRateLimiters">-</div>
                            <div class="stat-label">限流器总数</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-value" id="activeRateLimiters">-</div>
                            <div class="stat-label">活跃限流器</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-value" id="totalWaitingThreads">-</div>
                            <div class="stat-label">等待线程数</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-value" id="totalRequests">-</div>
                            <div class="stat-label">总请求数</div>
                        </div>
                        <div class="stat-card">
                            <div class="stat-value" id="successRate">-</div>
                            <div class="stat-label">成功率</div>
                        </div>
                    </div>
                    
                    <div class="actions">
                        <button class="btn btn-primary" onclick="refreshData()">刷新数据</button>
                        <button class="btn btn-primary" onclick="refreshUserInfo()">刷新用户信息</button>
                        <button class="btn btn-primary" onclick="showCreateDialog()">创建限流器</button>
                        <button class="btn btn-primary" onclick="showMetrics()">查看指标</button>
                    </div>
                    
                    <div class="rate-limiters" id="rateLimiters">
                        <!-- 限流器列表将在这里动态生成 -->
                    </div>
                </div>
                
                <script>
                    function refreshData() {
                        // 同时获取限流器状态和用户信息
                        Promise.all([
                            fetch('/actuator/rate-limiter/status'),
                            fetch('/actuator/circuit-breaker-config/user-info')
                        ])
                        .then(responses => Promise.all(responses.map(r => r.json())))
                        .then(([rateLimiterData, userInfoData]) => {
                            updateStats(rateLimiterData);
                            updateRateLimiters(rateLimiterData.rateLimiters);
                            updateUserInfo(userInfoData);
                        })
                        .catch(error => {
                            console.error('Error:', error);
                            // 如果获取用户信息失败，仍然尝试获取限流器数据
                            fetch('/actuator/rate-limiter/status')
                                .then(response => response.json())
                                .then(data => {
                                    updateStats(data);
                                    updateRateLimiters(data.rateLimiters);
                                })
                                .catch(error => console.error('Error fetching rate limiter data:', error));
                        });
                    }

                    function updateUserInfo(userInfoData) {
                        if (userInfoData && userInfoData.success) {
                            document.getElementById('currentUsername').textContent = userInfoData.username || '未知';
                            document.getElementById('currentUserId').textContent = userInfoData.userId || '未知';
                            document.getElementById('authStatus').textContent = userInfoData.authenticated ? '✅ 已认证' : '❌ 未认证';
                        } else {
                            document.getElementById('currentUsername').textContent = '获取失败';
                            document.getElementById('currentUserId').textContent = '获取失败';
                            document.getElementById('authStatus').textContent = '❌ 未知';
                        }
                    }

                    function refreshUserInfo() {
                        fetch('/actuator/circuit-breaker-config/user-info')
                            .then(response => response.json())
                            .then(data => {
                                updateUserInfo(data);
                            })
                            .catch(error => {
                                console.error('Error fetching user info:', error);
                                document.getElementById('currentUsername').textContent = '获取失败';
                                document.getElementById('currentUserId').textContent = '获取失败';
                                document.getElementById('authStatus').textContent = '❌ 获取失败';
                            });
                    }
                    
                    function updateStats(data) {
                        document.getElementById('totalRateLimiters').textContent = data.totalCount;
                        document.getElementById('activeRateLimiters').textContent = data.totalCount;

                        let totalWaiting = 0;
                        Object.values(data.rateLimiters).forEach(rl => {
                            totalWaiting += rl.numberOfWaitingThreads;
                        });
                        document.getElementById('totalWaitingThreads').textContent = totalWaiting;

                        // 更新指标信息
                        if (data.metrics) {
                            document.getElementById('totalRequests').textContent = data.metrics.totalRequests || 0;
                            document.getElementById('successRate').textContent =
                                ((data.metrics.successRate || 0) * 100).toFixed(1) + '%';
                        } else {
                            document.getElementById('totalRequests').textContent = '-';
                            document.getElementById('successRate').textContent = '-';
                        }
                    }
                    
                    function updateRateLimiters(rateLimiters) {
                        const container = document.getElementById('rateLimiters');
                        container.innerHTML = '';
                        
                        Object.entries(rateLimiters).forEach(([name, info]) => {
                            const div = document.createElement('div');
                            div.className = 'rate-limiter';
                            div.innerHTML = `
                                <div class="rate-limiter-header">
                                    <span class="rate-limiter-name">${name}</span>
                                    <span class="rate-limiter-status status-active">活跃</span>
                                </div>
                                <div class="rate-limiter-details">
                                    <div class="detail-item">
                                        <div class="detail-value">${info.availablePermissions}</div>
                                        <div class="detail-label">可用许可</div>
                                    </div>
                                    <div class="detail-item">
                                        <div class="detail-value">${info.numberOfWaitingThreads}</div>
                                        <div class="detail-label">等待线程</div>
                                    </div>
                                    <div class="detail-item">
                                        <div class="detail-value">${info.config.limitForPeriod}</div>
                                        <div class="detail-label">周期限制</div>
                                    </div>
                                    <div class="detail-item">
                                        <div class="detail-value">${info.config.limitRefreshPeriod}</div>
                                        <div class="detail-label">刷新周期</div>
                                    </div>
                                </div>
                                <div style="margin-top: 10px; text-align: right;">
                                    <button class="btn btn-primary" onclick="updateRateLimiter('${name}')">更新</button>
                                    <button class="btn btn-danger" onclick="removeRateLimiter('${name}')">删除</button>
                                </div>
                            `;
                            container.appendChild(div);
                        });
                    }
                    
                    function removeRateLimiter(name) {
                        if (confirm('确定要删除限流器 "' + name + '" 吗？')) {
                            fetch('/actuator/rate-limiter/' + name, { method: 'DELETE' })
                                .then(response => response.json())
                                .then(data => {
                                    if (data.success) {
                                        alert('删除成功');
                                        refreshData();
                                    } else {
                                        alert('删除失败: ' + data.message);
                                    }
                                })
                                .catch(error => {
                                    console.error('Error:', error);
                                    alert('删除失败');
                                });
                        }
                    }
                    
                    function showCreateDialog() {
                        const name = prompt('请输入限流器名称:');
                        if (name) {
                            const limitForPeriod = prompt('请输入每周期限制数量:', '10');
                            const limitRefreshPeriodSeconds = prompt('请输入刷新周期(秒):', '1');
                            const timeoutDurationMillis = prompt('请输入超时时间(毫秒):', '500');
                            
                            const config = {
                                limitForPeriod: parseInt(limitForPeriod),
                                limitRefreshPeriodSeconds: parseInt(limitRefreshPeriodSeconds),
                                timeoutDurationMillis: parseInt(timeoutDurationMillis)
                            };
                            
                            fetch('/actuator/rate-limiter/' + name, {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify(config)
                            })
                            .then(response => response.json())
                            .then(data => {
                                if (data.success) {
                                    alert('创建成功');
                                    refreshData();
                                } else {
                                    alert('创建失败: ' + data.message);
                                }
                            })
                            .catch(error => {
                                console.error('Error:', error);
                                alert('创建失败');
                            });
                        }
                    }

                    function updateRateLimiter(name) {
                        const limitForPeriod = prompt('请输入每周期限制数量:', '10');
                        if (limitForPeriod === null) return;

                        const limitRefreshPeriodSeconds = prompt('请输入刷新周期(秒):', '1');
                        if (limitRefreshPeriodSeconds === null) return;

                        const timeoutDurationMillis = prompt('请输入超时时间(毫秒):', '500');
                        if (timeoutDurationMillis === null) return;

                        const config = {
                            limitForPeriod: parseInt(limitForPeriod),
                            limitRefreshPeriodSeconds: parseInt(limitRefreshPeriodSeconds),
                            timeoutDurationMillis: parseInt(timeoutDurationMillis)
                        };

                        fetch('/actuator/rate-limiter/' + name, {
                            method: 'PUT',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify(config)
                        })
                        .then(response => response.json())
                        .then(data => {
                            if (data.success) {
                                alert('更新成功');
                                refreshData();
                            } else {
                                alert('更新失败: ' + data.message);
                            }
                        })
                        .catch(error => {
                            console.error('Error:', error);
                            alert('更新失败');
                        });
                    }

                    function showMetrics() {
                        fetch('/actuator/rate-limiter/metrics')
                            .then(response => response.json())
                            .then(data => {
                                if (data.success && data.metrics) {
                                    const metrics = data.metrics;
                                    const message = `指标统计信息:\\n\\n` +
                                        `总计数器: ${metrics.totalCounters}\\n` +
                                        `总计时器: ${metrics.totalTimers}\\n` +
                                        `总请求数: ${metrics.totalRequests}\\n` +
                                        `成功请求数: ${metrics.successRequests}\\n` +
                                        `拒绝请求数: ${metrics.rejectedRequests}\\n` +
                                        `成功率: ${(metrics.successRate * 100).toFixed(2)}%`;
                                    alert(message);
                                } else {
                                    alert('获取指标失败: ' + (data.message || '未知错误'));
                                }
                            })
                            .catch(error => {
                                console.error('Error:', error);
                                alert('获取指标失败');
                            });
                    }

                    // 页面加载时刷新数据
                    refreshData();

                    // 每5秒自动刷新
                    setInterval(refreshData, 5000);
                </script>
            </body>
            </html>
            """;
    }
}
