package com.chua.starter.ai.support.configuration.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * YAML 全局设置覆盖层（{@code chua.ai.settings.*}）测试。
 *
 * <p>锁三件事：</p>
 * <ol>
 *   <li><b>扫描</b>：{@code chua.ai.settings.<group>.<field>} 形态的原始属性名
 *       （yaml / properties 落盘形态）必须被扫进覆盖表 —— 扫不进来覆盖层就是
 *       <b>静默失效</b>：运维改完 yaml 以为换成了管线、实际还在跑旧值，
 *       这比启动报错更危险；</li>
 *   <li><b>取值语义</b>：kebab/snake/大小写归一化等价命中；空串=强制清空；
 *       空挂（null 值）= 未配置回落；</li>
 *   <li><b>排除项</b>：环境变量式写法（下划线当分隔符）不入表、单段键不入表。</li>
 * </ol>
 *
 * <p>用真实 {@link StandardEnvironment} + {@link MapPropertySource} 承载属性
 * （yaml 文件在生产里正是以这种形态进入 Environment），构造期扫描即被测逻辑本体，
 * 全程离线可跑。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
class AiSettingsYamlPropertiesTest {

    /**
     * 扫描 + 组内键名归一化：snake（与 DB field 逐字一致）、kebab（yaml 书写习惯）都必须命中。
     */
    @Test
    @DisplayName("扫描原始属性名建表，kebab/snake 键等价命中")
    void scansRawPropertyNamesIntoTable() {
        AiSettingsYamlProperties props = withProperties(
                "chua.ai.settings.layout.layout_threshold", "0.42",
                "chua.ai.settings.layout.layout-model", "onnx:pp-doc-layout",
                "chua.ai.settings.ocr.recognizer_model", "onnx:paddleocrv6-rec");
        // snake 键（与 sys_ai_global_setting.field 同名）
        assertEquals("0.42", props.get("layout", "layout_threshold"));
        // yaml 里写的 kebab 键，按 snake 字段查也必须命中
        assertEquals("onnx:pp-doc-layout", props.get("layout", "layout_model"));
        assertEquals("onnx:paddleocrv6-rec", props.get("ocr", "recognizer_model"));
        // 未配置的组/字段
        assertNull(props.get("ocr", "detector_model"));
        assertNull(props.get("face", "any"));
        assertFalse(props.isEmpty());
    }

    /**
     * 完全未配置：返回空、isEmpty=true —— 生产零覆盖时行为与引入覆盖层之前完全一致。
     */
    @Test
    @DisplayName("未配置 chua.ai.settings 时为默认空实例")
    void unconfiguredIsEmpty() {
        AiSettingsYamlProperties props = withProperties();
        assertTrue(props.isEmpty());
        assertEquals("(none)", props.summary());
        assertNull(props.get("layout", "layout_threshold"));
    }

    /**
     * 取值语义：组名大小写等价；字段 kebab/snake 等价；显式空串=强制清空；空挂=未配置。
     */
    @Test
    @DisplayName("归一化等价命中；空串=强制清空；null 值=未配置")
    void valueSemantics() {
        // 显式空串与空挂（null 值）混在同一组里
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("chua.ai.settings.layout.layout_model", "");
        source.put("chua.ai.settings.layout.device", null);
        AiSettingsYamlProperties props = withProperties(source);

        // 组名归一化：大小写等价
        assertEquals("", props.get("Layout", "layout_model"));
        // 字段 kebab 写法等价命中 snake 存储
        assertEquals("", props.get("layout", "layout-model"));
        // 显式空串原样返回（消费方按空值走「未配置」分支，与用户覆盖行的显式清空同权）
        assertEquals("", props.get("layout", "layout_model"));
        assertFalse(props.isEmpty());

        // 空挂（有键无值）不入表：该组只剩空串键，device 视同未配置
        assertNull(props.get("layout", "device"));

        // 入参 null 组/字段不抛异常
        assertNull(props.get(null, "layout_model"));
        assertNull(props.get("layout", null));
    }

    /**
     * summary 供启动日志自证：按组列出被接管的键。
     */
    @Test
    @DisplayName("summary 列出覆盖清单")
    void summaryListsOverriddenKeys() {
        AiSettingsYamlProperties props = withProperties(
                "chua.ai.settings.ocr.recognizer_model", "onnx:rec",
                "chua.ai.settings.ocr.device", "cpu",
                "chua.ai.settings.layout.layout_threshold", "0.42");
        String summary = props.summary();
        assertTrue(summary.contains("layout=[layout_threshold]"), summary);
        assertTrue(summary.contains("ocr=[recognizer_model, device]"), summary);
    }

    /**
     * 排除项：环境变量式写法（下划线当分隔符）与单段键都不入表 ——
     * 前者语义在下划线映射下不可控，宁可不生效也不猜错。
     */
    @Test
    @DisplayName("环境变量式写法与单段键被排除")
    void excludesEnvStyleAndSingleSegmentNames() {
        AiSettingsYamlProperties props = withProperties(
                "CHUA_AI_SETTINGS_LAYOUT_LAYOUT_THRESHOLD", "0.42",
                "chua.ai.settings.layout", "orphan");
        assertTrue(props.isEmpty(), props.summary());
        assertNull(props.get("layout", "layout_threshold"));
    }

    /**
     * 用真实 Environment 承载属性并构造扫描（即生产路径本体）。
     *
     * @param keyValues 扁平属性键值对
     * @return 扫描后的覆盖层
     */
    private static AiSettingsYamlProperties withProperties(String... keyValues) {
        Map<String, Object> source = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            source.put(keyValues[i], keyValues[i + 1]);
        }
        return withProperties(source);
    }

    /**
     * 用真实 Environment 承载属性并构造扫描。
     *
     * @param source 扁平属性源（允许 null 值模拟空挂）
     * @return 扫描后的覆盖层
     */
    private static AiSettingsYamlProperties withProperties(Map<String, Object> source) {
        StandardEnvironment environment = new StandardEnvironment();
        // LinkedHashMap：保留键序，summary 断言要的是确定性顺序
        environment.getPropertySources().addFirst(new MapPropertySource("test-yaml", new LinkedHashMap<>(source)));
        return new AiSettingsYamlProperties(environment);
    }
}