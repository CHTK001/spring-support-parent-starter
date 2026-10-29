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
import java.util.Map;

/**
 * Docker Hub 镜像库实现（type = {@code dockerhub}）。
 *
 * <p>检索与标签走 Docker Hub 的开放 API：{@code /v2/search/repositories/} 与
 * {@code /v2/repositories/{ns}/{repo}/tags}，均带超时。{@code url} 字段在这里表示
 * 「镜像加速地址」——检索固定用 hub.docker.com，但 {@link #pullRef} 会把加速地址
 * 作为 registry host 前缀，因此换成国内镜像源后拉取走加速地址、检索仍可用。</p>
 *
 * @author CH
 * @since 4.0.0.45
 */
@Slf4j
@Component
public class DockerHubRegistryProvider implements DockerRegistryProvider {

    /**
     * Docker Hub API 基址（固定，不受镜像加速地址影响）
     */
    private static final String HUB_API = "https://hub.docker.com";

    /**
     * 官方镜像默认命名空间
     */
    private static final String LIBRARY = "library";

    /**
     * 标签查询上限
     */
    private static final int TAG_LIMIT = 100;

    /**
     * JSON 解析器
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String type() {
        return "dockerhub";
    }

    @Override
    public String displayName() {
        return "Docker Hub";
    }

    @Override
    public List<Map<String, Object>> search(RegistryConfig cfg, String keyword, int limit) {
        String word = keyword == null ? "" : keyword.trim();
        if (word.length() < 2) {
            return new ArrayList<>();
        }
        String url = HUB_API + "/v2/search/repositories/?query=" + RegistryHttp.encode(word)
                + "&page_size=" + Math.max(1, limit);
        try {
            RegistryHttp.Result result = RegistryHttp.get(url, cfg, "application/json");
            if (!result.ok()) {
                log.warn("[docker] Docker Hub 检索失败 status={}", result.status());
                return new ArrayList<>();
            }
            JsonNode root = objectMapper.readTree(result.body());
            List<Map<String, Object>> list = new ArrayList<>();
            for (JsonNode item : root.path("results")) {
                String repo = item.path("repo_name").asText("");
                if (repo.isEmpty()) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", repo);
                row.put("description", item.path("short_description").asText(""));
                row.put("stars", item.path("star_count").asInt(0));
                row.put("official", item.path("is_official").asBoolean(false));
                row.put("pullRef", pullRef(cfg, repo));
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
            log.warn("[docker] Docker Hub 检索异常: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public List<String> tags(RegistryConfig cfg, String image) {
        String name = normalize(image);
        if (name.isEmpty()) {
            return new ArrayList<>();
        }
        String namespace = cfg == null || !RegistryHttp.hasText(cfg.getNamespace()) ? null : cfg.getNamespace().trim();
        int slash = name.indexOf('/');
        if (slash > 0) {
            namespace = name.substring(0, slash);
            name = name.substring(slash + 1);
        }
        String repo = (namespace == null || namespace.isEmpty() ? LIBRARY : namespace) + "/" + name;
        String url = HUB_API + "/v2/repositories/" + repo + "/tags?page_size=" + TAG_LIMIT;
        try {
            RegistryHttp.Result result = RegistryHttp.get(url, cfg, "application/json");
            if (!result.ok()) {
                log.warn("[docker] Docker Hub 标签查询失败 status={} image={}", result.status(), image);
                return new ArrayList<>();
            }
            JsonNode root = objectMapper.readTree(result.body());
            List<String> tags = new ArrayList<>();
            for (JsonNode item : root.path("results")) {
                String tag = item.path("name").asText("");
                if (!tag.isEmpty()) {
                    tags.add(tag);
                }
            }
            return tags;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ArrayList<>();
        } catch (Exception e) {
            log.warn("[docker] Docker Hub 标签查询异常 image={}: {}", image, e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public String pullRef(RegistryConfig cfg, String image) {
        String name = normalize(image);
        if (name.isEmpty()) {
            return "";
        }
        if (cfg != null && RegistryHttp.hasText(cfg.getNamespace()) && !name.contains("/")) {
            name = cfg.getNamespace().trim() + "/" + name;
        }
        String host = cfg == null ? "" : RegistryHttp.host(cfg.getUrl());
        return host.isEmpty() ? name : host + "/" + name;
    }

    @Override
    public boolean test(RegistryConfig cfg) {
        try {
            RegistryHttp.Result result = RegistryHttp.get(HUB_API + "/v2/repositories/" + LIBRARY + "/nginx/",
                    cfg, "application/json");
            return result.ok();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            log.warn("[docker] Docker Hub 连通性测试失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 归一化镜像名：去空白、去前后斜杠，并去掉可能自带的 Docker Hub host 前缀。
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
        for (String prefix : List.of("docker.io/", "index.docker.io/", "registry-1.docker.io/", "hub.docker.com/")) {
            if (value.startsWith(prefix)) {
                value = value.substring(prefix.length());
            }
        }
        return value;
    }
}
