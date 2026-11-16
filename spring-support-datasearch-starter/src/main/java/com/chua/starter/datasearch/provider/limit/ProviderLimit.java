package com.chua.starter.datasearch.provider.limit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单个提供商的限制快照：并发 / 请求频率 / 配额 / 计费口径。
 *
 * <p>字段刻意全部可空：不同来源能拿到的维度不同（官方文档写并发、网关响应头写
 * RPM/TPM、计费页写按字符或按张计价），<b>拿不到的维度保持 {@code null}</b>，
 * 不写 0、不猜数值。前端按 null 显示「未公布」，而不是把 0 当成真实限制。</p>
 *
 * <p>来源优先级与判定见 {@link ProviderLimitCollector}。{@code verified} 只表示
 * 「数值经过官方文档逐项核对」，{@code false} 表示文档只说明了限制机制
 * （按账户等级 / 按响应头动态下发），本次没有逐档取数。</p>
 *
 * <p>本类属于「<b>怎么拿</b>」的机制侧产物（{@link ProviderLimitMechanism} 的返回值），
 * 与「<b>去哪拿</b>」的数据侧 {@link com.chua.starter.datasearch.provider.model.ProviderLimitInfo}
 * （登记表里写了什么）区分开：前者是本次探测的快照，后者是静态登记项。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderLimit implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 来源机制：manual / doc / header
     */
    private String kind;

    /**
     * 来源标识（预设名、响应头机制名或 manual）
     */
    private String source;

    /**
     * 官方限制 / 计费文档地址
     */
    private String sourceUrl;

    /**
     * 采集时间（毫秒）
     */
    private Long capturedAt;

    /**
     * 数值是否经官方文档逐项核对
     */
    private Boolean verified;

    /**
     * 账号级并发上限
     */
    private Integer concurrency;

    /**
     * 每分钟请求数上限（RPM）
     */
    private Integer requestsPerMinute;

    /**
     * 每小时请求数上限
     */
    private Integer requestsPerHour;

    /**
     * 每天请求数上限
     */
    private Integer requestsPerDay;

    /**
     * 每分钟 token 上限（TPM）
     */
    private Integer tokensPerMinute;

    /**
     * 每天 token 上限
     */
    private Integer tokensPerDay;

    /**
     * 月度请求配额
     */
    private Long monthlyRequestQuota;

    /**
     * 月度 token 配额
     */
    private Long monthlyTokenQuota;

    /**
     * 计费口径（按 token / 按字符 / 按时长 / 按张 / 按次）
     */
    private String billingBasis;

    /**
     * 计费币种
     */
    private String currency;

    /**
     * 补充说明（官方口径原文摘要或未收录原因）
     */
    private String notes;

    /**
     * 按模型区分的并发上限（如 DeepSeek 的 deepseek-flash / deepseek-v4-pro）
     */
    private Map<String, Integer> modelConcurrency;

    /**
     * 动态观测到的原始限制信息（响应头等）
     */
    private Map<String, Object> observed;

    /**
     * 是否至少拿到一个有效维度。
     *
     * @return true 表示有可展示/可落库的内容
     */
    public boolean hasValues() {
        return concurrency != null
                || requestsPerMinute != null
                || requestsPerHour != null
                || requestsPerDay != null
                || tokensPerMinute != null
                || tokensPerDay != null
                || monthlyRequestQuota != null
                || monthlyTokenQuota != null
                || billingBasis != null
                || (notes != null && !notes.isBlank())
                || (modelConcurrency != null && !modelConcurrency.isEmpty())
                || (observed != null && !observed.isEmpty());
    }

    /**
     * 转成可落 {@code extra} JSON 的 Map（去掉 null 值，保留 false/0）。
     *
     * <p>键名固定用下划线小写，与 {@code extra.limits_manual} 的录入键、
     * 前端读取键保持同一套写法；不用 Jackson 默认命名，避免同一份数据
     * 在「写进 extra」和「手工填」两条路上出现两套键名。</p>
     *
     * @return 有序 Map
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>(24);
        put(map, "kind", kind);
        put(map, "source", source);
        put(map, "source_url", sourceUrl);
        put(map, "captured_at", capturedAt);
        put(map, "verified", verified);
        put(map, "concurrency", concurrency);
        put(map, "requests_per_minute", requestsPerMinute);
        put(map, "requests_per_hour", requestsPerHour);
        put(map, "requests_per_day", requestsPerDay);
        put(map, "tokens_per_minute", tokensPerMinute);
        put(map, "tokens_per_day", tokensPerDay);
        put(map, "monthly_request_quota", monthlyRequestQuota);
        put(map, "monthly_token_quota", monthlyTokenQuota);
        put(map, "billing_basis", billingBasis);
        put(map, "currency", currency);
        put(map, "notes", notes);
        put(map, "model_concurrency", modelConcurrency);
        put(map, "observed", observed);
        return map;
    }

    /**
     * 只在值非 null 时写入，空维度不落键。
     *
     * @param map   目标
     * @param key   键名
     * @param value 值
     */
    private static void put(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
