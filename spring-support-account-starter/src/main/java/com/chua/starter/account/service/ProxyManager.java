package com.chua.starter.account.service;

import com.chua.common.support.datasearch.network.proxy.ProxyFetcherFlow;
import com.chua.common.support.network.client.HttpClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 代理管理服务。
 *
 * <p>管理代理配置、连通性测试、免费代理获取。代理支持格式：</p>
 * <ul>
 *   <li>{@code http://127.0.0.1:7897}</li>
 *   <li>{@code socks5://host:port}</li>
 *   <li>{@code host:port}</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Service
public class ProxyManager {

    /** 当前代理 host */
    private volatile String proxyHost;

    /** 当前代理 port */
    private volatile int proxyPort;

    /** 代理是否启用 */
    private volatile boolean enabled;

    /** 原始代理字符串 */
    private volatile String rawProxy;

    /** 测试结果缓存：key=proxy|target */
    private final Map<String, Integer> testCache = new ConcurrentHashMap<>();

    /**
     * 设置代理。
     *
     * @param proxy 原始代理字符串
     */
    public void setProxy(String proxy) {
        this.rawProxy = proxy;
        if (proxy == null || proxy.isBlank()) {
            this.proxyHost = null;
            this.proxyPort = 0;
            this.enabled = false;
            log.info("[ProxyManager] 代理已清除");
            return;
        }
        String cleaned = proxy.replace("http://", "")
                .replace("https://", "")
                .replace("socks5://", "")
                .replace("socks://", "");
        String[] parts = cleaned.split(":");
        this.proxyHost = parts[0].trim();
        this.proxyPort = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1080;
        this.enabled = true;
        log.info("[ProxyManager] 代理已设置: {} (host={}, port={}, enabled={})",
                rawProxy, proxyHost, proxyPort, enabled);
    }

    public String getProxyHost() {
        return enabled ? proxyHost : null;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getRawProxy() {
        return rawProxy;
    }

    /**
     * 从 datasearch-starter 的 ProxyFetcherFlow 获取免费代理列表。
     *
     * @return 代理列表 "host:port"
     */
    public List<String> fetchFreeProxies() {
        try {
            List<String> proxies = ProxyFetcherFlow.of().fetchAll();
            log.info("[ProxyManager] 获取到 {} 个免费代理", proxies.size());
            return proxies;
        } catch (Exception e) {
            log.warn("[ProxyManager] 获取免费代理失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 测试当前代理到目标站点的连通性。
     *
     * @param targetUrl 目标 URL，默认 accounts.x.ai
     * @return HTTP 状态码（0 表示不可达）
     */
    public int testProxy(String targetUrl) {
        if (!enabled) {
            return 0;
        }
        String url = (targetUrl == null || targetUrl.isBlank()) ? "https://accounts.x.ai/sign-up" : targetUrl;
        String cacheKey = rawProxy + "|" + url;
        if (testCache.containsKey(cacheKey)) {
            return testCache.get(cacheKey);
        }
        try {
            HttpClientBuilder builder = new HttpClientBuilder(url)
                    .proxy(proxyHost, proxyPort)
                    .accept("*/*")
                    .connectTimeout(10000)
                    .readTimeout(10000);
            var resp = builder.get();
            int status = resp.getStatusCode();
            testCache.put(cacheKey, status);
            log.info("[ProxyManager] 代理测试 {} -> HTTP {}", url, status);
            return status;
        } catch (Exception e) {
            log.warn("[ProxyManager] 代理测试失败 {}: {}", url, e.getMessage());
            testCache.put(cacheKey, 0);
            return 0;
        }
    }

    /**
     * 清除测试缓存。
     */
    public void clearTestCache() {
        testCache.clear();
    }
}
