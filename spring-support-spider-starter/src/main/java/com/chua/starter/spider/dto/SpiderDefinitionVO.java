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
    * 主键 标识
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
    * 执行类型（ONCE / 调度）
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

    /**
    * 获取蜘蛛标识
    *
    * @return 获取蜘蛛id的结果
     */
    public Long getSpiderId() {
        return spiderId;
    }

    /**
    * 设置蜘蛛标识
    *
    * @param spiderId 蜘蛛标识
     */
    public void setSpiderId(Long spiderId) {
        this.spiderId = spiderId;
    }

    /**
    * 获取蜘蛛编码
    *
    * @return 获取蜘蛛编码的结果
     */
    public String getSpiderCode() {
        return spiderCode;
    }

    /**
    * 设置蜘蛛编码
    *
    * @param spiderCode 蜘蛛编码
     */
    public void setSpiderCode(String spiderCode) {
        this.spiderCode = spiderCode;
    }

    /**
    * 获取蜘蛛名称
    *
    * @return 获取蜘蛛名称的结果
     */
    public String getSpiderName() {
        return spiderName;
    }

    /**
    * 设置蜘蛛名称
    *
    * @param spiderName 蜘蛛名称
     */
    public void setSpiderName(String spiderName) {
        this.spiderName = spiderName;
    }

    /**
    * 获取蜘蛛entryurl
    *
    * @return 获取蜘蛛entryurl的结果
     */
    public String getSpiderEntryUrl() {
        return spiderEntryUrl;
    }

    /**
    * 设置蜘蛛entryurl
    *
    * @param spiderEntryUrl 蜘蛛entryurl
     */
    public void setSpiderEntryUrl(String spiderEntryUrl) {
        this.spiderEntryUrl = spiderEntryUrl;
    }

    /**
    * 获取蜘蛛site主机
    *
    * @return 获取蜘蛛site主机的结果
     */
    public String getSpiderSiteHost() {
        return spiderSiteHost;
    }

    /**
    * 设置蜘蛛site主机
    *
    * @param spiderSiteHost 蜘蛛site主机
     */
    public void setSpiderSiteHost(String spiderSiteHost) {
        this.spiderSiteHost = spiderSiteHost;
    }

    /**
    * 获取蜘蛛执行器类型
    *
    * @return 获取蜘蛛执行器类型的结果
     */
    public String getSpiderExecutorType() {
        return spiderExecutorType;
    }

    /**
    * 设置蜘蛛执行器类型
    *
    * @param spiderExecutorType 蜘蛛执行器类型
     */
    public void setSpiderExecutorType(String spiderExecutorType) {
        this.spiderExecutorType = spiderExecutorType;
    }

    /**
    * 获取蜘蛛执行器rounds
    *
    * @return 获取蜘蛛执行器rounds的结果
     */
    public Integer getSpiderExecutorRounds() {
        return spiderExecutorRounds;
    }

    /**
    * 设置蜘蛛执行器rounds
    *
    * @param spiderExecutorRounds 蜘蛛执行器rounds
     */
    public void setSpiderExecutorRounds(Integer spiderExecutorRounds) {
        this.spiderExecutorRounds = spiderExecutorRounds;
    }

    /**
    * 获取蜘蛛cron
    *
    * @return 获取蜘蛛cron的结果
     */
    public String getSpiderCron() {
        return spiderCron;
    }

    /**
    * 设置蜘蛛cron
    *
    * @param spiderCron 蜘蛛cron
     */
    public void setSpiderCron(String spiderCron) {
        this.spiderCron = spiderCron;
    }

    /**
    * 获取蜘蛛collectdeepurl
    *
    * @return 获取蜘蛛collectdeepurl的结果
     */
    public Integer getSpiderCollectDeepUrl() {
        return spiderCollectDeepUrl;
    }

    /**
    * 设置蜘蛛collectdeepurl
    *
    * @param spiderCollectDeepUrl 蜘蛛collectdeepurl
     */
    public void setSpiderCollectDeepUrl(Integer spiderCollectDeepUrl) {
        this.spiderCollectDeepUrl = spiderCollectDeepUrl;
    }

    /**
    * 获取蜘蛛最大值深度
    *
    * @return 获取蜘蛛最大深度的结果
     */
    public Integer getSpiderMaxDepth() {
        return spiderMaxDepth;
    }

    /**
    * 设置蜘蛛最大值深度
    *
    * @param spiderMaxDepth 蜘蛛最大深度
     */
    public void setSpiderMaxDepth(Integer spiderMaxDepth) {
        this.spiderMaxDepth = spiderMaxDepth;
    }

    /**
    * 获取蜘蛛最大值Pages
    *
    * @return 获取蜘蛛最大pages的结果
     */
    public Integer getSpiderMaxPages() {
        return spiderMaxPages;
    }

    /**
    * 设置蜘蛛最大值Pages
    *
    * @param spiderMaxPages 蜘蛛最大pages
     */
    public void setSpiderMaxPages(Integer spiderMaxPages) {
        this.spiderMaxPages = spiderMaxPages;
    }

    /**
    * 获取蜘蛛去重strategy
    *
    * @return 获取蜘蛛去重strategy的结果
     */
    public String getSpiderDedupStrategy() {
        return spiderDedupStrategy;
    }

    /**
    * 设置蜘蛛去重strategy
    *
    * @param spiderDedupStrategy 蜘蛛去重strategy
     */
    public void setSpiderDedupStrategy(String spiderDedupStrategy) {
        this.spiderDedupStrategy = spiderDedupStrategy;
    }

    /**
    * 获取蜘蛛urlstorage类型
    *
    * @return 获取蜘蛛urlstorage类型的结果
     */
    public String getSpiderUrlStorageType() {
        return spiderUrlStorageType;
    }

    /**
    * 设置蜘蛛urlstorage类型
    *
    * @param spiderUrlStorageType 蜘蛛urlstorage类型
     */
    public void setSpiderUrlStorageType(String spiderUrlStorageType) {
        this.spiderUrlStorageType = spiderUrlStorageType;
    }

    /**
    * 获取蜘蛛状态
    *
    * @return 获取蜘蛛状态的结果
     */
    public Integer getSpiderStatus() {
        return spiderStatus;
    }

    /**
    * 设置蜘蛛状态
    *
    * @param spiderStatus 蜘蛛状态
     */
    public void setSpiderStatus(Integer spiderStatus) {
        this.spiderStatus = spiderStatus;
    }

    /**
    * 获取蜘蛛评论
    *
    * @return 获取蜘蛛评论的结果
     */
    public String getSpiderRemark() {
        return spiderRemark;
    }

    /**
    * 设置蜘蛛评论
    *
    * @param spiderRemark 蜘蛛评论
     */
    public void setSpiderRemark(String spiderRemark) {
        this.spiderRemark = spiderRemark;
    }

    /**
    * 获取创建时间
    *
    * @return 获取创建时间的结果
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
    * 设置创建时间
    *
    * @param createTime 创建时间
     */
    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    /**
    * 获取更新时间
    *
    * @return 获取更新时间的结果
     */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    /**
    * 设置更新时间
    *
    * @param updateTime 更新时间
     */
    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}