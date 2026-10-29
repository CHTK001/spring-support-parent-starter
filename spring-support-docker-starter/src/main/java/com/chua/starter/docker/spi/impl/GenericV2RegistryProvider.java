package com.chua.starter.docker.spi.impl;

import com.chua.starter.docker.spi.DockerRegistryProvider;
import com.chua.starter.docker.spi.RegistryConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 通用 Registry v2 镜像库实现（type = {@code generic-v2}）。
 *
 * <p>对应 Docker Registry HTTP API V2：{@code /v2/_catalog} 列目录、
 * {@code /v2/{name}/tags/list} 查标签，凭据走 Basic Auth。私有 Harbor / Nexus /
 * registry:2 都属于这一类。目录接口不支持关键词检索，故按 {@code _catalog}
 * 拉取后在本地做包含匹配。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
@Slf4j
@Component
public class GenericV2RegistryProvider implements DockerRegistryProvider {

    /**
     * 目录一次拉取上限的放大系数（本地过滤后再截断）
     */
    private static final int CATALOG_FACTOR = 10;

    /**
     * 目录硬上限（避免超大仓库一次拉爆内存）
     */
    private static final int CATALOG_MAX = 500;

    /**
     * JSON 解析器
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String type() {
        return "generic-v2";
    }

    @Override
    public String displayName() {
        return "通用 Registry v2";
    }

    @Override
    public List<Map<String, Object>> search(RegistryConfig cfg, String keyword, int limit) {
        String base = base(cfg);
        if (base.isEmpty()) {
            return new ArrayList<>();
        }
        int size = Math.min(CATALOG_MAX, Math.max(1, limit) * CATALOG_FACTOR);
        String word = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        try {
            RegistryHttp.Result result = RegistryHttp.get(base + "/_catalog?n=" + size, cfg, "application/json");
            if (!result.ok()) {
                log.warn("[docker] Registry 目录查询失败 status={} url={}", result.status(), base);
                return new ArrayList<>();
            }
            JsonNode root = objectMapper.readTree(result.body());
            List<Map<String, Object>> list = new ArrayList<>();
            for (JsonNode item : root.path("repositories")) {
                String name = item.asText("");
                if (name.isEmpty() || (!word.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(word))) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", name);
                row.put("description", "");
                row.put("stars", 0);
                row.put("official", false);
                row.put("pullRef", pullRef(cfg, name));
                list.add(row);
                if (list.size() >= limit) {
                    break;
                }
            }
            return list;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ArrayList<>();
        } catch (Exception e) {
            log.warn("[docker] Registry 目录查询异常 url={}: {}", base, e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public List<String> tags(RegistryConfig cfg, String image) {
        String base = base(cfg);
        String name = normalize(image);
        if (base.isEmpty() || name.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            RegistryHttp.Result result = RegistryHttp.get(base + "/" + name + "/tags/list", cfg, "application/json");
            if (!result.ok()) {
                log.warn("[docker] Registry 标签查询失败 status={} image={}", result.status(), image);
                return new ArrayList<>();
            }
            JsonNode root = objectMapper.readTree(result.body());
            List<String> tags = new ArrayList<>();
            for (JsonNode item : root.path("tags")) {
                String tag = item.asText("");
                if (!tag.isEmpty()) {
                    tags.add(tag);
                }
            }
            return tags;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ArrayList<>();
        } catch (Exception e) {
            log.warn("[docker] Registry 标签查询异常 image={}: {}", image, e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public String pullRef(RegistryConfig cfg, String image) {
        String name = normalize(image);
        if (name.isEmpty() || cfg == null) {
            return name;
        }
        String host = RegistryHttp.host(cfg.getUrl());
        if (host.isEmpty()) {
            return name;
        }
        return host + "/" + name;
    }

    @Override
    public boolean test(RegistryConfig cfg) {
        String base = base(cfg);
        if (base.isEmpty()) {
            return false;
        }
        try {
            RegistryHttp.Result result = RegistryHttp.get(base + "/", cfg, "application/json");
            // 401 说明服务在线、只是需要凭据：连通性本身没问题
            return result.ok() || result.status() == 401 || result.status() == 403;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("[docker] Registry 连通性测试失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 把库地址归一化为 {@code http(s)://host/v2}。
     *
     * @param cfg 镜像库配置
     * @return API 基址，配置缺失时返回空串
     */
    private String base(RegistryConfig cfg) {
        if (cfg == null || !RegistryHttp.hasText(cfg.getUrl())) {
            return "";
        }
        String url = cfg.getUrl().trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.endsWith("/v2")) {
            url = url + "/v2";
        }
        return url;
    }

    /**
     * 归一化镜像名（去空白与前后斜杠）
     *
     * @param image 原始镜像名
     * @return 归一化后的名称
     */
    private String normalize(String image) {
        if (image == null) {
            return "";
        }
        String value = image.trim();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
