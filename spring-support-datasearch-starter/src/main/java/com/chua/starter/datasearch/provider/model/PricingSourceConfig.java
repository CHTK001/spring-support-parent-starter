package com.chua.starter.datasearch.provider.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 官方价格页抓取配置。
 *
 * <p>价格页结构差异只写在 {@code provider-pricing-rules.json} 的 {@code source} 节点里，
 * Java 侧只保留四种通用读取方式，不再为每家厂商新增一个抓取类：</p>
 * <ul>
 *   <li>{@code html-table}：标准 {@code <table>}，按表头映射计费项；</li>
 *   <li>{@code html-grid}：Tailwind / div 网格，用 {@code role="row"} 标记行；</li>
 *   <li>{@code html-cards}：模型卡片，标题 + 价格行；</li>
 *   <li>{@code named-regex}：正文重复块，命名捕获组就是计费项。</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.56
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PricingSourceConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 读取方式：html-table / html-grid / html-cards / named-regex
     */
    private String type;

    /**
     * 官方价格页；为空时使用登记项的 doc
     */
    private String url;

    /**
     * 表格 / 网格 / 卡片选择器（可选，默认按类型取通用选择器）
     */
    private String tableSelector;

    /**
     * 行选择器（html-grid 使用，默认 [role=row]）
     */
    private String rowSelector;

    /**
     * 卡片选择器（html-cards 使用）
     */
    private String cardSelector;

    /**
     * 卡片标题选择器（html-cards 使用，默认 h3）
     */
    private String titleSelector;

    /**
     * 卡片价格行选择器（html-cards 使用）
     */
    private String priceRowSelector;

    /**
     * 卡片价格标签选择器（html-cards 使用）
     */
    private String labelSelector;

    /**
     * 卡片价格值选择器（html-cards 使用）
     */
    private String valueSelector;

    /**
     * 官方页面模型名 → 目录模型名前缀（如 Anthropic 的 {@code Claude }）
     */
    private String modelPrefix;

    /**
     * 官方页面模型名 → 目录模型名后缀
     */
    private String modelSuffix;

    /**
     * 从官方模型名里去掉的前缀（如 Command Code 的 {@code Tencent }）
     */
    private List<String> stripPrefixes = new ArrayList<>();

    /**
     * 从官方模型名里去掉的后缀（如 FREE 角标、{@code (latest)}）
     */
    private List<String> stripSuffixes = new ArrayList<>();

    /**
     * 币种（USD / CNY）
     */
    private String currency = "USD";

    /**
     * 每 N 个单位计价，默认每百万 token
     */
    private BigDecimal quantity = new BigDecimal("1000000");

    /**
     * 表头 / 标签 → 计费项。键会做小写、去空格和斜杠后比较，
     * 因此 {@code Input/M} 与 {@code Input} 可共用同一个配置。
     */
    private Map<String, String> columns = new LinkedHashMap<>();

    /**
     * named-regex 的正文正则；命名组 {@code model} 是模型名，其余命名组是计费项。
     */
    private String pattern;

    /**
     * named-regex 捕获组名 → 计费项（Java 命名组不允许下划线，所以这里做一层映射）。
     */
    private Map<String, String> groupItems = new LinkedHashMap<>();

    /**
     * 规则备注；为空时写「官方价格页实时抓取」。
     */
    private String note;

    /**
     * 是否把官方页面的 Free 写成 0；默认写入。
     */
    private boolean includeFree = true;

    /**
     * 单位（默认 token；按次价格页可写 image / request 等）
     */
    private String unit = "token";
}
