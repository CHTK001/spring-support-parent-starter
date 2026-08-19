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

    public Long getSpiderId() {
        return spiderId;
    }

    public void setSpiderId(Long spiderId) {
        this.spiderId = spiderId;
    }

    public String getSpiderCode() {
        return spiderCode;
    }

    public void setSpiderCode(String spiderCode) {
        this.spiderCode = spiderCode;
    }

    public String getSpiderName() {
        return spiderName;
    }

    public void setSpiderName(String spiderName) {
        this.spiderName = spiderName;
    }

    public String getSpiderEntryUrl() {
        return spiderEntryUrl;
    }

    public void setSpiderEntryUrl(String spiderEntryUrl) {
        this.spiderEntryUrl = spiderEntryUrl;
    }

    public String getSpiderSiteHost() {
        return spiderSiteHost;
    }

    public void setSpiderSiteHost(String spiderSiteHost) {
        this.spiderSiteHost = spiderSiteHost;
    }

    public String getSpiderExecutorType() {
        return spiderExecutorType;
    }

    public void setSpiderExecutorType(String spiderExecutorType) {
        this.spiderExecutorType = spiderExecutorType;
    }

    public Integer getSpiderExecutorRounds() {
        return spiderExecutorRounds;
    }

    public void setSpiderExecutorRounds(Integer spiderExecutorRounds) {
        this.spiderExecutorRounds = spiderExecutorRounds;
    }

    public String getSpiderCron() {
        return spiderCron;
    }

    public void setSpiderCron(String spiderCron) {
        this.spiderCron = spiderCron;
    }

    public Integer getSpiderCollectDeepUrl() {
        return spiderCollectDeepUrl;
    }

    public void setSpiderCollectDeepUrl(Integer spiderCollectDeepUrl) {
        this.spiderCollectDeepUrl = spiderCollectDeepUrl;
    }

    public Integer getSpiderMaxDepth() {
        return spiderMaxDepth;
    }

    public void setSpiderMaxDepth(Integer spiderMaxDepth) {
        this.spiderMaxDepth = spiderMaxDepth;
    }

    public Integer getSpiderMaxPages() {
        return spiderMaxPages;
    }

    public void setSpiderMaxPages(Integer spiderMaxPages) {
        this.spiderMaxPages = spiderMaxPages;
    }

    public String getSpiderDedupStrategy() {
        return spiderDedupStrategy;
    }

    public void setSpiderDedupStrategy(String spiderDedupStrategy) {
        this.spiderDedupStrategy = spiderDedupStrategy;
    }

    public String getSpiderUrlStorageType() {
        return spiderUrlStorageType;
    }

    public void setSpiderUrlStorageType(String spiderUrlStorageType) {
        this.spiderUrlStorageType = spiderUrlStorageType;
    }

    public Integer getSpiderStatus() {
        return spiderStatus;
    }

    public void setSpiderStatus(Integer spiderStatus) {
        this.spiderStatus = spiderStatus;
    }

    public String getSpiderRemark() {
        return spiderRemark;
    }

    public void setSpiderRemark(String spiderRemark) {
        this.spiderRemark = spiderRemark;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}