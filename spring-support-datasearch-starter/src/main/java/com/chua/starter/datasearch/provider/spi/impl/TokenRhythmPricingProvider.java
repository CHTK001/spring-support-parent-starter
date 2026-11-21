package com.chua.starter.datasearch.provider.spi.impl;

import com.chua.starter.datasearch.provider.model.ProviderPricingRefresh;
import com.chua.starter.datasearch.provider.model.ProviderPricingRule;
import com.chua.starter.datasearch.provider.spi.ProviderPricingProvider;
import com.chua.starter.datasearch.provider.support.TokenRhythmPricingSource;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * 基元律动（TokenRhythm）定价规则实现：实时抓控制台公开模型目录。
 *
 * <p>TokenRhythm 未被 models.dev 快照收录，也没有静态登记表；价格来自控制台
 * 公开接口 {@code GET https://tokenrhythm.studio/api/models}（币种 CNY、按百万
 * token 计价）。抓取只发生在点击「同步」时的 {@link #refresh(String, String)}：
 * 成功后写内存缓存并返回规则；失败返回 {@link ProviderPricingRefresh#fail}、
 * <b>不写缓存</b>，保留库里上一次的价格。{@link #query(String, String)} 只读缓存
 * 绝不出网——目录读取路径（{@code ProviderPricings.of}）会调它，不能拖慢。</p>
 *
 * @author CH
 * @since 4.0.0.63
 */
@Slf4j
public class TokenRhythmPricingProvider implements ProviderPricingProvider {

    /**
     * 最近一次成功抓取的规则缓存；null 表示尚未抓取过
     */
    private static volatile List<ProviderPricingRule> cache;

    @Override
    public String name() {
        return TokenRhythmPricingSource.PLATFORM;
    }

    @Override
    public boolean supports(String platform, String baseUrl) {
        return matches(platform, baseUrl);
    }

    @Override
    public List<ProviderPricingRule> query(String platform, String baseUrl) {
        if (!matches(platform, baseUrl)) {
            return List.of();
        }
        List<ProviderPricingRule> current = cache;
        return current == null ? List.of() : current;
    }

    @Override
    public ProviderPricingRefresh refresh(String platform, String baseUrl) {
        if (!matches(platform, baseUrl)) {
            return ProviderPricingRefresh.unsupported();
        }
        synchronized (this) {
            try {
                JsonNode models = TokenRhythmPricingSource.fetchModels();
                String fetchedAt = TokenRhythmPricingSource.now();
                List<ProviderPricingRule> rules = TokenRhythmPricingSource.toRules(models, fetchedAt);
                cache = rules;
                return ProviderPricingRefresh.ok(rules, fetchedAt);
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.warn("[provider] tokenrhythm 定价抓取失败: {}", e.toString());
                return ProviderPricingRefresh.fail("tokenrhythm 定价抓取失败: " + e.getMessage());
            }
        }
    }

    @Override
    public int size() {
        List<ProviderPricingRule> current = cache;
        return current == null ? 0 : current.size();
    }

    /**
     * 平台标识或上游地址命中 TokenRhythm。
     *
     * @param platform 平台类型（provider id）
     * @param baseUrl  上游基础地址
     * @return 命中返回 true
     */
    static boolean matches(String platform, String baseUrl) {
        if (platform != null && TokenRhythmPricingSource.PLATFORM.equalsIgnoreCase(platform.trim())) {
            return true;
        }
        String host = hostOf(baseUrl);
        return host.equals(TokenRhythmPricingSource.HOST)
                || host.endsWith("." + TokenRhythmPricingSource.HOST);
    }

    /**
     * 提取 URL 主机名。
     *
     * @param url 地址
     * @return 主机名（小写）；失败返回空串
     */
    private static String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            String host = URI.create(url.trim()).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }
}
