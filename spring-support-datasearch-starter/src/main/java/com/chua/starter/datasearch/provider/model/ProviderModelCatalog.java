package com.chua.starter.datasearch.provider.model;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 一家厂商的模型目录。
 *
 * @author CH
 * @since 4.0.0.55
 */
@Data
public class ProviderModelCatalog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 厂商键
     */
    private String providerId;

    /**
     * 展示名
     */
    private String name;

    /**
     * 官方文档入口
     */
    private String doc;

    /**
     * 数据源标识（models.dev 等）
     */
    private String source;

    /**
     * 模型清单
     */
    private List<ProviderModelInfo> models = new ArrayList<>();

    /**
     * 峰谷 / 按 N 次定价规则（官方价格页核对；可空）
     */
    private List<ProviderPricingRule> pricingRules = new ArrayList<>();

    /**
     * 本次规则是否来自点击同步时的实时抓取
     */
    private Boolean pricingLive;

    /**
     * 实时抓取时间（ISO-8601）
     */
    private String pricingFetchedAt;

    /**
     * 实时抓取失败原因；成功或未尝试时为空
     */
    private String pricingError;
}
