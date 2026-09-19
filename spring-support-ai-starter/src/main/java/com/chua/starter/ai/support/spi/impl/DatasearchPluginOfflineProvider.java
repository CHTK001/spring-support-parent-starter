package com.chua.starter.ai.support.spi.impl;

import com.chua.common.support.datasearch.plugin.model.PluginDefinition;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.spi.PluginOfflineProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地插件离线 Provider：适配 {@code utils-support-datasearch-starter} 的插件扫描 SPI。
 *
 * <p>遍历该库注册的每种本机插件来源（Claude、Trae/Trae-CN、VS Code、Cursor、Windsurf、
 * Cline、Roo Code、Kilo Code、Copilot、Zed、OpenClaw 等），仅收录插件目录确实存在
 * （{@code isInstalled()} 为真）的来源，并把 {@link PluginDefinition} 摊平成前端可用的 Map。
 * 单个来源扫描失败只记录告警，不影响其余来源。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
@Slf4j
@Component
public class DatasearchPluginOfflineProvider implements PluginOfflineProvider {

    @Override
    public String name() {
        return "本地已安装插件（编辑器/Agent 目录扫描）";
    }

    @Override
    public List<Map<String, Object>> listPlugins() {
        List<Map<String, Object>> plugins = new ArrayList<>();
        var sources = ServiceProvider.of(com.chua.common.support.datasearch.plugin.spi.PluginOfflineProvider.class).list();
        if (sources.isEmpty()) {
            log.info("[PluginSPI] 未发现任何本地插件来源（datasearch 插件 SPI 未注册实现）");
            return plugins;
        }
        for (var source : sources.values()) {
            try {
                if (!source.isInstalled()) {
                    continue;
                }
                List<PluginDefinition> definitions = source.listPlugins();
                if (definitions == null) {
                    continue;
                }
                for (PluginDefinition definition : definitions) {
                    if (definition != null) {
                        plugins.add(toMap(definition));
                    }
                }
            } catch (Exception e) {
                log.warn("[PluginSPI] 来源 {} 扫描失败: {}", source.name(), e.getMessage());
            }
        }
        return plugins;
    }

    /**
     * 将插件定义转换为前端展示用的键值对。
     *
     * @param definition 插件定义，不允许为 null
     * @return 有序键值对，插件标识缺失时以「来源/名称」兜底
     */
    private Map<String, Object> toMap(PluginDefinition definition) {
        String id = definition.getId();
        if (id == null || id.isBlank()) {
            id = definition.getSource() + "/" + definition.getName();
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("pluginId", id);
        item.put("name", definition.getName());
        item.put("description", definition.getDescription());
        item.put("author", definition.getAuthor());
        item.put("version", definition.getVersion());
        item.put("location", definition.getLocation());
        item.put("source", definition.getSource());
        item.put("manifestPath", definition.getManifestPath());
        item.put("origin", "local");
        return item;
    }
}
