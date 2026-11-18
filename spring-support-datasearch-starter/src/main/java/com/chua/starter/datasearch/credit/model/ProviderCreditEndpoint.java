package com.chua.starter.datasearch.credit.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 官方余额接口登记项（一家一条）。
 *
 * <p>把「地址 + 字段表达式 + 币种 + 签名方案」从代码里搬到数据登记表：新增一家只需
 * 加一条 JSON，不用改实现类。{@link #signType} 非空表示需要 AK/SK 签名（阿里云 BSS /
 * 火山 billing / 百度 BCE / 七牛 / 腾讯云 TC3 / 华为云 SDK），为空表示走 Bearer 或自定义请求头。</p>
 *
 * @author CH
 * @since 4.0.0.57
 */
@Data
public class ProviderCreditEndpoint implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 厂商标识（与供应商登记表 / 前端模板库一致）
     */
    private String id;

    /**
     * 展示名
     */
    private String name;

    /**
     * 精确匹配的厂商标识（小写）
     */
    private List<String> aliases;

    /**
     * 主机名匹配（用于按 Key 所属站点判断是否同源）
     */
    private List<String> hosts;

    /**
     * 余额接口地址
     */
    private String url;

    /**
     * 字段表达式（{@code ¥} 人民币、{@code $} 美元、{@code !} 百分比取反）
     */
    private String expression;

    /**
     * 币种（usd / cny / %）
     */
    private String currency;

    /**
     * 签名方案（aliyun-bss / volc / bce / qiniu / tencent / huawei）；为空表示 Bearer
     */
    private String signType;

    /**
     * 请求方法（GET / POST）；为空按 GET
     */
    private String method;

    /**
     * 自定义鉴权请求头（如 nano-gpt 的 x-api-key）；为空按 Authorization + Bearer
     */
    private String authHeader;

    /**
     * 地址与字段是否按官方原文核对过
     */
    private boolean verified;

    /**
     * 补充说明
     */
    private String notes;
}
