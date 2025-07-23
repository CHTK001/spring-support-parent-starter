package com.chua.starter.plugin.service;

import com.chua.starter.plugin.entity.PluginXssConfig;
import com.chua.starter.plugin.filter.XssFilter;
import com.chua.starter.plugin.store.PersistenceStore;
import com.chua.starter.plugin.store.QueryCondition;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * XSS防护服务
 * 
 * @author CH
 * @since 2025/1/16
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XssProtectionService implements ApplicationRunner {

    private final PersistenceStore<PluginXssConfig, Long> xssConfigStore;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * XSS配置缓存
     */
    private final Map<String, PluginXssConfig> configCache = new ConcurrentHashMap<>();

    /**
     * 攻击统计缓存 (IP -> 攻击次数)
     */
    private final Map<String, Integer> attackCountCache = new ConcurrentHashMap<>();

    /**
     * 攻击时间窗口缓存 (IP -> 最后攻击时间)
     */
    private final Map<String, LocalDateTime> attackTimeCache = new ConcurrentHashMap<>();

    /**
     * XSS检测规则
     */
    private static final List<XssRule> XSS_RULES = Arrays.asList(
            new XssRule("script",
                    Pattern.compile("<script[^>]*>.*?</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)),
            new XssRule("javascript", Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE)),
            new XssRule("vbscript", Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE)),
            new XssRule("onload", Pattern.compile("onload\\s*=", Pattern.CASE_INSENSITIVE)),
            new XssRule("onerror", Pattern.compile("onerror\\s*=", Pattern.CASE_INSENSITIVE)),
            new XssRule("onclick", Pattern.compile("onclick\\s*=", Pattern.CASE_INSENSITIVE)),
            new XssRule("onmouseover", Pattern.compile("onmouseover\\s*=", Pattern.CASE_INSENSITIVE)),
            new XssRule("onfocus", Pattern.compile("onfocus\\s*=", Pattern.CASE_INSENSITIVE)),
            new XssRule("onblur", Pattern.compile("onblur\\s*=", Pattern.CASE_INSENSITIVE)),
            new XssRule("eval", Pattern.compile("eval\\s*\\(", Pattern.CASE_INSENSITIVE)),
            new XssRule("expression", Pattern.compile("expression\\s*\\(", Pattern.CASE_INSENSITIVE)),
            new XssRule("iframe", Pattern.compile("<iframe[^>]*>", Pattern.CASE_INSENSITIVE)),
            new XssRule("object", Pattern.compile("<object[^>]*>", Pattern.CASE_INSENSITIVE)),
            new XssRule("embed", Pattern.compile("<embed[^>]*>", Pattern.CASE_INSENSITIVE)),
            new XssRule("form", Pattern.compile("<form[^>]*>", Pattern.CASE_INSENSITIVE)),
            new XssRule("img_onerror", Pattern.compile("<img[^>]*onerror[^>]*>", Pattern.CASE_INSENSITIVE)),
            new XssRule("svg_onload", Pattern.compile("<svg[^>]*onload[^>]*>", Pattern.CASE_INSENSITIVE)));

    @Override
    public void run(ApplicationArguments args) throws Exception {
        loadAllConfigsToCache();
        log.info("XSS Protection Service initialized");
    }

    /**
     * 从数据库加载所有配置到内存缓存
     */
    public void loadAllConfigsToCache() {
        try {
            QueryCondition condition = QueryCondition.empty().eq("pluginXssConfigEnabled", true);
            List<PluginXssConfig> configs = xssConfigStore.findByCondition(condition);

            configCache.clear();
            for (PluginXssConfig config : configs) {
                configCache.put(config.getPluginXssConfigName(), config);
            }

            log.info("Loaded {} XSS protection configs to cache", configs.size());
        } catch (Exception e) {
            log.error("Failed to load XSS protection configs to cache", e);
        }
    }

    /**
     * 检查XSS防护是否启用
     * 
     * @return 是否启用
     */
    public boolean isXssProtectionEnabled() {
        return !configCache.isEmpty()
                && configCache.values().stream().anyMatch(config -> config.getPluginXssConfigEnabled());
    }

    /**
     * 检查是否需要对指定URL进行防护
     * 
     * @param requestUri 请求URI
     * @return 是否需要防护
     */
    public boolean shouldProtectUrl(String requestUri) {
        for (PluginXssConfig config : configCache.values()) {
            if (!config.getPluginXssConfigEnabled()) {
                continue;
            }

            // 检查排除模式
            if (isUrlExcluded(requestUri, config)) {
                return false;
            }

            // 检查包含模式
            if (isUrlIncluded(requestUri, config)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查是否需要检查指定参数
     * 
     * @param parameterName 参数名
     * @return 是否需要检查
     */
    public boolean shouldCheckParameter(String parameterName) {
        for (PluginXssConfig config : configCache.values()) {
            if (!config.getPluginXssConfigEnabled()) {
                continue;
            }

            // 检查排除参数
            if (isParameterExcluded(parameterName, config)) {
                return false;
            }

            // 检查包含参数
            if (isParameterIncluded(parameterName, config)) {
                return true;
            }
        }

        // 默认检查所有参数
        return true;
    }

    /**
     * 检查是否为拒绝模式
     * 
     * @return 是否为拒绝模式
     */
    public boolean isRejectMode() {
        return configCache.values().stream().filter(config -> config.getPluginXssConfigEnabled())
                .anyMatch(config -> config.getPluginXssConfigProtectionMode() == PluginXssConfig.ProtectionMode.REJECT);
    }

    /**
     * 过滤XSS内容
     * 
     * @param content       原始内容
     * @param parameterName 参数名
     * @param request       请求对象
     * @return 过滤后的内容
     * @throws XssFilter.XssAttackException XSS攻击异常
     */
    public String filterXssContent(String content, String parameterName, HttpServletRequest request) {
        if (!StringUtils.hasText(content)) {
            return content;
        }

        // 检测XSS攻击
        XssDetectionResult result = detectXssAttack(content);

        if (result.isAttack()) {
            // 获取当前配置
            PluginXssConfig config = getCurrentConfig();

            if (config != null) {
                switch (config.getPluginXssConfigProtectionMode()) {
                case REJECT:
                    throw new XssFilter.XssAttackException("XSS attack detected in parameter: " + parameterName,
                            parameterName, content, result.getAttackType());

                case ESCAPE:
                    return escapeHtml(content);

                case FILTER:
                default:
                    return filterDangerousContent(content);
                }
            }
        }

        return content;
    }


    /**
     * XSS检测结果
     */
    private static class XssDetectionResult {
        private final boolean isAttack;
        private final String attackType;
        private final int riskScore;

        public XssDetectionResult(boolean isAttack, String attackType, int riskScore) {
            this.isAttack = isAttack;
            this.attackType = attackType;
            this.riskScore = riskScore;
        }

        public boolean isAttack() {
            return isAttack;
        }

        public String getAttackType() {
            return attackType;
        }

        public int getRiskScore() {
            return riskScore;
        }
    }

    /**
     * XSS规则
     */
    private static class XssRule {
        private final String name;
        private final Pattern pattern;

        public XssRule(String name, Pattern pattern) {
            this.name = name;
            this.pattern = pattern;
        }

        public String getName() {
            return name;
        }

        public Pattern getPattern() {
            return pattern;
        }
    }

    /**
     * 检测XSS攻击
     *
     * @param content 内容
     * @return 检测结果
     */
    private XssDetectionResult detectXssAttack(String content) {
        if (!StringUtils.hasText(content)) {
            return new XssDetectionResult(false, null, 0);
        }

        String lowerContent = content.toLowerCase();
        int maxRiskScore = 0;
        String detectedType = null;

        for (XssRule rule : XSS_RULES) {
            if (rule.getPattern().matcher(content).find()) {
                int riskScore = calculateRiskScore(rule.getName(), content);
                if (riskScore > maxRiskScore) {
                    maxRiskScore = riskScore;
                    detectedType = rule.getName();
                }
            }
        }

        return new XssDetectionResult(maxRiskScore > 0, detectedType, maxRiskScore);
    }

    /**
     * 计算风险分数
     *
     * @param ruleType 规则类型
     * @param content  内容
     * @return 风险分数
     */
    private int calculateRiskScore(String ruleType, String content) {
        int baseScore = getBaseRiskScore(ruleType);

        // 根据内容特征调整分数
        if (content.contains("alert(") || content.contains("confirm(") || content.contains("prompt(")) {
            baseScore += 20;
        }

        if (content.contains("document.cookie") || content.contains("localStorage")
                || content.contains("sessionStorage")) {
            baseScore += 30;
        }

        if (content.contains("window.location") || content.contains("location.href")) {
            baseScore += 25;
        }

        return Math.min(baseScore, 100);
    }

    /**
     * 获取基础风险分数
     *
     * @param ruleType 规则类型
     * @return 基础分数
     */
    private int getBaseRiskScore(String ruleType) {
        switch (ruleType) {
        case "script":
            return 80;
        case "javascript":
            return 70;
        case "eval":
            return 75;
        case "expression":
            return 65;
        case "iframe":
            return 60;
        case "object":
            return 55;
        case "embed":
            return 55;
        default:
            return 40;
        }
    }

    /**
     * HTML转义
     *
     * @param content 原始内容
     * @return 转义后内容
     */
    private String escapeHtml(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }

        return content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#x27;").replace("/", "&#x2F;");
    }

    /**
     * 过滤危险内容
     *
     * @param content 原始内容
     * @return 过滤后内容
     */
    private String filterDangerousContent(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }

        String filtered = content;

        for (XssRule rule : XSS_RULES) {
            filtered = rule.getPattern().matcher(filtered).replaceAll("");
        }

        return filtered;
    }

    /**
     * 获取当前配置
     *
     * @return 当前配置
     */
    private PluginXssConfig getCurrentConfig() {
        return configCache.values().stream().filter(config -> config.getPluginXssConfigEnabled()).findFirst()
                .orElse(null);
    }

    /**
     * 检查URL是否被排除
     *
     * @param requestUri 请求URI
     * @param config     配置
     * @return 是否被排除
     */
    private boolean isUrlExcluded(String requestUri, PluginXssConfig config) {
        String excludePatterns = config.getPluginXssConfigExcludePatterns();
        if (!StringUtils.hasText(excludePatterns)) {
            return false;
        }

        try {
            List<String> patterns = objectMapper.readValue(excludePatterns, new TypeReference<List<String>>() {
            });
            return patterns.stream().anyMatch(pattern -> pathMatcher.match(pattern, requestUri));
        } catch (Exception e) {
            // 如果不是JSON格式，按逗号分割处理
            String[] patterns = excludePatterns.split(",");
            return Arrays.stream(patterns).map(String::trim)
                    .anyMatch(pattern -> pathMatcher.match(pattern, requestUri));
        }
    }

    /**
     * 检查URL是否被包含
     *
     * @param requestUri 请求URI
     * @param config     配置
     * @return 是否被包含
     */
    private boolean isUrlIncluded(String requestUri, PluginXssConfig config) {
        String urlPatterns = config.getPluginXssConfigUrlPatterns();
        if (!StringUtils.hasText(urlPatterns)) {
            return true; // 默认包含所有
        }

        try {
            List<String> patterns = objectMapper.readValue(urlPatterns, new TypeReference<List<String>>() {
            });
            return patterns.stream().anyMatch(pattern -> pathMatcher.match(pattern, requestUri));
        } catch (Exception e) {
            // 如果不是JSON格式，按逗号分割处理
            String[] patterns = urlPatterns.split(",");
            return Arrays.stream(patterns).map(String::trim)
                    .anyMatch(pattern -> pathMatcher.match(pattern, requestUri));
        }
    }

    /**
     * 检查参数是否被排除
     *
     * @param parameterName 参数名
     * @param config        配置
     * @return 是否被排除
     */
    private boolean isParameterExcluded(String parameterName, PluginXssConfig config) {
        String excludeParameters = config.getPluginXssConfigExcludeParameters();
        if (!StringUtils.hasText(excludeParameters)) {
            return false;
        }

        try {
            List<String> parameters = objectMapper.readValue(excludeParameters, new TypeReference<List<String>>() {
            });
            return parameters.contains(parameterName);
        } catch (Exception e) {
            String[] parameters = excludeParameters.split(",");
            return Arrays.stream(parameters).map(String::trim).anyMatch(param -> param.equals(parameterName));
        }
    }

    /**
     * 检查参数是否被包含
     *
     * @param parameterName 参数名
     * @param config        配置
     * @return 是否被包含
     */
    private boolean isParameterIncluded(String parameterName, PluginXssConfig config) {
        String checkParameters = config.getPluginXssConfigCheckParameters();
        if (!StringUtils.hasText(checkParameters)) {
            return true; // 默认检查所有参数
        }

        try {
            List<String> parameters = objectMapper.readValue(checkParameters, new TypeReference<List<String>>() {
            });
            return parameters.contains(parameterName);
        } catch (Exception e) {
            String[] parameters = checkParameters.split(",");
            return Arrays.stream(parameters).map(String::trim).anyMatch(param -> param.equals(parameterName));
        }
    }

    /**
     * 获取客户端IP地址
     *
     * @param request 请求对象
     * @return IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

    /**
     * 更新攻击统计
     *
     * @param attackerIp 攻击者IP
     */
    private void updateAttackStatistics(String attackerIp) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastAttackTime = attackTimeCache.get(attackerIp);

        // 检查时间窗口
        if (lastAttackTime != null && lastAttackTime.plusMinutes(5).isAfter(now)) {
            // 在时间窗口内，增加攻击次数
            attackCountCache.merge(attackerIp, 1, Integer::sum);
        } else {
            // 超出时间窗口，重置计数
            attackCountCache.put(attackerIp, 1);
        }

        attackTimeCache.put(attackerIp, now);

        // 检查是否超过阈值
        Integer attackCount = attackCountCache.get(attackerIp);
        if (attackCount != null && attackCount >= 10) { // 默认阈值
            handleAttackThresholdExceeded(attackerIp, attackCount);
        }
    }

    /**
     * 处理攻击阈值超出
     *
     * @param attackerIp  攻击者IP
     * @param attackCount 攻击次数
     */
    private void handleAttackThresholdExceeded(String attackerIp, Integer attackCount) {
        log.warn("Attack threshold exceeded for IP: {}, count: {}", attackerIp, attackCount);

        // 这里可以集成黑名单服务，将IP加入黑名单
        // blackWhiteListService.addToBlacklist(attackerIp,
        // BlackWhiteList.MatchType.EXACT);
    }
}
