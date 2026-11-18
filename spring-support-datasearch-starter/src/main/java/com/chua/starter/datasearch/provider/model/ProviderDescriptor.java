package com.chua.starter.datasearch.provider.model;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 供应商登记项（一家一条）。
 *
 * <p>前端模板库的 200+ 家供应商被归一成这一份描述：名称、说明、协议、默认地址、
 * 别名 / 主机名、models.dev 目录键、能力位（流式 / 异步）、余额口径与限制口径。
 * 限制 / 目录 / 定价 / 余额 / 能力各 SPI 共用它，避免每处各写一套匹配逻辑。</p>
 *
 * <p>字段语义：{@link #creditMode} 为 {@code official} 表示官方余额接口已登记，
 * {@code probe} 表示 OpenAI 兼容探测，{@code none} 表示无远端账户（本地 / 无地址）；
 * {@link #limitsVerified} 只有在限制数值按官方原文核对过时才为 true。</p>
 *
 * @author CH
 * @since 4.0.0.57
 */
@Data
public class ProviderDescriptor implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 厂商标识（与前端模板库 provider id 一致）
     */
    private String id;

    /**
     * 展示名
     */
    private String name;

    /**
     * 供应商说明（可空）
     */
    private String description;

    /**
     * 协议（openai / anthropic / gemini）
     */
    private String protocol;

    /**
     * 默认上游地址（可空；本地 / 无地址的供应商为空）
     */
    private String baseUrl;

    /**
     * 官方文档入口（可空）
     */
    private String doc;

    /**
     * 官网（可空；免登录 / 本地 / 自建等没有对外站点的供应商为空）
     *
     * <p>来源优先级：登记表显式 {@code website} → 上游地址的可注册主域
     * （{@code api.openai.com → openai.com}、{@code llm.chutes.ai → chutes.ai}）
     * → 官方文档站的可注册主域。取不到就保持 {@code null}，不拿上游 API 地址冒充官网
     * —— 那种地址打开是 404。</p>
     */
    private String website;

    /**
     * models.dev 目录键（可空；目录未收录时为空）
     */
    private String modelsDevId;

    /**
     * 精确匹配的别名（小写）
     */
    private List<String> aliases;

    /**
     * 主机名匹配
     */
    private List<String> hosts;

    /**
     * 能力位（流式 / 异步）
     */
    private ProviderCapability capability;

    /**
     * 余额口径：official / probe / none
     */
    private String creditMode;

    /**
     * 余额实现标识：registry / auto / none
     */
    private String creditKind;

    /**
     * 官方余额实现使用的厂商标识（可空）
     */
    private String creditProviderId;

    /**
     * 官方余额币种（usd / cny / %，可空）
     */
    private String creditCurrency;

    /**
     * 限制数值是否按官方原文核对过
     */
    private boolean limitsVerified;
}
