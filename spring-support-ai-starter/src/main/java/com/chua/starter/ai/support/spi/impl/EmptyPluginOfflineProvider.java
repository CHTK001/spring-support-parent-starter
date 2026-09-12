package com.chua.starter.ai.support.spi.impl;

import com.chua.starter.ai.support.spi.PluginOfflineProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
* 默认本地插件离线 Provider：当前插件市场暂无数据源，返回空列表。
*
* @since 4.0.0.43
* @author CH
 */
@Component
public class EmptyPluginOfflineProvider implements PluginOfflineProvider {

    @Override
    public String name() {
        return "本地插件（暂无数据源）";
    }

    @Override
    public List<Map<String, Object>> listPlugins() {
        return List.of();
    }
}
