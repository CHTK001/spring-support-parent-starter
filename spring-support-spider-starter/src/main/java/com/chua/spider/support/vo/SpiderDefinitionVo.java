package com.chua.spider.support.vo;

import com.chua.spider.support.entity.SpiderDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 爬虫定义视图对象。
 * <p>用于 {@code /spider/definitions} 等 REST 接口的响应数据。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(description = "爬虫定义视图")
public class SpiderDefinitionVo {

    /**
     * 主键
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
     * 调度类型
     */
    private String spiderExecutorType;

    /**
     * 调度轮次
     */
    private Integer spiderExecutorRounds;

    /**
     * 是否深度采集
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
     * 状态
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
     * 将实体转换为视图对象。
     *
     * @param e 实体
     * @return 视图对象
     */
    public static SpiderDefinitionVo from(SpiderDefinition e) {
        if (e == null) {
            return null;
        }
        SpiderDefinitionVo v = new SpiderDefinitionVo();
        v.spiderId = e.getSpiderId();
        v.spiderCode = e.getSpiderCode();
        v.spiderName = e.getSpiderName();
        v.spiderEntryUrl = e.getSpiderEntryUrl();
        v.spiderSiteHost = e.getSpiderSiteHost();
        v.spiderExecutorType = e.getSpiderExecutorType();
        v.spiderExecutorRounds = e.getSpiderExecutorRounds();
        v.spiderCollectDeepUrl = e.getSpiderCollectDeepUrl();
        v.spiderMaxDepth = e.getSpiderMaxDepth();
        v.spiderMaxPages = e.getSpiderMaxPages();
        v.spiderDedupStrategy = e.getSpiderDedupStrategy();
        v.spiderUrlStorageType = e.getSpiderUrlStorageType();
        v.spiderStatus = e.getSpiderStatus();
        v.spiderRemark = e.getSpiderRemark();
        v.createTime = e.getCreateTime();
        v.updateTime = e.getUpdateTime();
        return v;
    }
}