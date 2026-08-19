package com.chua.starter.spider.dto;

import java.time.LocalDateTime;

/**
 * 爬虫定义前端视图对象。
 *
 * <p>字段命名对齐前端 {@code pages/spider} 的 {@code SpiderDefinition} 类型，
 * 由后端实体 {@code SpiderDefinition} 映射而来，避免前后端字段差异。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public class SpiderDefinitionVO {

    /**
     * 主键 ID
     */
    private Long spiderId;

    /**
     * 爬虫编码
     */
    private String spiderCode;

    /**
     * 爬虫名称
     */
    private String spiderName;

    /**
     * 入口 URL
     */
    private String spiderEntryUrl;

    /**
     * 站点主机
     */
    private String spiderSiteHost;

    /**
     * 执行类型（ONCE / SCHEDULE）
     */
    private String spiderExecutorType;

    /**
     * 执行轮次
     */
    private Integer spiderExecutorRounds;

    /**
     * 定时 cron 表达式
     */
    private String spiderCron;

    /**
     * 是否深度采集（1/0）
     */
    private Integer spiderCollectDeepUrl;

    /**
     * 最大深度
     */
    private Integer spiderMaxDepth;

    /**
     * 最大页数
     */
    private Integer spiderMaxPages;

    /**
     * 去重策略
     */
    private String spiderDedupStrategy;

    /**
     * URL 存储类型
     */
    private String spiderUrlStorageType;

    /**
     * 状态（1 启用 / 0 禁用）
     */
    private Integer spiderStatus;

    /**
     * 备注
     */
    private String spiderRemark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /** 获取SpiderId */
    public Long getSpiderId() {
        return spiderId;
    }

    /** 设置SpiderId */
    public void setSpiderId(Long spiderId) {
        this.spiderId = spiderId;
    }

    /** 获取SpiderCode */
    public String getSpiderCode() {
        return spiderCode;
    }

    /** 设置SpiderCode */
    public void setSpiderCode(String spiderCode) {
        this.spiderCode = spiderCode;
    }

    /** 获取SpiderName */
    public String getSpiderName() {
        return spiderName;
    }

    /** 设置SpiderName */
    public void setSpiderName(String spiderName) {
        this.spiderName = spiderName;
    }

    /** 获取SpiderEntryUrl */
    public String getSpiderEntryUrl() {
        return spiderEntryUrl;
    }

    /** 设置SpiderEntryUrl */
    public void setSpiderEntryUrl(String spiderEntryUrl) {
        this.spiderEntryUrl = spiderEntryUrl;
    }

    /** 获取SpiderSiteHost */
    public String getSpiderSiteHost() {
        return spiderSiteHost;
    }

    /** 设置SpiderSiteHost */
    public void setSpiderSiteHost(String spiderSiteHost) {
        this.spiderSiteHost = spiderSiteHost;
    }

    /** 获取SpiderExecutorType */
    public String getSpiderExecutorType() {
        return spiderExecutorType;
    }

    /** 设置SpiderExecutorType */
    public void setSpiderExecutorType(String spiderExecutorType) {
        this.spiderExecutorType = spiderExecutorType;
    }

    /** 获取SpiderExecutorRounds */
    public Integer getSpiderExecutorRounds() {
        return spiderExecutorRounds;
    }

    /** 设置SpiderExecutorRounds */
    public void setSpiderExecutorRounds(Integer spiderExecutorRounds) {
        this.spiderExecutorRounds = spiderExecutorRounds;
    }

    /** 获取SpiderCron */
    public String getSpiderCron() {
        return spiderCron;
    }

    /** 设置SpiderCron */
    public void setSpiderCron(String spiderCron) {
        this.spiderCron = spiderCron;
    }

    /** 获取SpiderCollectDeepUrl */
    public Integer getSpiderCollectDeepUrl() {
        return spiderCollectDeepUrl;
    }

    /** 设置SpiderCollectDeepUrl */
    public void setSpiderCollectDeepUrl(Integer spiderCollectDeepUrl) {
        this.spiderCollectDeepUrl = spiderCollectDeepUrl;
    }

    /** 获取Spider最大值Depth */
    public Integer getSpiderMaxDepth() {
        return spiderMaxDepth;
    }

    /** 设置Spider最大值Depth */
    public void setSpiderMaxDepth(Integer spiderMaxDepth) {
        this.spiderMaxDepth = spiderMaxDepth;
    }

    /** 获取Spider最大值Pages */
    public Integer getSpiderMaxPages() {
        return spiderMaxPages;
    }

    /** 设置Spider最大值Pages */
    public void setSpiderMaxPages(Integer spiderMaxPages) {
        this.spiderMaxPages = spiderMaxPages;
    }

    /** 获取SpiderDedupStrategy */
    public String getSpiderDedupStrategy() {
        return spiderDedupStrategy;
    }

    /** 设置SpiderDedupStrategy */
    public void setSpiderDedupStrategy(String spiderDedupStrategy) {
        this.spiderDedupStrategy = spiderDedupStrategy;
    }

    /** 获取SpiderUrlStorageType */
    public String getSpiderUrlStorageType() {
        return spiderUrlStorageType;
    }

    /** 设置SpiderUrlStorageType */
    public void setSpiderUrlStorageType(String spiderUrlStorageType) {
        this.spiderUrlStorageType = spiderUrlStorageType;
    }

    /** 获取SpiderStatus */
    public Integer getSpiderStatus() {
        return spiderStatus;
    }

    /** 设置SpiderStatus */
    public void setSpiderStatus(Integer spiderStatus) {
        this.spiderStatus = spiderStatus;
    }

    /** 获取SpiderRemark */
    public String getSpiderRemark() {
        return spiderRemark;
    }

    /** 设置SpiderRemark */
    public void setSpiderRemark(String spiderRemark) {
        this.spiderRemark = spiderRemark;
    }

    /** 获取创建Time */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /** 设置创建Time */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    /** 获取更新Time */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    /** 设置更新Time */
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}