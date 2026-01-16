package com.chua.starter.strategy.interceptor;

import com.chua.starter.strategy.event.IpAccessControlEvent;
import com.chua.starter.strategy.util.StrategyEventPublisher;
import com.chua.starter.strategy.util.UserContextHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * IP访问控制拦截器
 * <p>
 * 支持IP黑白名单，使用Java 21特性（sealed类、record、pattern matching）。
 * </p>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-28
 */
@Slf4j
@RequiredArgsConstructor
public class IpAccessControlInterceptor implements HandlerInterceptor {

    private final IpAccessRuleProvider ruleProvider;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    
    // 访问记录缓存
    private final Map<String, AccessRecord> accessRecords = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                            @NonNull HttpServletResponse response,
                            @NonNull Object handler) throws Exception {
        String clientIp = getClientIp(request);
        String uri = request.getRequestURI();

        // 获取访问规则
        List<IpAccessRule> rules = ruleProvider.getRules();
        
        // 使用 pattern matching for switch 检查IP访问权限
        AccessDecision decision = checkAccess(clientIp, uri, rules);
        
        return switch (decision) {
            case Allowed allowed -> {
                log.debug("IP访问允许: ip={}, uri={}, reason={}", clientIp, uri, allowed.reason());
                recordAccess(clientIp, uri, true);
                IpAccessRule matchedRule = findMatchedRule(clientIp, uri, rules);
                publishEvent(request, allowed, matchedRule);
                yield true;
            }
            case Denied denied -> {
                log.warn("IP访问拒绝: ip={}, uri={}, reason={}", clientIp, uri, denied.reason());
                recordAccess(clientIp, uri, false);
                IpAccessRule matchedRule = findMatchedRule(clientIp, uri, rules);
                publishEvent(request, denied, matchedRule);
                handleDenied(response, denied);
                yield false;
            }
        };
    }

    /**
     * 检查访问权限
     */
    private AccessDecision checkAccess(String ip, String uri, List<IpAccessRule> rules) {
        for (IpAccessRule rule : rules) {
            if (!rule.enabled()) continue;
            
            // 检查URI是否匹配
            if (rule.urlPattern() != null && !pathMatcher.match(rule.urlPattern(), uri)) {
                continue;
            }
            
            // 检查IP是否匹配
            if (matchIp(ip, rule.ipPattern())) {
                return switch (rule.type()) {
                    case WHITELIST -> new Allowed("匹配白名单规则: " + rule.name());
                    case BLACKLIST -> new Denied("匹配黑名单规则: " + rule.name(), rule.message());
                };
            }
        }
        
        // 默认允许
        return new Allowed("未匹配任何规则，默认允许");
    }

    /**
     * IP匹配（支持通配符和CIDR）
     */
    private boolean matchIp(String ip, String pattern) {
        if (pattern == null || pattern.isBlank()) return false;
        
        // 精确匹配
        if (pattern.equals(ip)) return true;
        
        // 通配符匹配（如 192.168.1.*）
        if (pattern.contains("*")) {
            String regex = pattern.replace(".", "\\.").replace("*", ".*");
            return ip.matches(regex);
        }
        
        // CIDR匹配（如 192.168.1.0/24）
        if (pattern.contains("/")) {
            return matchCidr(ip, pattern);
        }
        
        return false;
    }

    /**
     * CIDR匹配
     */
    private boolean matchCidr(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            String baseIp = parts[0];
            int prefixLength = Integer.parseInt(parts[1]);
            
            long ipLong = ipToLong(ip);
            long baseIpLong = ipToLong(baseIp);
            long mask = -1L << (32 - prefixLength);
            
            return (ipLong & mask) == (baseIpLong & mask);
        } catch (Exception e) {
            log.warn("CIDR匹配失败: ip={}, cidr={}", ip, cidr, e);
            return false;
        }
    }

    private long ipToLong(String ip) {
        String[] octets = ip.split("\\.");
        long result = 0;
        for (int i = 0; i < 4; i++) {
            result = (result << 8) | Integer.parseInt(octets[i]);
        }
        return result;
    }

    private void handleDenied(HttpServletResponse response, Denied denied) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"code\":403,\"message\":\"" + denied.message() + "\",\"success\":false}");
    }

    private void recordAccess(String ip, String uri, boolean allowed) {
        accessRecords.compute(ip, (k, v) -> {
            if (v == null) {
                return new AccessRecord(ip, 1, allowed ? 1 : 0, LocalDateTime.now());
            }
            return new AccessRecord(ip, v.totalCount() + 1, 
                    v.allowedCount() + (allowed ? 1 : 0), LocalDateTime.now());
        });
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip != null ? ip.split(",")[0].trim() : "unknown";
    }

    /**
     * 获取访问统计
     */
    public Map<String, AccessRecord> getAccessStats() {
        return Map.copyOf(accessRecords);
    }

    /**
     * 清除访问统计
     */
    public void clearAccessStats() {
        accessRecords.clear();
    }

    /**
     * 查找匹配的规则
     *
     * @param ip    客户端IP
     * @param uri   请求URI
     * @param rules 规则列表
     * @return 匹配的规则，如果没有则返回null
     */
    private IpAccessRule findMatchedRule(String ip, String uri, List<IpAccessRule> rules) {
        for (IpAccessRule rule : rules) {
            if (!rule.enabled()) {
                continue;
            }
            if (rule.urlPattern() != null && !pathMatcher.match(rule.urlPattern(), uri)) {
                continue;
            }
            if (matchIp(ip, rule.ipPattern())) {
                return rule;
            }
        }
        return null;
    }

    /**
     * 发布IP访问控制事件
     *
     * @param request HTTP请求
     * @param decision 访问决策
     * @param rule     匹配的规则
     */
    private void publishEvent(HttpServletRequest request, AccessDecision decision, IpAccessRule rule) {
        String clientIp = getClientIp(request);
        String userId = UserContextHelper.getUserId(request);

        String ruleName = null;
        String ruleType = null;
        String ipPattern = null;
        boolean allowed = decision instanceof Allowed;
        String reason = switch (decision) {
            case Allowed a -> a.reason();
            case Denied d -> d.reason();
        };

        if (rule != null) {
            ruleName = rule.name();
            ruleType = rule.type().name();
            ipPattern = rule.ipPattern();
        }

        IpAccessControlEvent event = new IpAccessControlEvent(
                this,
                request.getRequestURI(),
                request.getMethod(),
                clientIp,
                userId,
                "IP访问控制",
                allowed,
                reason,
                null,
                ruleName,
                ruleType,
                ipPattern
        );

        StrategyEventPublisher.publishEvent(event);
    }

    // ========== Java 21 sealed classes 和 records ==========

    /**
     * 访问决策（sealed interface + record patterns）
     */
    public sealed interface AccessDecision permits Allowed, Denied {}
    
    public record Allowed(String reason) implements AccessDecision {}
    
    public record Denied(String reason, String message) implements AccessDecision {}

    /**
     * IP访问规则
     */
    public record IpAccessRule(
            String name,
            RuleType type,
            String ipPattern,
            String urlPattern,
            String message,
            boolean enabled
    ) {
        public IpAccessRule {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("规则名称不能为空");
            }
        }
    }

    /**
     * 规则类型
     */
    public enum RuleType {
        WHITELIST, BLACKLIST
    }

    /**
     * 访问记录
     */
    public record AccessRecord(
            String ip,
            long totalCount,
            long allowedCount,
            LocalDateTime lastAccessTime
    ) {
        public long deniedCount() {
            return totalCount - allowedCount;
        }
        
        public double allowedRate() {
            return totalCount == 0 ? 0 : (double) allowedCount / totalCount;
        }
    }

    /**
     * 规则提供者接口
     */
    @FunctionalInterface
    public interface IpAccessRuleProvider {
        List<IpAccessRule> getRules();
    }

    /**
     * 默认规则提供者（从配置加载）
     */
    public static class DefaultRuleProvider implements IpAccessRuleProvider {
        private final Supplier<List<IpAccessRule>> ruleSupplier;

        public DefaultRuleProvider(Supplier<List<IpAccessRule>> ruleSupplier) {
            this.ruleSupplier = ruleSupplier;
        }

        @Override
        public List<IpAccessRule> getRules() {
            return ruleSupplier.get();
        }
    }
}
