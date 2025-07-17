package com.chua.starter.plugin.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * XSS攻击日志实体
 * 
 * @author CH
 * @since 2025/1/16
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class PluginXssAttackLog {

    /**
     * 主键ID
     */
    private Long pluginXssAttackLogId;

    /**
     * 攻击者IP地址
     */
    private String pluginXssAttackLogAttackerIp;

    /**
     * 请求URL
     */
    private String pluginXssAttackLogRequestUrl;

    /**
     * HTTP方法
     */
    private String pluginXssAttackLogHttpMethod;

    /**
     * 攻击参数名
     */
    private String pluginXssAttackLogParameterName;

    /**
     * 原始攻击内容
     */
    private String pluginXssAttackLogOriginalContent;

    /**
     * 过滤后内容
     */
    private String pluginXssAttackLogFilteredContent;

    /**
     * 攻击类型
     */
    private AttackType pluginXssAttackLogAttackType;

    /**
     * 危险等级
     */
    private RiskLevel pluginXssAttackLogRiskLevel;

    /**
     * 处理动作
     */
    private String pluginXssAttackLogAction;

    /**
     * User-Agent
     */
    private String pluginXssAttackLogUserAgent;

    /**
     * Referer
     */
    private String pluginXssAttackLogReferer;

    /**
     * 会话ID
     */
    private String pluginXssAttackLogSessionId;

    /**
     * 用户ID（如果已登录）
     */
    private String pluginXssAttackLogUserId;

    /**
     * 攻击时间
     */
    private LocalDateTime pluginXssAttackLogAttackTime;

    /**
     * 是否已处理
     */
    private Boolean pluginXssAttackLogHandled = false;

    /**
     * 处理备注
     */
    private String pluginXssAttackLogRemark;

    /**
     * 攻击类型枚举
     */
    public enum AttackType {
        /**
         * 脚本注入
         */
        SCRIPT_INJECTION,
        
        /**
         * HTML注入
         */
        HTML_INJECTION,
        
        /**
         * 事件处理器注入
         */
        EVENT_HANDLER,
        
        /**
         * CSS表达式注入
         */
        CSS_EXPRESSION,
        
        /**
         * JavaScript伪协议
         */
        JAVASCRIPT_PROTOCOL,
        
        /**
         * 其他
         */
        OTHER
    }

    /**
     * 危险等级枚举
     */
    public enum RiskLevel {
        /**
         * 低危
         */
        LOW,
        
        /**
         * 中危
         */
        MEDIUM,
        
        /**
         * 高危
         */
        HIGH,
        
        /**
         * 严重
         */
        CRITICAL
    }

    public void onCreate() {
        pluginXssAttackLogAttackTime = LocalDateTime.now();
    }

    /**
     * 构造函数
     */
    public PluginXssAttackLog() {
        onCreate();
    }

    /**
     * 构造函数
     * 
     * @param attackerIp 攻击者IP
     * @param requestUrl 请求URL
     * @param parameterName 参数名
     * @param originalContent 原始内容
     */
    public PluginXssAttackLog(String attackerIp, String requestUrl, String parameterName, String originalContent) {
        this.pluginXssAttackLogAttackerIp = attackerIp;
        this.pluginXssAttackLogRequestUrl = requestUrl;
        this.pluginXssAttackLogParameterName = parameterName;
        this.pluginXssAttackLogOriginalContent = originalContent;
        onCreate();
    }

    /**
     * 获取攻击摘要
     * 
     * @return 攻击摘要
     */
    public String getAttackSummary() {
        return String.format("[%s] %s from %s at %s", 
            pluginXssAttackLogRiskLevel != null ? pluginXssAttackLogRiskLevel : "UNKNOWN",
            pluginXssAttackLogAttackType != null ? pluginXssAttackLogAttackType : "UNKNOWN",
            pluginXssAttackLogAttackerIp,
            pluginXssAttackLogAttackTime);
    }

    /**
     * 检查是否为高危攻击
     * 
     * @return 是否高危
     */
    public boolean isHighRisk() {
        return pluginXssAttackLogRiskLevel == RiskLevel.HIGH || 
               pluginXssAttackLogRiskLevel == RiskLevel.CRITICAL;
    }
}
