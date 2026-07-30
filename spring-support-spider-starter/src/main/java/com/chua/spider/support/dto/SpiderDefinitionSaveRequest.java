package com.chua.spider.support.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建或更新爬虫定义的请求体。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Data
@Schema(description = "爬虫定义保存请求")
public class SpiderDefinitionSaveRequest {

    /**
     * 爬虫业务编码，全局唯一
     */
    @NotBlank
    private String spiderCode;

    /**
     * 爬虫名称
     */
    @NotBlank
    private String spiderName;

    /**
     * 入口 URL
     */
    @NotBlank
    private String spiderEntryUrl;

    /**
     * 站点主机名
     */
    private String spiderSiteHost;

    /**
     * 调度类型，默认 ONCE
     */
    private String spiderExecutorType = "ONCE";

    /**
     * 调度轮次，默认 1
     */
    private Integer spiderExecutorRounds = 1;

    /**
     * 是否深度采集（0=否，1=是），默认 1
     */
    private Integer spiderCollectDeepUrl = 1;

    /**
     * 允许的最大爬取深度，默认 1
     */
    private Integer spiderMaxDepth = 1;

    /**
     * 最大采集页数，默认 50
     */
    private Integer spiderMaxPages = 50;

    /**
     * 去重策略，默认 URL_FINGERPRINT
     */
    private String spiderDedupStrategy = "URL_FINGERPRINT";

    /**
     * URL 存储类型，默认 MEMORY
     */
    private String spiderUrlStorageType = "MEMORY";

    /**
     * 启停状态，默认 1 启用
     */
    private Integer spiderStatus = 1;

    /**
     * 备注
     */
    private String spiderRemark;
}