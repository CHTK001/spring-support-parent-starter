package com.chua.starter.ai.support.service;

import java.util.List;
import java.util.Map;

/**
 * AI 插件清单服务。
 *
 * <p>把插件后台的三层数据串成一条真实链路，供设置页与插件管理接口共同消费：
 * <ul>
 *   <li>Provider 开关：读写 {@code sys_ai_global_setting} 的 {@code plugins} 分组，
 *       字段 {@code provider_<local|online>_<key>_enabled}，由本服务统一保管键格式；</li>
 *   <li>本地插件：仅聚合开关为真的 {@code PluginOfflineProvider} 实现（底层扫描本机编辑器/Agent 插件目录）；</li>
 *   <li>在线插件：仅检索开关为真的 {@code PluginMarketSourceProvider} 市场源，
 *       按源标识委派到 datasearch 的在线插件来源执行真实搜索。</li>
 * </ul>
 * 每个插件条目携带 {@code enabled} 字段，取自同一分组的 {@code plugins.<pluginId>} 单插件开关。</p>
 *
 * @since 4.0.0.43
 * @author CH
 */
public interface AiPluginCatalogService {

    /**
     * 读取 Provider 开关。
     *
     * @param providerKey    Provider 键（形如 {@code local_XxxProvider} 或 {@code online_open-vsx}）
     * @param defaultValue   未配置时的兜底值
     * @return 该 Provider 是否启用
     */
    boolean isProviderEnabled(String providerKey, boolean defaultValue);

    /**
     * 写入 Provider 开关。
     *
     * @param providerKey Provider 键，不允许为 null
     * @param enabled     是否启用
     */
    void setProviderEnabled(String providerKey, boolean enabled);

    /**
     * 列出启用来源下的本地插件。
     *
     * @return 本地插件条目列表，无数据时返回空列表
     */
    List<Map<String, Object>> listLocalPlugins();

    /**
     * 统计某个本地 Provider 扫描到的插件数量。
     *
     * @param providerKey 本地 Provider 键（{@code local_} 前缀）
     * @return 插件数量；Provider 不存在或已关闭时返回 0
     */
    int countLocalPlugins(String providerKey);

    /**
     * 在启用的在线市场源中检索插件。
     *
     * @param keyword 搜索关键词，为空时返回各市场源的默认展示列表
     * @return 在线插件条目列表，无数据时返回空列表
     */
    List<Map<String, Object>> searchMarketPlugins(String keyword);

    /**
     * 按插件标识查找本地插件。
     *
     * @param pluginId 插件标识
     * @return 插件条目（含 enabled 字段），未找到返回 null
     */
    Map<String, Object> findLocalPlugin(String pluginId);

    /**
     * 丢弃本地插件缓存，下次读取时重新扫描磁盘。
     */
    void refresh();
}
