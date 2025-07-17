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
public class XssAttackLog {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 攻击者IP地址
     */
    private String attackerIp;

    /**
     * 请求URL
     */
    private String requestUrl;

    /**
     * HTTP方法
     */
    private String httpMethod;

    /**
     * 攻击参数名
     */
    private String parameterName;

    /**
     * 原始攻击内容
     */
    private String originalContent;

    /**
     * 过滤后内容
     */
    private String filteredContent;

    /**
     * 攻击类型
     */
    private AttackType attackType;

    /**
     * 危险等级
     */
    private RiskLevel riskLevel;

    /**
     * 处理动作
     */
    private String action;

    /**
     * User-Agent
     */
    private String userAgent;

    /**
     * Referer
     */
    private String referer;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 用户ID（如果已登录）
     */
    private String userId;

    /**
     * 攻击时间
     */
    private LocalDateTime attackTime;

    /**
     * 是否已处理
     */
    private Boolean handled = false;

    /**
     * 处理备注
     */
    private String remark;

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
        attackTime = LocalDateTime.now();
    }

    /**
     * 构造函数
     */
    public XssAttackLog() {
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
    public XssAttackLog(String attackerIp, String requestUrl, String parameterName, String originalContent) {
        this.attackerIp = attackerIp;
        this.requestUrl = requestUrl;
        this.parameterName = parameterName;
        this.originalContent = originalContent;
        onCreate();
    }

    /**
     * 获取攻击摘要
     * 
     * @return 攻击摘要
     */
    public String getAttackSummary() {
        return String.format("[%s] %s from %s at %s", 
            riskLevel != null ? riskLevel : "UNKNOWN",
            attackType != null ? attackType : "UNKNOWN",
            attackerIp,
            attackTime);
    }

    /**
     * 检查是否为高危攻击
     * 
     * @return 是否高危
     */
    public boolean isHighRisk() {
        return riskLevel == RiskLevel.HIGH || riskLevel == RiskLevel.CRITICAL;
    }
}
