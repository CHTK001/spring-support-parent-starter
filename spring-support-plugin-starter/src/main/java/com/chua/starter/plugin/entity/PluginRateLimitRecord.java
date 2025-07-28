package com.chua.starter.plugin.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dizitart.no2.repository.annotations.Entity;

import java.time.LocalDateTime;

/**
 * 限流记录实体
 * 
 * @author CH
 * @since 2025/1/16
 */
@Data
@Entity
@Table(name = "rate_limit_record")
@EqualsAndHashCode(callSuper = false)
public class PluginRateLimitRecord {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 限流键（IP或API路径的唯一标识）
     */
    @Column(name = "limit_key", nullable = false, length = 255)
    private String limitKey;

    /**
     * IP地址
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * API路径
     */
    @Column(name = "api_path", length = 500)
    private String apiPath;

    /**
     * HTTP方法
     */
    @Column(name = "http_method", length = 10)
    private String httpMethod;

    /**
     * 用户代理
     */
    @Column(name = "user_agent", length = 1000)
    private String userAgent;

    /**
     * 时间窗口开始时间
     */
    @Column(name = "window_start", nullable = false)
    private LocalDateTime windowStart;

    /**
     * 时间窗口结束时间
     */
    @Column(name = "window_end", nullable = false)
    private LocalDateTime windowEnd;

    /**
     * 当前窗口内的请求计数
     */
    @Column(name = "request_count", nullable = false)
    private Integer requestCount = 0;

    /**
     * 限流规则的每秒允许请求数
     */
    @Column(name = "permits_per_second", nullable = false)
    private Integer permitsPerSecond;

    /**
     * 是否被限流
     */
    @Column(name = "is_limited", nullable = false)
    private Boolean isLimited = false;

    /**
     * 限流算法类型
     */
    @Column(name = "algorithm_type", length = 50)
    private String algorithmType;

    /**
     * 创建时间
     */
    @Column(name = "created_time", nullable = false)
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    /**
     * 过期时间
     */
    @Column(name = "expire_time")
    private LocalDateTime expireTime;

    /**
     * 备注信息
     */
    @Column(name = "remark", length = 500)
    private String remark;

    @PrePersist
    protected void onCreate() {
        createdTime = LocalDateTime.now();
        updatedTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedTime = LocalDateTime.now();
    }

    /**
     * 构造函数
     */
    public PluginRateLimitRecord() {
    }

    /**
     * 构造函数
     * 
     * @param limitKey 限流键
     * @param ipAddress IP地址
     * @param apiPath API路径
     */
    public PluginRateLimitRecord(String limitKey, String ipAddress, String apiPath) {
        this.limitKey = limitKey;
        this.ipAddress = ipAddress;
        this.apiPath = apiPath;
        this.createdTime = LocalDateTime.now();
        this.updatedTime = LocalDateTime.now();
    }

    /**
     * 增加请求计数
     */
    public void incrementRequestCount() {
        this.requestCount++;
        this.updatedTime = LocalDateTime.now();
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
     * 检查是否在当前时间窗口内
     * 
     * @param now 当前时间
     * @return 是否在窗口内
     */
    public boolean isInWindow(LocalDateTime now) {
        return now.isAfter(windowStart) && now.isBefore(windowEnd);
    }

    /**
     * 重置计数器（用于新的时间窗口）
     * 
     * @param windowStart 新窗口开始时间
     * @param windowEnd 新窗口结束时间
     */
    public void resetForNewWindow(LocalDateTime windowStart, LocalDateTime windowEnd) {
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.requestCount = 0;
        this.isLimited = false;
        this.updatedTime = LocalDateTime.now();
    }
}
