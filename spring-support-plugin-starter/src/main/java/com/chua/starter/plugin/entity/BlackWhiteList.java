package com.chua.starter.plugin.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 黑白名单实体
 * 
 * @author CH
 * @since 2025/1/16
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class BlackWhiteList {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 名单类型：BLACKLIST、WHITELIST
     */
    private ListType listType;

    /**
     * 名单值（IP地址、API路径等）
     */
    private String listValue;

    /**
     * 匹配类型：EXACT、WILDCARD、REGEX
     */
    private MatchType matchType = MatchType.EXACT;

    /**
     * 是否启用
     */
    private Boolean enabled = true;

    /**
     * 优先级（数字越小优先级越高）
     */
    private Integer priority = 0;

    /**
     * 描述
     */
    private String description;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    private LocalDateTime updatedTime;

    /**
     * 创建者
     */
    private String createdBy;

    /**
     * 更新者
     */
    private String updatedBy;

    /**
     * 过期时间（可选）
     */
    private LocalDateTime expireTime;

    /**
     * 名单类型枚举
     */
    public enum ListType {
        /**
         * 黑名单
         */
        BLACKLIST,

        /**
         * 白名单
         */
        WHITELIST
    }

    /**
     * 匹配类型枚举
     */
    public enum MatchType {
        /**
         * 精确匹配
         */
        EXACT,

        /**
         * 通配符匹配
         */
        WILDCARD,

        /**
         * 正则表达式匹配
         */
        REGEX
    }

    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdTime = now;
        updatedTime = now;
    }

    public void onUpdate() {
        updatedTime = LocalDateTime.now();
    }

    /**
     * 构造函数
     */
    public BlackWhiteList() {
    }

    /**
     * 构造函数
     * 
     * @param listType  名单类型
     * @param listValue 名单值
     * @param matchType 匹配类型
     */
    public BlackWhiteList(ListType listType, String listValue, MatchType matchType) {
        this.listType = listType;
        this.listValue = listValue;
        this.matchType = matchType;
        LocalDateTime now = LocalDateTime.now();
        this.createdTime = now;
        this.updatedTime = now;
    }

    /**
     * 创建黑名单条目
     * 
     * @param value     值
     * @param matchType 匹配类型
     * @return 黑名单条目
     */
    public static BlackWhiteList createBlacklist(String value, MatchType matchType) {
        return new BlackWhiteList(ListType.BLACKLIST, value, matchType);
    }

    /**
     * 创建白名单条目
     * 
     * @param value     值
     * @param matchType 匹配类型
     * @return 白名单条目
     */
    public static BlackWhiteList createWhitelist(String value, MatchType matchType) {
        return new BlackWhiteList(ListType.WHITELIST, value, matchType);
    }

    /**
     * 获取唯一键
     * 
     * @return 唯一键
     */
    public String getUniqueKey() {
        return listType + ":" + listValue;
    }

    /**
     * 检查是否有效
     * 
     * @return 是否有效
     */
    public boolean isValid() {
        return listType != null && listValue != null && !listValue.trim().isEmpty() && matchType != null
                && enabled != null;
    }

    /**
     * 检查是否过期
     * 
     * @return 是否过期
     */
    public boolean isExpired() {
        return expireTime != null && LocalDateTime.now().isAfter(expireTime);
    }

    /**
     * 检查值是否匹配
     * 
     * @param value 要检查的值
     * @return 是否匹配
     */
    public boolean matches(String value) {
        if (value == null || listValue == null) {
            return false;
        }

        switch (matchType) {
        case EXACT:
            return listValue.equals(value);
        case WILDCARD:
            return matchWildcard(listValue, value);
        case REGEX:
            try {
                return value.matches(listValue);
            } catch (Exception e) {
                return false;
            }
        default:
            return false;
        }
    }

    /**
     * 通配符匹配
     * 
     * @param pattern 模式
     * @param value   值
     * @return 是否匹配
     */
    private boolean matchWildcard(String pattern, String value) {
        // 简单的通配符匹配实现
        String regex = pattern.replace(".", "\\.").replace("*", ".*").replace("?", ".");
        try {
            return value.matches(regex);
        } catch (Exception e) {
            return false;
        }
    }
}
