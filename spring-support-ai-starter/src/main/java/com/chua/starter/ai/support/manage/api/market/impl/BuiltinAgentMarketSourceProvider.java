package com.chua.starter.ai.support.manage.api.market.impl;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.starter.ai.support.manage.api.market.AgentMarketSourceProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 内置智能体市场源。
 *
 * <p>把 {@code {nomifun.agents-root}/builtin} 下的内置智能体包作为可分发的安装源枚举出来，
 * 使「安装智能体」在没有远程市场时也能走通完整流程（枚举 → 安装 → 落库）。
 * 远程市场可按同一 SPI 另加实现，无需改动控制器。</p>
 *
 * <p>本类由 SPI 直接实例化，不参与 Spring 注入，故根目录从系统属性
 * {@code nomifun.agents-root} 或环境变量 {@code NOMIFUN_AGENTS_ROOT} 读取
 * （与 {@code FileSystemSkillOfflineProvider} 的做法一致），缺省 {@code ./agents}。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Spi("builtin")
public class BuiltinAgentMarketSourceProvider implements AgentMarketSourceProvider {

    /**
     * 智能体包清单文件名
     */
    private static final String MANIFEST = "AGENT.md";

    @Override
    public String source() {
        return "builtin";
    }

    @Override
    public String displayName() {
        return "内置智能体";
    }

    @Override
    public String url() {
        return builtinDir().toString();
    }

    @Override
    public List<Map<String, Object>> search(String keyword, int limit) {
        List<Map<String, Object>> items = new ArrayList<>();
        Path dir = builtinDir();
        if (!Files.isDirectory(dir)) {
            return items;
        }
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path child : stream.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList()) {
                Path manifest = findManifest(child);
                if (manifest == null) {
                    continue;
                }
                Map<String, Object> meta = simpleFrontMatter(manifest);
                String name = text(meta.get("name"), child.getFileName().toString());
                String description = text(meta.get("description"), "");
                if (!kw.isEmpty()
                        && !name.toLowerCase(Locale.ROOT).contains(kw)
                        && !description.toLowerCase(Locale.ROOT).contains(kw)) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>(8);
                item.put("name", name);
                item.put("description", description);
                item.put("model", meta.get("model"));
                item.put("source", source());
                item.put("display_source", displayName());
                item.put("path", child.toString());
                item.put("url", child.toString());
                items.add(item);
                if (limit > 0 && items.size() >= limit) {
                    break;
                }
            }
        } catch (IOException ignore) {
            // 目录不可读时返回空列表，由上层提示
        }
        return items;
    }

    /**
     * 内置智能体目录。
     *
     * @return {root}/builtin
     */
    private Path builtinDir() {
        String root = System.getProperty("nomifun.agents-root");
        if (root == null || root.isBlank()) {
            root = System.getenv("NOMIFUN_AGENTS_ROOT");
        }
        if (root == null || root.isBlank()) {
            root = "./agents";
        }
        return Paths.get(root).toAbsolutePath().normalize().resolve("builtin");
    }

    /**
     * 目录内查找清单文件（大小写不敏感）。
     *
     * @param dir 目录
     * @return 清单路径，未找到返回 空
     */
    private Path findManifest(Path dir) {
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(p -> MANIFEST.equalsIgnoreCase(p.getFileName().toString()))
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 极简 frontmatter 解析（仅取顶层标量，够市场列表展示）。
     *
     * @param manifest 清单文件
     * @return 键值对
     */
    private Map<String, Object> simpleFrontMatter(Path manifest) {
        Map<String, Object> meta = new LinkedHashMap<>();
        try {
            String raw = Files.readString(manifest, StandardCharsets.UTF_8);
            if (!raw.startsWith("---")) {
                return meta;
            }
            int end = raw.indexOf("\n---", 3);
            if (end < 0) {
                return meta;
            }
            for (String line : raw.substring(3, end).split("\\R")) {
                String trimmed = line.trim();
                int colon = trimmed.indexOf(':');
                if (trimmed.isEmpty() || trimmed.startsWith("#") || colon <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, colon).trim().toLowerCase(Locale.ROOT);
                String value = trimmed.substring(colon + 1).trim();
                if (value.length() >= 2) {
                    char c = value.charAt(0);
                    if ((c == '"' || c == '\'') && value.charAt(value.length() - 1) == c) {
                        value = value.substring(1, value.length() - 1);
                    }
                }
                meta.put(key, value);
            }
        } catch (IOException ignore) {
            // 解析失败返回已收集内容
        }
        return meta;
    }

    /**
     * 取字符串（空安全 + 兜底）。
     *
     * @param value    原值
     * @param fallback 兜底值
     * @return 字符串
     */
    private String text(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String s = String.valueOf(value);
        return s.isBlank() ? fallback : s;
    }
}
