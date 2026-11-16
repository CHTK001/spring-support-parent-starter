package com.chua.starter.datasearch.provider.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 厂商限制口径（一条登记项）。
 *
 * <p>只描述「登记表里写了什么」，不代表已经探测过：{@link #limits} 里的数值只有
 * {@link #verified} 为 true 时才是按官方原文核对过的；{@link #doc} 是官方文档入口，
 * 多数是模型 / 接口文档，不是限制页。</p>
 *
 * @author CH
 * @since 4.0.0.55
 */
@Data
public class ProviderLimitInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 厂商键（与 models.dev 对齐，如 openai、alibaba、moonshotai）
     */
    private String id;

    /**
     * 展示名
     */
    private String name;

    /**
     * 官方文档入口（可空）
     */
    private String doc;

    /**
     * 数值 / 计费口径是否按官方原文核对过
     */
    private boolean verified;

    /**
     * 已登记的口径：concurrency / requests_per_minute / billing_basis / model_concurrency 等
     */
    private Map<String, Object> limits;

    /**
     * 补充说明
     */
    private String notes;

    /**
     * 精确匹配的 provider id
     */
    private List<String> aliases;

    /**
     * 主机名匹配
     */
    private List<String> hosts;
}
