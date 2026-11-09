// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.configuration.runtime;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent 引擎解析口径测试。
 *
 * <p>钉住三件事：</p>
 * <ol>
 *   <li>默认引擎是 {@code opencode-server}，且 {@code runtime} 分组里确实登记了自动下载开关；</li>
 *   <li>历史值 {@code opencode} 归一为 {@code opencode-server}，未注册值/空值回落默认；</li>
 *   <li>显式选择 {@code agentscope} 时不做降级（降级只发生在选择 opencode 而它不可用时）。</li>
 * </ol>
 *
 * <p>不在此处断言「opencode 不可用时降级」：可用性取决于运行机器上是否装了 CLI、
 * 常驻服务能否健康检查通过，属于环境事实，写成断言会让测试在装了 opencode 的机器上假红。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@DisplayName("Agent 引擎解析口径")
class AgentProviderFactoryTest {

    @Test
    @DisplayName("默认引擎为 opencode-server，并登记自动下载开关")
    void defaultProviderAndSettings() {
        assertEquals("opencode-server", RuntimeGlobalSettingProvider.DEFAULT_AGENT_IMPL);
        assertEquals("opencode-server", AgentProviderFactory.DEFAULT_PROVIDER);

        List<GlobalSettingProvider.SettingItem> items = new RuntimeGlobalSettingProvider().getItems();
        GlobalSettingProvider.SettingItem agentImpl = find(items, RuntimeGlobalSettingProvider.FIELD_AGENT_IMPL);
        GlobalSettingProvider.SettingItem autoInstall =
                find(items, RuntimeGlobalSettingProvider.FIELD_OPENCODE_AUTO_INSTALL);
        assertNotNull(agentImpl, "runtime 分组必须登记 agent_impl");
        assertEquals("opencode-server", agentImpl.defaultValue());
        assertTrue(agentImpl.adminOnly(), "引擎实现是管理员专属配置");
        assertNotNull(autoInstall, "runtime 分组必须登记 opencode_auto_install");
        assertEquals("true", autoInstall.defaultValue(), "默认允许自动下载并启动");
        assertTrue(autoInstall.adminOnly(), "自动下载开关是管理员专属配置");
    }

    @Test
    @DisplayName("历史值 opencode 归一为 opencode-server，空值/未注册值回落默认")
    void normalizeProviderAliases() {
        assertEquals("opencode-server", AgentProviderFactory.effectiveRuntime(null));
        assertEquals("opencode-server", AgentProviderFactory.effectiveRuntime("   "));
        assertEquals("opencode-server", AgentProviderFactory.effectiveRuntime("opencode"));
        assertEquals("opencode-server", AgentProviderFactory.effectiveRuntime("OpenCode"));
        assertEquals("opencode-server", AgentProviderFactory.effectiveRuntime("opencode-server"));
        assertEquals("opencode-server", AgentProviderFactory.effectiveRuntime("langchain4j"),
                "未装配的 SPI 不能透传，否则实例化为 null");
        assertEquals("agentscope", AgentProviderFactory.effectiveRuntime("agentscope"));
        assertEquals("agentscope", AgentProviderFactory.effectiveRuntime("AgentScope"));
    }

    @Test
    @DisplayName("显式选择 agentscope 时不降级")
    void explicitAgentscopeIsNotDegraded() {
        FakeSettingService settings = new FakeSettingService();
        settings.put(RuntimeGlobalSettingProvider.GROUP, RuntimeGlobalSettingProvider.FIELD_AGENT_IMPL, "agentscope");
        AgentProviderFactory factory = new AgentProviderFactory(settings);

        AgentProviderFactory.RuntimeResolution resolution = factory.resolution();
        assertEquals("agentscope", resolution.selected());
        assertEquals("agentscope", resolution.requested());
        assertEquals("agentscope", resolution.effective());
        assertFalse(resolution.degraded());
        assertEquals("agentscope", factory.resolveProvider());
    }

    @Test
    @DisplayName("自动下载开关按配置读取，缺省为 true")
    void autoInstallFlag() {
        FakeSettingService settings = new FakeSettingService();
        AgentProviderFactory factory = new AgentProviderFactory(settings);
        assertTrue(factory.autoInstallEnabled(), "未配置时默认允许自动下载");

        settings.put(RuntimeGlobalSettingProvider.GROUP,
                RuntimeGlobalSettingProvider.FIELD_OPENCODE_AUTO_INSTALL, "false");
        assertFalse(factory.autoInstallEnabled());
    }

    /**
     * 按字段名找设置项。
     *
     * @param items 设置项列表
     * @param field 字段名
     * @return 匹配项；不存在返回 {@code null}
     */
    private static GlobalSettingProvider.SettingItem find(List<GlobalSettingProvider.SettingItem> items,
                                                          String field) {
        return items.stream().filter(item -> field.equals(item.field())).findFirst().orElse(null);
    }

    /**
     * 内存版全局设置服务：只实现本测试用到的读取语义。
     */
    private static final class FakeSettingService implements SysAiGlobalSettingService {

        /**
         * group -> field -> value
         */
        private final Map<String, Map<String, String>> values = new HashMap<>();

        /**
         * 写入一个设置值。
         *
         * @param group 分组
         * @param field 字段
         * @param value 值
         */
        void put(String group, String field, String value) {
            values.computeIfAbsent(group, key -> new HashMap<>()).put(field, value);
        }

        @Override
        public Optional<String> getValue(String group, String field) {
            return Optional.ofNullable(values.getOrDefault(group, Map.of()).get(field));
        }

        @Override
        public Optional<String> getValueDirect(String group, String field) {
            return getValue(group, field);
        }

        @Override
        public boolean getBoolean(String group, String field, boolean defaultValue) {
            return getValue(group, field).map(Boolean::parseBoolean).orElse(defaultValue);
        }

        @Override
        public List<Map<String, Object>> list(String group) {
            return List.of();
        }

        @Override
        public void replace(Object request) {
            // 测试桩不落库
        }

        @Override
        public void upsertValue(String group, String field, String value, String description) {
            put(group, field, value);
        }

        @Override
        public void upsertField(String group, String field, String value, String valueType,
                                String description, int sort) {
            put(group, field, value);
        }

        @Override
        public int syncDescriptions(String group, Map<String, String> fieldToDescription) {
            return 0;
        }
    }
}
