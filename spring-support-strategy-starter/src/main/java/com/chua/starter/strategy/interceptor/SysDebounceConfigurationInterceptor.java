package com.chua.starter.strategy.interceptor;

import com.chua.common.support.task.debounce.DebounceException;
import com.chua.common.support.task.debounce.DebounceManager;
import com.chua.starter.strategy.entity.SysDebounceConfiguration;
import com.chua.starter.strategy.event.DebounceEvent;
import com.chua.starter.strategy.service.SysDebounceConfigurationService;
import com.chua.starter.strategy.util.StrategyEventPublisher;
import com.chua.starter.strategy.util.UserContextHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 防抖配置拦截器
 * 
 * 处理数据库配置的防抖策略，拦截HTTP请求
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SysDebounceConfigurationInterceptor implements HandlerInterceptor {

    private final SysDebounceConfigurationService sysDebounceConfigurationService;
    private final DebounceManager debounceManager = DebounceManager.getInstance();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) 
            throws Exception {
        String requestPath = request.getRequestURI();
        
        // 从内存缓存中匹配防抖配置
        SysDebounceConfiguration config = sysDebounceConfigurationService.matchDebounceConfiguration(requestPath);
        
        if (config == null) {
            // 没有匹配的配置，放行
            return true;
        }
        
        // 生成防抖键
        String debounceKey = generateDebounceKey(config, request);
        
        // 尝试获取防抖锁
        boolean acquired = debounceManager.tryAcquire(debounceKey, config.getSysDebounceDuration());
        
        if (acquired) {
            // 未被防抖，放行
            log.debug("防抖检查通过: {} - {}", config.getSysDebounceName(), debounceKey);
            publishEvent(request, config, debounceKey, true, null);
            return true;
        } else {
            // 被防抖，拒绝请求
            log.debug("防抖触发: {} - {}", config.getSysDebounceName(), debounceKey);
            publishEvent(request, config, debounceKey, false, config.getSysDebounceMessage());
            throw new DebounceException(config.getSysDebounceMessage());
        }
    }

    /**
     * 生成防抖键
     *
     * @param config  防抖配置
     * @param request HTTP请求
     * @return 防抖键
     */
    private String generateDebounceKey(SysDebounceConfiguration config, HttpServletRequest request) {
        StringBuilder keyBuilder = new StringBuilder("debounce:");
        keyBuilder.append(config.getSysDebounceName()).append(":");
        
        // 根据模式生成键
        String mode = config.getSysDebounceMode();
        switch (mode) {
            case "ip" -> keyBuilder.append(getClientIp(request));
            case "user" -> keyBuilder.append(getUserId(request));
            case "session" -> keyBuilder.append(getSessionId(request));
            default -> keyBuilder.append("global");
        }
        
        // 如果配置了自定义键表达式，追加到键中
        if (StringUtils.hasText(config.getSysDebounceKey())) {
            keyBuilder.append(":").append(config.getSysDebounceKey());
        }
        
        return keyBuilder.toString();
    }

    /**
     * 获取客户端IP
     *
     * @param request HTTP请求
     * @return 客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip != null ? ip : "unknown";
    }

    /**
     * 获取用户ID
     *
     * @param request HTTP请求
     * @return 用户ID
     */
    private String getUserId(HttpServletRequest request) {
        String userId = UserContextHelper.getUserId(request);
        return userId != null ? userId : "anonymous";
    }

    /**
     * 发布防抖事件
     *
     * @param request       HTTP请求
     * @param config        防抖配置
     * @param debounceKey   防抖键
     * @param allowed       是否允许通过
     * @param reason        拒绝原因
     */
    private void publishEvent(HttpServletRequest request, SysDebounceConfiguration config,
                             String debounceKey, boolean allowed, String reason) {
        String clientIp = getClientIp(request);
        String userId = UserContextHelper.getUserId(request);

        DebounceEvent event = new DebounceEvent(
                this,
                request.getRequestURI(),
                request.getMethod(),
                clientIp,
                userId,
                config.getSysDebounceName(),
                allowed,
                reason,
                null,
                config.getSysDebounceConfigurationId(),
                config.getSysDebounceMode(),
                debounceKey,
                config.getSysDebounceDuration()
        );

        StrategyEventPublisher.publishEvent(event);
    }

    /**
     * 获取会话ID
     *
     * @param request HTTP请求
     * @return 会话ID
     */
    private String getSessionId(HttpServletRequest request) {
        try {
            return request.getSession().getId();
        } catch (Exception e) {
            return "no-session";
        }
    }
}
