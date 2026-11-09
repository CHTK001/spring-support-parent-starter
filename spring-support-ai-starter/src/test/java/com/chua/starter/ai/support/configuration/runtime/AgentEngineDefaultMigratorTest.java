// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.configuration.runtime;

import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent 引擎默认值迁移口径测试。
 *
 * <p>迁移存在的价值只有一个：存量部署里 {@code agent_impl} 被旧版本种子成 {@code agentscope}，
 * 而初始化器按设计不覆盖已有值，于是「默认 opencode」永远不生效。因此这里必须钉住两件事——</p>
 * <ol>
 *   <li>系统默认层的 {@code agentscope} 会被改写为 {@code opencode-server}；</li>
 *   <li>管理员自己配过的值（{@code systemDefault=false}）一律不动，即使取值恰好也是
 *       {@code agentscope}——那是真实选择，不是遗留默认值。</li>
 * </ol>
 *
 * @author CH
 * @since 4.0.0.52
 */
@DisplayName("Agent 引擎默认值迁移")
class AgentEngineDefaultMigratorTest {

    @Test
    @DisplayName("系统默认层的 agentscope 迁移为 opencode-server")
    void migratesLegacySystemDefault() {
        FakeSettingService settings = new FakeSettingService();
        settings.seed(RuntimeGlobalSettingProvider.FIELD_AGENT_IMPL, "agentscope", true);

        assertTrue(new AgentEngineDefaultMigrator(settings).migrate());

        assertEquals(1, settings.writes.size(), "必须真正写回一次");
        assertEquals("opencode-server", settings.getValue(RuntimeGlobalSettingProvider.GROUP,
                RuntimeGlobalSettingProvider.FIELD_AGENT_IMPL).orElse(null),
                "迁移后取值应为新默认值");
        assertEquals(RuntimeGlobalSettingProvider.DEFAULT_AGENT_IMPL_DESCRIPTION, settings.lastDescription,
                "描述与 provider 注册的文案共用同一常量，避免两处漂移");
        assertFalse(settings.lastRow.systemDefault, "写入后该行不再属于系统默认层");
    }

    @Test
    @DisplayName("管理员配置过的 agentscope 不被迁移")
    void keepsConfiguredValue() {
        FakeSettingService settings = new FakeSettingService();
        settings.seed(RuntimeGlobalSettingProvider.FIELD_AGENT_IMPL, "agentscope", false);

        assertFalse(new AgentEngineDefaultMigrator(settings).migrate());

        assertTrue(settings.writes.isEmpty(), "人工配置不能被默认值迁移覆盖");
        assertEquals("agentscope", settings.getValue(RuntimeGlobalSettingProvider.GROUP,
                RuntimeGlobalSettingProvider.FIELD_AGENT_IMPL).orElse(null));
    }

    @Test
    @DisplayName("已是新默认值时不写库")
    void noopWhenAlreadyMigrated() {
        FakeSettingService settings = new FakeSettingService();
        settings.seed(RuntimeGlobalSettingProvider.FIELD_AGENT_IMPL, "opencode-server", true);

        assertFalse(new AgentEngineDefaultMigrator(settings).migrate());

        assertTrue(settings.writes.isEmpty(), "取值已正确时不该产生一次无意义的 upsert");
    }

    @Test
    @DisplayName("字段缺失时不写库")
    void noopWhenFieldMissing() {
        FakeSettingService settings = new FakeSettingService();

        assertFalse(new AgentEngineDefaultMigrator(settings).migrate());

        assertTrue(settings.writes.isEmpty(), "分组尚未注册时迁移必须静默跳过（首次启动早期）");
    }

    /**
     * 内存版全局设置服务：只实现迁移用到的读取与写入语义。
     */
    private static final class FakeSettingService implements SysAiGlobalSettingService {

        /**
         * 已写入的字段名，用于断言「是否真的改了库」
         */
        private final List<String> writes = new ArrayList<>();

        /**
         * runtime 分组的当前行；{@code null} 表示该字段尚未注册
         */
        private Row lastRow;

        /**
         * 最后一次 upsert 传入的描述
         */
        private String lastDescription;

        /**
         * 种一行 runtime 配置。
         *
         * @param field         字段名
         * @param value         取值
         * @param systemDefault 是否属于系统默认层（无人配置过）
         */
        void seed(String field, String value, boolean systemDefault) {
            lastRow = new Row(field, value, systemDefault);
        }

        @Override
        public Optional<String> getValue(String group, String field) {
            return lastRow != null && lastRow.field.equals(field) ? Optional.of(lastRow.value) : Optional.empty();
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
            if (lastRow == null) {
                return List.of();
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("group", group);
            item.put("field", lastRow.field);
            item.put("value", lastRow.value);
            item.put("systemDefault", lastRow.systemDefault);
            return List.of(item);
        }

        @Override
        public void replace(Object request) {
            // 测试桩不落库
        }

        @Override
        public void upsertValue(String group, String field, String value, String description) {
            writes.add(field);
            lastDescription = description;
            // 与真实实现一致：写入必然带上操作人，该行随即脱离系统默认层
            lastRow = new Row(field, value, false);
        }

        @Override
        public void upsertField(String group, String field, String value, String valueType,
                                String description, int sort) {
            upsertValue(group, field, value, description);
        }

        @Override
        public int syncDescriptions(String group, Map<String, String> fieldToDescription) {
            return 0;
        }

        /**
         * runtime 分组的一行配置。
         */
        private static final class Row {

            /**
             * 字段名
             */
            private final String field;

            /**
             * 取值
             */
            private final String value;

            /**
             * 是否系统默认层
             */
            private final boolean systemDefault;

            /**
             * 构造一行配置。
             *
             * @param field         字段名
             * @param value         取值
             * @param systemDefault 是否系统默认层
             */
            private Row(String field, String value, boolean systemDefault) {
                this.field = field;
                this.value = value;
                this.systemDefault = systemDefault;
            }
        }
    }
}
