package com.chua.starter.ai.support.spi;

import java.util.List;
import java.util.Map;

/**
* 插件离线 Provider SPI。
*
* <p>每个本地/内置插件来源一个实现，通过 {@code @Spi("source-key")} 注册。
* 当前插件市场暂无数据源，默认实现返回空列表。开关状态持久化在
* {@code sys_ai_global_setting} 的 {@code plugins} 分组。</p>
*
* @since 4.0.0.43
* @author CH
 */
public interface PluginOfflineProvider {

    /**
    * 离线源名称。
    *
    * @return 源名称
     */
    String name();

    /**
    * 列出该来源提供的插件列表。
    *
    * @return 插件列表
     */
    List<Map<String, Object>> listPlugins();
}
