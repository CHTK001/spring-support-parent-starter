package com.chua.starter.ai.support.configuration.search;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
 * 联网搜索模块全局设置提供者 SPI。
 *
 * <p>定义 {@code search} 分组的全部配置项，由
 * {@link com.chua.starter.ai.support.configuration.client.GlobalSettingInitializer}
 * 启动时注册到 {@code sys_ai_global_setting} 表。</p>
 *
 * <p><b>为什么补这个类。</b>设置中心的「搜索设置」页早就在读写 {@code search} 分组
 * （{@code fetchGroupSettings('search')} / {@code updateGroupSettings('search', ...)}），
 * 但后端从来没有声明过这个分组的设置项——于是库里 {@code search} 分组一行都没有，
 * 页面拿到空数据、填的 key 无处落库，而 {@link com.chua.starter.ai.support.search.WebSearchService}
 * 只能吃 {@code orElse("360")} 的硬编码兜底：用户在页面上既看不到 360、也配不了付费源。
 * 两端字段格式本就是配套的（前端存 {@code {apiKey:"..."}}，后端用 API_KEY 正则抠 key），
 * 缺的就是这一层声明。</p>
 *
 * <p><b>字段格式约定</b>：三个付费源存 JSON 对象 {@code {"apiKey":"xxx"}}，
 * 与前端 {@code providerKeys} 的结构一致；后端 {@code extractApiKey} 同时兼容
 * {@code {apiKey=xxx}} 与 JSON 两种写法，历史值不需要迁移。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Spi("search")
public class SearchGlobalSettingProvider implements GlobalSettingProvider {

    /**
     * 配置分组标识
     */
    private static final String GROUP = "search";

    /**
     * 分组显示名称
     */
    private static final String GROUP_NAME = "联网搜索";

    /**
     * 配置分组标识。
     *
     * @return search
     */
    @Override
    public String group() {
        return GROUP;
    }

    /**
     * 配置分组显示名称。
     *
     * @return 联网搜索
     */
    @Override
    public String groupName() {
        return GROUP_NAME;
    }

    /**
     * 联网搜索模块全部配置项。
     *
     * <p>默认值取 {@code 360} 而不是付费源：360 无需 key 即可用，是唯一
     * 「装上就能联网」的路径；付费源留空时后端会自动回落到它。这与
     * {@code WebSearchService} 里 {@code orElse("360")} 的兜底口径保持一致，
     * 避免页面显示的选中项与实际生效的搜索源不是同一个。</p>
     *
     * @return 配置项列表
     */
    @Override
    public List<SettingItem> getItems() {
        return List.of(
                new SettingItem("provider", "360", "STRING",
                        "搜索源：360（免费，无需 API Key，默认）/ bocha 博查 / tavily / metaso 秘塔 /"
                                + " langsearch / gubo / tinyfish。付费源需在下方填写对应 API Key，"
                                + "留空或调用失败时后端自动回落到 360", 1),
                new SettingItem("bocha", "", "STRING",
                        "博查 API Key（格式 {\"apiKey\":\"sk-xxx\"}；留空则该源不可用，不影响其他源）", 2),
                new SettingItem("tavily", "", "STRING",
                        "Tavily API Key（格式 {\"apiKey\":\"tvly-xxx\"}；留空则该源不可用，不影响其他源）", 3),
                new SettingItem("metaso", "", "STRING",
                        "秘塔 API Key（格式 {\"apiKey\":\"xxx\"}；该源的搜索接口尚未接入，"
                                + "填写后暂无效果，预留字段）", 4),
                new SettingItem("langsearch", "", "STRING",
                        "LangSearch API Key（格式 {\"apiKey\":\"sk-xxx\"}；"
                                + "按 langsearch.com 官方 web-search 契约调用）", 5),
                new SettingItem("gubo", "", "STRING",
                        "Gubo Search API Key（格式 {\"apiKey\":\"xxx\"}）", 6),
                new SettingItem("tinyfish", "", "STRING",
                        "TinyFish API Key（格式 {\"apiKey\":\"xxx\"}）", 7),
                // Gubo / TinyFish 的接口地址可覆盖。
                // 这两家的公开文档我没能核对到（本机联网检索不可用），代码里按「通用
                // web-search 契约」实现：POST JSON、Authorization: Bearer <key>、
                // 响应取 data.webPages.value[].{name,url,snippet}。若厂商契约不同，
                // 在这里改地址即可适配，不必改代码重新发版。
                new SettingItem("gubo_endpoint", "", "STRING",
                        "Gubo 接口地址（留空用内置默认 https://api.gubosearch.com/v1/web-search；"
                                + "契约不同时在此覆盖）", 8),
                new SettingItem("tinyfish_endpoint", "", "STRING",
                        "TinyFish 接口地址（留空用内置默认 https://api.tinyfish.ai/v1/web-search；"
                                + "契约不同时在此覆盖）", 9),
                new SettingItem("langsearch_endpoint", "", "STRING",
                        "LangSearch 接口地址（留空用内置默认 https://api.langsearch.com/v1/web-search）", 10)
        );
    }
}
