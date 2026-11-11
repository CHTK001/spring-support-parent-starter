// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.usage.spi;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.common.support.ai.AiUsage;
import com.chua.common.support.datasearch.usage.spi.UsageParser;
import com.chua.common.support.lang.json.JsonArray;
import com.chua.common.support.lang.json.JsonObject;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderConnectionMapper;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.manage.store.SysAiProviderConnection;
import com.chua.starter.common.support.channel.MagpieSupport;
import com.chua.starter.common.support.http.ChannelHttp;
import com.chua.starter.common.support.http.ChannelHttpException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Magpie 数据面用量解析器 — 从 Magpie 网关的用量 API（{@code /v1/magpie/usage/requests}）
 * 按天拉取全部使用记录，经 Token 口径转换后喂给 {@code UsageAggregationTask.syncExternalUsage()}。
 *
 * <p>Token 口径（与 {@link UsageParser} 契约一致）：</p>
 * <ul>
 *   <li>{@code inputTokens} = in + cache_read：magpie 的 {@code in} 只计未命中的输入 token，
 *       命中缓存的请先增量 {@code cache_read}，计入 {@code cacheTokens}；</li>
 *   <li>{@code outputTokens} = out - reasoning：magpie 的 {@code out} 把内部推理 token 打包在内（
 *       {@code out >= reasoning}），需净出；</li>
 *   <li>{@code reasoningTokens} = reasoning（无推理模型的字段为 null）；</li>
 *   <li>{@code totalTokens} = inputTokens + outputTokens。</li>
 * </ul>
 *
 * <p>鉴权：清单从 {@code sys_ai_provider_connection} 读（{@code base_url} 为数据面地址，
 * {@code gateway_key} 为数据面网关 key），通过 {@code MagpieSupport} 发数据面请求。
 * 管理面（{@code :3430/api/*}，页面 Key 鉴权）不参与本解析器。</p>
 *
 * <p>配额是数据面 OpenAI 兼容接口，读 {@code GET /v1/magpie/usage/requests?period=today&account=&provider=&offset=...&limit=500}，
 * 响应带 {@code total}/{@code rows} 分页，{@code rows} 最大 500 条/页。</p>
 *
 * @since 4.0.0.53
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MagpieUsageParser implements UsageParser {

    /**
     * 数据面用量 REST 端点（需与 {@code baseUrl} 根地址拼接）
     */
    private static final String USAGE_PATH = "/v1/magpie/usage/requests";

    /**
     * 数据面每次翻页的请求条数
     */
    private static final int PAGE_SIZE = 500;

    /**
     * 渠道 provider 行 ID（渠道管理按 {@code MAGPIE} 建行）
     */
    private static final String MAGPIE_PROVIDER_ID = "MAGPIE";

    /**
     * 供应商映射器
     */
    private final SysAiProviderMapper providerMapper;

    /**
     * 供应商连接映射器
     */
    private final SysAiProviderConnectionMapper connectionMapper;

    @Override
    public String name() {
        return "magpie";
    }

    @Override
    public Flux<AiUsage> streamAll() {
        // 按消费方节奏在 boundedElastic 线程上拉数据，避免阻塞调度线程
        return Flux.defer(() -> Flux.fromIterable(lookupAllRows()).subscribeOn(Schedulers.boundedElastic()));
    }

    /**
     * 解析全部用量记录（含分页拉取与凭据解析），消费失败只记日志，不影响其他数据源。
     *
     * @return 解析出的用量记录
     */
    private List<AiUsage> lookupAllRows() {
        try {
            SysAiProviderConnection connection = resolveConnection();
            if (connection == null || connection.getCredentials() == null || connection.getCredentials().isEmpty()) {
                log.debug("[MagpieUsageParser] 未找到 magpie 连接凭据，跳过外部同步");
                return List.of();
            }
            Map<String, Object> credentials = connection.getCredentials();
            String baseUrl = MagpieSupport.baseUrl(credentials);
            Map<String, String> headers = MagpieSupport.dataHeaders(MagpieSupport.gatewayKey(credentials));
            log.info("[MagpieUsageParser] 从数据面 {} 拉取用量", baseUrl);
            return fetchRows(baseUrl, headers);
        } catch (Throwable e) {
            log.warn("[MagpieUsageParser] 拉取 magpie 用量失败: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 分页拉取数据面用量 API，直到取满 {@code total} 条（未给 total 时取到不足一页为止）
     *
     * @param baseUrl 数据面根地址
     * @param headers 数据面请求头（含 Authorization）
     * @return 解析出的用量记录
     */
    private List<AiUsage> fetchRows(String baseUrl, Map<String, String> headers) {
        List<AiUsage> rows = new ArrayList<>();
        int offset = 0;
        for (int page = 0; page < 500 && rows.size() > 0; page++) {
            // 单页拉取
            String url = baseUrl + USAGE_PATH + "?period=today&account=&provider=&offset=" + offset
                    + "&limit=" + PAGE_SIZE;
            String body;
            try {
                body = ChannelHttp.getJson(url, headers);
            } catch (ChannelHttpException e) {
                log.warn("[MagpieUsageParser] 用量页面拉取失败(offset={}): 状态码={}", offset, e.getHttpStatus());
                break;
            } catch (RuntimeException e) {
                log.warn("[MagpieUsageParser] 用量页面拉取失败(offset={}): {}", offset, e.getMessage());
                break;
            }
            JsonObject pageData;
            try {
                pageData = JsonObject.parse(body);
            } catch (RuntimeException e) {
                log.warn("[MagpieUsageParser] 用量响应解析失败: {}", e.getMessage());
                break;
            }
            if (pageData == null) {
                break;
            }
            Integer total = pageData.getObject("total", Integer.class);
            JsonArray pageRows = pageData.getJsonArray("rows");
            if (total == null || total <= 0 || pageRows == null || pageRows.isEmpty()) {
                break;
            }
            for (int i = 0; i < pageRows.size(); i++) {
                try {
                    rows.add(toAiUsage(pageRows.getJsonObject(i)));
                } catch (Throwable e) {
                    log.debug("[MagpieUsageParser] 单条用量转换失败: {}", e.getMessage());
                }
            }
            offset += pageRows.size();
            if (total == null) {
                if (pageRows.size() < PAGE_SIZE) {
                    break;
                }
            } else if (offset >= total) {
                break;
            }
        }
        if (!rows.isEmpty()) {
            log.info("[MagpieUsageParser] 已解析 {} 条 magpie 用量", rows.size());
        }
        return rows;
    }

    /**
     * 取该 provider 下的第一条 connection 凭据（系统级作用域：历史 NULL 系统池 + 角色/更新时间）
     *
     * @return 连接；未绑定返回 null
     */
    private SysAiProviderConnection resolveConnection() {
        try {
            SysAiProvider provider = providerMapper.selectById(MAGPIE_PROVIDER_ID);
            if (provider == null) {
                provider = providerMapper.selectOne(new LambdaQueryWrapper<SysAiProvider>()
                        .eq(SysAiProvider::getPlatform, MAGPIE_PROVIDER_ID)
                        .last("LIMIT 1"));
            }
            if (provider == null || !Boolean.TRUE.equals(provider.getEnabled())) {
                return null;
            }
            return connectionMapper.selectOne(new LambdaQueryWrapper<SysAiProviderConnection>()
                    .eq(SysAiProviderConnection::getProviderId, provider.getProviderId())
                    .orderByDesc(SysAiProviderConnection::getRole)
                    .orderByDesc(SysAiProviderConnection::getUpdatedAt)
                    .last("LIMIT 1"));
        } catch (Throwable e) {
            log.warn("[MagpieUsageParser] 解析 magpie 连接失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 把单条用量 API 行转为通用 AiUsage
     *
     * @param row row
     * @return 转为AIusage的结果
     */
    private AiUsage toAiUsage(JsonObject row) {
        Integer in = row.getType("in", 0, Integer.class);
        Integer out = row.getType("out", 0, Integer.class);
        Integer cacheRead = row.getType("cache_read", 0, Integer.class);
        Integer reasoning = row.getObject("reasoning", Integer.class);
        if (in == null) {
            in = 0;
        }
        if (out == null) {
            out = 0;
        }
        if (cacheRead == null) {
            cacheRead = 0;
        }
        if (reasoning == null) {
            reasoning = 0;
        }

        // 推理 token 单独列示，不得再算进 outputTokens 里
        int reasoningTokens = reasoning > 0 ? reasoning : 0;
        // magpie 的 out 打包了内部推理 token（out >= reasoning）
        int outputTokens = Math.max(out - reasoning, 0);
        // 输入 token 含缓存命中部分（cache_read）
        int inputTokens = in + cacheRead;

        Double cost = row.getObject("cost", Double.class);
        if (cost == null) {
            cost = 0.0;
        }

        return AiUsage.builder()
                .provider(row.getObject("provider", String.class))
                .model(row.getObject("model", String.class))
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .cacheTokens(cacheRead)
                .reasoningTokens(reasoningTokens > 0 ? reasoningTokens : null)
                .totalTokens(inputTokens + outputTokens)
                .startTime(startTimeOf(row))
                .firstTokenLatencyMillis(row.getObject("ttft_ms", Long.class))
                .durationMillis(row.getObject("ms", Long.class))
                .requestId(requestIdOf(row))
                .currency("USD")
                .totalCost(BigDecimal.valueOf(cost))
                .build();
    }

    /**
     * 请求 ID：有 rid 取 rid，否则取 response_id
     *
     * @param row row
     * @return 请求 ID
     */
    private String requestIdOf(JsonObject row) {
        String rid = row.getObject("rid", String.class);
        if (rid != null && !rid.isBlank()) {
            return rid;
        }
        return row.getObject("response_id", String.class);
    }

    /**
     * 调用时间：magpie 以 ISO-8601 字符串（带时区偏移）存放，转 epoch millis
     *
     * @param row row
     * @return 调用时间（epoch millis）
     */
    private Long startTimeOf(JsonObject row) {
        Object value = row.get("t");
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            if (text.isBlank()) {
                return null;
            }
            try {
                return OffsetDateTime.parse(text).toInstant().toEpochMilli();
            } catch (Throwable e) {
                log.debug("[MagpieUsageParser] 时间解析失败: {}", e.getMessage());
                return null;
            }
        }
        return null;
    }
}
