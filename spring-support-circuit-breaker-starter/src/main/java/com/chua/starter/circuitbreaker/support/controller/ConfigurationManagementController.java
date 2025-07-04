package com.chua.starter.circuitbreaker.support.controller;

import com.chua.starter.circuitbreaker.support.properties.CircuitBreakerProperties;
import com.chua.starter.common.support.oauth.AuthService;
import com.chua.starter.common.support.oauth.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 配置管理控制器
 *
 * 提供熔断器配置的查看和动态修改功能，集成用户认证服务获取当前用户信息
 *
 * @author CH
 * @since 2024/12/20
 */
@Slf4j
@RestController
@RequestMapping("/actuator/circuit-breaker-config")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "plugin.circuit-breaker", name = "enable", havingValue = "true", matchIfMissing = true)
public class ConfigurationManagementController {

    private final CircuitBreakerProperties properties;

    /**
     * 认证服务 - 用于获取当前登录用户的账号信息
     * 提供用户身份验证和权限检查功能，支持多种认证方式（Spring Security、JWT Token、Session等）
     */
    @Autowired(required = false)
    private AuthService authService;

    /**
     * 获取配置管理页面
     */
    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public String getConfigurationPage() {
        return generateConfigurationPage();
    }

    /**
     * 获取当前配置
     */
    @GetMapping("/current")
    public Map<String, Object> getCurrentConfiguration() {
        Map<String, Object> config = new HashMap<>();

        config.put("enable", properties.isEnable());
        config.put("circuitBreaker", properties.getCircuitBreaker());
        config.put("rateLimiter", properties.getRateLimiter());
        config.put("retry", properties.getRetry());
        config.put("bulkhead", properties.getBulkhead());
        config.put("timeLimiter", properties.getTimeLimiter());
        config.put("cache", properties.getCache());

        // 添加当前用户信息
        if (authService != null) {
            Map<String, Object> userInfo = new HashMap<>();
            CurrentUser currentUser = authService.getCurrentUser();
            if(null != currentUser) {
                try {
                    String userId = currentUser.getUserId();
                    String username = currentUser.getUsername();
                    boolean authenticated = authService.isAuthenticated();

                    userInfo.put("userId", userId != null ? userId : "未知");
                    userInfo.put("username", username != null ? username : "匿名用户");
                    userInfo.put("authenticated", authenticated);

                    log.debug("获取当前用户信息 - 用户ID: {}, 用户名: {}, 认证状态: {}", userId, username, authenticated);
                } catch (Exception e) {
                    log.warn("获取用户信息失败: {}", e.getMessage());
                    userInfo.put("userId", "获取失败");
                    userInfo.put("username", "获取失败");
                    userInfo.put("authenticated", false);
                }
            }
            config.put("currentUser", userInfo);
        }

        return config;
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/user-info")
    public Map<String, Object> getCurrentUserInfo() {
        Map<String, Object> result = new HashMap<>();

        if (authService != null) {
            CurrentUser currentUser = authService.getCurrentUser();
            if(null != currentUser) {
                try {
                    String userId = currentUser.getUserId();
                    String username = currentUser.getUsername();
                    boolean authenticated = authService.isAuthenticated();
                    Set<String> roles = currentUser.getRoles();
                    Set<String> permissions = currentUser.getPermission();

                    result.put("userId", userId != null ? userId : "未知");
                    result.put("username", username != null ? username : "匿名用户");
                    result.put("authenticated", authenticated);
                    result.put("userInfo", currentUser);
                    result.put("roles", roles != null ? roles : new String[0]);
                    result.put("permissions", permissions != null ? permissions : new String[0]);
                    result.put("success", true);

                    log.debug("获取用户详细信息 - 用户ID: {}, 用户名: {}, 角色数: {}, 权限数: {}",
                            userId, username, roles != null ? roles.size() : 0, permissions != null ? permissions.size() : 0);
                } catch (Exception e) {
                    log.warn("获取用户详细信息失败: {}", e.getMessage());
                    result.put("success", false);
                    result.put("message", "获取用户信息失败: " + e.getMessage());
                }
            }
        } else {
            result.put("success", false);
            result.put("message", "认证服务不可用");
        }

        return result;
    }

    /**
     * 更新限流器配置
     */
    @PostMapping("/rate-limiter")
    public Map<String, Object> updateRateLimiterConfig(@RequestBody Map<String, Object> config) {
        Map<String, Object> result = new HashMap<>();

        // 获取当前操作用户信息
        String currentUser = "系统";
        String userId = "unknown";
        if (authService != null) {
            CurrentUser currentUser1 = authService.getCurrentUser();
            if(null != currentUser1) {
                try {
                    String username = currentUser1.getUsername();
                    String userIdFromAuth = currentUser1.getUserId();
                    currentUser = username != null ? username : "匿名用户";
                    userId = userIdFromAuth != null ? userIdFromAuth : "unknown";
                } catch (Exception e) {
                    log.warn("获取当前用户信息失败: {}", e.getMessage());
                }
            }
        }

        try {
            CircuitBreakerProperties.RateLimiter rateLimiter = properties.getRateLimiter();
            if (rateLimiter == null) {
                rateLimiter = new CircuitBreakerProperties.RateLimiter();
                properties.setRateLimiter(rateLimiter);
            }

            // 记录配置变更前的状态
            Map<String, Object> oldConfig = new HashMap<>();
            oldConfig.put("limitForPeriod", rateLimiter.getLimitForPeriod());
            oldConfig.put("enableManagement", rateLimiter.isEnableManagement());
            oldConfig.put("managementPath", rateLimiter.getManagementPath());

            // 更新配置
            if (config.containsKey("limitForPeriod")) {
                rateLimiter.setLimitForPeriod((Integer) config.get("limitForPeriod"));
            }
            if (config.containsKey("enableManagement")) {
                rateLimiter.setEnableManagement((Boolean) config.get("enableManagement"));
            }
            if (config.containsKey("managementPath")) {
                rateLimiter.setManagementPath((String) config.get("managementPath"));
            }

            result.put("success", true);
            result.put("message", "限流器配置更新成功");
            result.put("config", rateLimiter);
            result.put("operator", currentUser);

            // 记录详细的操作日志，包含用户信息和配置变更详情
            log.info("限流器配置已更新 - 操作用户: {} (ID: {}), 原配置: {}, 新配置: {}",
                    currentUser, userId, oldConfig, config);

        } catch (Exception e) {
            log.error("更新限流器配置失败 - 操作用户: {} (ID: {}), 配置: {}, 错误: {}",
                     currentUser, userId, config, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "更新失败: " + e.getMessage());
            result.put("operator", currentUser);
        }

        return result;
    }

    /**
     * 重置配置为默认值
     */
    @PostMapping("/reset")
    public Map<String, Object> resetConfiguration() {
        Map<String, Object> result = new HashMap<>();

        // 获取当前操作用户信息
        String currentUser = "系统";
        String userId = "unknown";
        if (authService != null) {
            CurrentUser currentUser1 = authService.getCurrentUser();
            if(null != currentUser1) {
                try {
                    String username = currentUser1.getUsername();
                    String userIdFromAuth = currentUser1.getUserId();
                    currentUser = username != null ? username : "匿名用户";
                    userId = userIdFromAuth != null ? userIdFromAuth : "unknown";
                } catch (Exception e) {
                    log.warn("获取当前用户信息失败: {}", e.getMessage());
                }
            }
        }

        try {
            // 记录重置前的配置状态
            Map<String, Object> oldConfig = new HashMap<>();
            oldConfig.put("rateLimiter", properties.getRateLimiter());
            oldConfig.put("circuitBreaker", properties.getCircuitBreaker());
            oldConfig.put("retry", properties.getRetry());
            oldConfig.put("bulkhead", properties.getBulkhead());
            oldConfig.put("timeLimiter", properties.getTimeLimiter());

            // 重置为默认配置
            properties.setRateLimiter(new CircuitBreakerProperties.RateLimiter());
            properties.setCircuitBreaker(new CircuitBreakerProperties.CircuitBreaker());
            properties.setRetry(new CircuitBreakerProperties.Retry());
            properties.setBulkhead(new CircuitBreakerProperties.Bulkhead());
            properties.setTimeLimiter(new CircuitBreakerProperties.TimeLimiter());

            result.put("success", true);
            result.put("message", "配置已重置为默认值");
            result.put("operator", currentUser);

            // 记录详细的重置操作日志
            log.info("配置已重置为默认值 - 操作用户: {} (ID: {}), 重置前配置已备份", currentUser, userId);
            log.debug("配置重置详情 - 操作用户: {} (ID: {}), 原配置: {}", currentUser, userId, oldConfig);

        } catch (Exception e) {
            log.error("重置配置失败 - 操作用户: {} (ID: {}), 错误: {}", currentUser, userId, e.getMessage(), e);
            result.put("success", false);
            result.put("message", "重置失败: " + e.getMessage());
            result.put("operator", currentUser);
        }

        return result;
    }

    /**
     * 生成配置管理页面HTML
     */
    private String generateConfigurationPage() {
        return """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>熔断器配置管理</title>
                <style>
                    body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }
                    .container { max-width: 1200px; margin: 0 auto; background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }
                    h1 { color: #333; text-align: center; margin-bottom: 30px; }
                    .config-section { margin-bottom: 30px; padding: 20px; border: 1px solid #ddd; border-radius: 6px; }
                    .config-title { font-size: 18px; font-weight: bold; color: #333; margin-bottom: 15px; }
                    .config-item { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; padding: 10px; background: #f8f9fa; border-radius: 4px; }
                    .config-label { font-weight: bold; color: #555; }
                    .config-value { color: #007bff; font-family: monospace; }
                    .config-input { padding: 5px 10px; border: 1px solid #ddd; border-radius: 4px; width: 200px; }
                    .btn { padding: 8px 16px; margin: 0 5px; border: none; border-radius: 4px; cursor: pointer; }
                    .btn-primary { background: #007bff; color: white; }
                    .btn-success { background: #28a745; color: white; }
                    .btn-warning { background: #ffc107; color: #212529; }
                    .btn-danger { background: #dc3545; color: white; }
                    .btn:hover { opacity: 0.8; }
                    .status-enabled { color: #28a745; font-weight: bold; }
                    .status-disabled { color: #dc3545; font-weight: bold; }
                    .actions { text-align: center; margin-top: 20px; }
                    .alert { padding: 15px; margin-bottom: 20px; border: 1px solid transparent; border-radius: 4px; }
                    .alert-success { color: #155724; background-color: #d4edda; border-color: #c3e6cb; }
                    .alert-danger { color: #721c24; background-color: #f8d7da; border-color: #f5c6cb; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>⚙️ 熔断器配置管理</h1>

                    <div id="alertContainer"></div>

                    <!-- 当前用户信息区域 -->
                    <div class="config-section">
                        <div class="config-title">👤 当前用户信息</div>
                        <div class="config-item">
                            <span class="config-label">用户名:</span>
                            <span class="config-value" id="currentUsername">-</span>
                        </div>
                        <div class="config-item">
                            <span class="config-label">用户ID:</span>
                            <span class="config-value" id="currentUserId">-</span>
                        </div>
                        <div class="config-item">
                            <span class="config-label">认证状态:</span>
                            <span class="config-value" id="authStatus">-</span>
                        </div>
                        <div class="actions">
                            <button class="btn btn-primary" onclick="refreshUserInfo()">刷新用户信息</button>
                        </div>
                    </div>

                    <div class="config-section">
                        <div class="config-title">📊 功能状态</div>
                        <div class="config-item">
                            <span class="config-label">熔断器模块:</span>
                            <span class="config-value" id="moduleStatus">-</span>
                        </div>
                        <div class="config-item">
                            <span class="config-label">熔断器:</span>
                            <span class="config-value" id="circuitBreakerStatus">-</span>
                        </div>
                        <div class="config-item">
                            <span class="config-label">限流器:</span>
                            <span class="config-value" id="rateLimiterStatus">-</span>
                        </div>
                        <div class="config-item">
                            <span class="config-label">重试机制:</span>
                            <span class="config-value" id="retryStatus">-</span>
                        </div>
                        <div class="config-item">
                            <span class="config-label">舱壁隔离:</span>
                            <span class="config-value" id="bulkheadStatus">-</span>
                        </div>
                        <div class="config-item">
                            <span class="config-label">超时控制:</span>
                            <span class="config-value" id="timeLimiterStatus">-</span>
                        </div>
                    </div>
                    
                    <div class="config-section">
                        <div class="config-title">🚦 限流器配置</div>
                        <div class="config-item">
                            <span class="config-label">每周期限制数量:</span>
                            <input type="number" id="limitForPeriod" class="config-input" placeholder="10">
                        </div>
                        <div class="config-item">
                            <span class="config-label">启用管理页面:</span>
                            <select id="enableManagement" class="config-input">
                                <option value="true">启用</option>
                                <option value="false">禁用</option>
                            </select>
                        </div>
                        <div class="config-item">
                            <span class="config-label">管理页面路径:</span>
                            <input type="text" id="managementPath" class="config-input" placeholder="/actuator/rate-limiter">
                        </div>
                        <div class="actions">
                            <button class="btn btn-success" onclick="updateRateLimiterConfig()">更新限流器配置</button>
                        </div>
                    </div>
                    
                    <div class="actions">
                        <button class="btn btn-primary" onclick="refreshConfig()">刷新配置</button>
                        <button class="btn btn-warning" onclick="resetConfig()">重置为默认值</button>
                        <button class="btn btn-primary" onclick="openRateLimiterManagement()">打开限流器管理</button>
                    </div>
                </div>
                
                <script>
                    function refreshConfig() {
                        fetch('/actuator/circuit-breaker-config/current')
                            .then(response => response.json())
                            .then(data => {
                                updateConfigDisplay(data);
                                showAlert('配置已刷新', 'success');
                            })
                            .catch(error => {
                                console.error('Error:', error);
                                showAlert('刷新配置失败', 'danger');
                            });
                    }
                    
                    function updateConfigDisplay(config) {
                        document.getElementById('moduleStatus').textContent = config.enable ? '✅ 已启用' : '❌ 已禁用';
                        document.getElementById('circuitBreakerStatus').textContent = config.circuitBreaker ? '✅ 已配置' : '❌ 未配置';
                        document.getElementById('rateLimiterStatus').textContent = config.rateLimiter ? '✅ 已配置' : '❌ 未配置';
                        document.getElementById('retryStatus').textContent = config.retry ? '✅ 已配置' : '❌ 未配置';
                        document.getElementById('bulkheadStatus').textContent = config.bulkhead ? '✅ 已配置' : '❌ 未配置';
                        document.getElementById('timeLimiterStatus').textContent = config.timeLimiter ? '✅ 已配置' : '❌ 未配置';

                        // 更新用户信息显示
                        if (config.currentUser) {
                            document.getElementById('currentUsername').textContent = config.currentUser.username || '未知';
                            document.getElementById('currentUserId').textContent = config.currentUser.userId || '未知';
                            document.getElementById('authStatus').textContent = config.currentUser.authenticated ? '✅ 已认证' : '❌ 未认证';
                        }

                        if (config.rateLimiter) {
                            document.getElementById('limitForPeriod').value = config.rateLimiter.limitForPeriod || 10;
                            document.getElementById('enableManagement').value = config.rateLimiter.enableManagement || true;
                            document.getElementById('managementPath').value = config.rateLimiter.managementPath || '/actuator/rate-limiter';
                        }
                    }

                    function refreshUserInfo() {
                        fetch('/actuator/circuit-breaker-config/user-info')
                            .then(response => response.json())
                            .then(data => {
                                if (data.success) {
                                    document.getElementById('currentUsername').textContent = data.username || '未知';
                                    document.getElementById('currentUserId').textContent = data.userId || '未知';
                                    document.getElementById('authStatus').textContent = data.authenticated ? '✅ 已认证' : '❌ 未认证';
                                    showAlert('用户信息已刷新', 'success');
                                } else {
                                    showAlert(data.message || '获取用户信息失败', 'danger');
                                }
                            })
                            .catch(error => {
                                console.error('Error:', error);
                                showAlert('获取用户信息失败', 'danger');
                            });
                    }
                    
                    function updateRateLimiterConfig() {
                        const config = {
                            limitForPeriod: parseInt(document.getElementById('limitForPeriod').value),
                            enableManagement: document.getElementById('enableManagement').value === 'true',
                            managementPath: document.getElementById('managementPath').value
                        };
                        
                        fetch('/actuator/circuit-breaker-config/rate-limiter', {
                            method: 'POST',
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify(config)
                        })
                        .then(response => response.json())
                        .then(data => {
                            if (data.success) {
                                showAlert(data.message, 'success');
                                refreshConfig();
                            } else {
                                showAlert(data.message, 'danger');
                            }
                        })
                        .catch(error => {
                            console.error('Error:', error);
                            showAlert('更新配置失败', 'danger');
                        });
                    }
                    
                    function resetConfig() {
                        if (confirm('确定要重置所有配置为默认值吗？')) {
                            fetch('/actuator/circuit-breaker-config/reset', { method: 'POST' })
                                .then(response => response.json())
                                .then(data => {
                                    if (data.success) {
                                        showAlert(data.message, 'success');
                                        refreshConfig();
                                    } else {
                                        showAlert(data.message, 'danger');
                                    }
                                })
                                .catch(error => {
                                    console.error('Error:', error);
                                    showAlert('重置配置失败', 'danger');
                                });
                        }
                    }
                    
                    function openRateLimiterManagement() {
                        const path = document.getElementById('managementPath').value || '/actuator/rate-limiter';
                        window.open(path, '_blank');
                    }
                    
                    function showAlert(message, type) {
                        const alertContainer = document.getElementById('alertContainer');
                        const alert = document.createElement('div');
                        alert.className = `alert alert-${type}`;
                        alert.textContent = message;
                        
                        alertContainer.innerHTML = '';
                        alertContainer.appendChild(alert);
                        
                        setTimeout(() => {
                            alertContainer.innerHTML = '';
                        }, 3000);
                    }
                    
                    // 页面加载时刷新配置
                    refreshConfig();
                </script>
            </body>
            </html>
            """;
    }
}
