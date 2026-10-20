package com.chua.starter.ai.support.configuration.ocr;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.deeplearning.support.engine.ModelRegistry;
import com.chua.deeplearning.support.ocr.OcrPipeline;
import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.client.ModelIds;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OCR 文本矫正（corrector_model）配置链路测试。
 *
 * <p>文本矫正是 OCR 管线的<strong>非必填</strong>能力：留空必须照常构建管线（不阻断
 * detect/recognize 主链路），配置 macbert4csc 后必须真正挂到 {@link OcrPipeline}
 * 的 corrector 上——否则配置「保存成功却不生效」。本用例锁住两端：
 * {@link OcrGlobalSettingProvider} 的设置项声明与 {@link OcrClientManager} 的装配。</p>
 *
 * <p>管线构建只做图编排：detector/recognizer 用占位名（none），corrector 走注册表
 * 懒加载包装（不实例化权重、不触发下载），全程离线可跑。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
class OcrCorrectorWiringTest {

    /**
     * 发现全部本地模型注册项：corrector 装配依赖注册表里存在 macbert4csc 条目。
     */
    @BeforeAll
    static void discoverRegistry() {
        ModelRegistry.discoverAll();
    }

    /**
     * 设置项声明：默认留空（非必填）、描述标明留空关闭，排序稳定在 restorer 与 device 之间。
     */
    @Test
    @DisplayName("设置项声明 corrector_model：默认留空、非必填")
    void providerDeclaresOptionalCorrector() {
        List<GlobalSettingProvider.SettingItem> items = new OcrGlobalSettingProvider().getItems();

        GlobalSettingProvider.SettingItem corrector = items.stream()
                .filter(item -> "corrector_model".equals(item.field()))
                .findFirst()
                .orElse(null);
        assertNotNull(corrector, "ocr 分组必须声明 corrector_model 设置项");
        assertEquals("", corrector.defaultValue(), "默认留空：未配置即关闭，OCR 不依赖文本矫正");
        assertTrue(corrector.description().contains("留空关闭"), "描述需标明留空关闭语义");
        assertTrue(corrector.description().contains("非必填"), "描述需标明非必填语义");

        int restorer = indexOf(items, "restorer_model");
        int declared = indexOf(items, "corrector_model");
        int device = indexOf(items, "device");
        assertTrue(restorer >= 0 && restorer < declared && declared < device,
                "corrector_model 应排在 restorer_model 之后、device 之前");
    }

    /**
     * 留空路径（非必填主语义）：OCR 管线照常构建，corrector 为空即不启用。
     */
    @Test
    @DisplayName("未配置 corrector：管线照常构建，corrector 为空")
    void blankCorrectorKeepsPipelineBuilding() {
        OcrPipeline pipeline = new OcrClientManager(baseReader()).current();

        assertNotNull(pipeline, "文本矫正留空不得阻断 OCR 管线构建");
        assertNull(pipeline.corrector(), "未配置时 corrector 应为 空（不启用）");
    }

    /**
     * 配置路径：corrector_model=macbert4csc 必须装配到管线实例上。
     */
    @Test
    @DisplayName("配置 macbert4csc：corrector 挂载生效")
    void configuredCorrectorIsWired() {
        MapSettingReader reader = baseReader().put("corrector_model", "macbert4csc");

        OcrPipeline pipeline = new OcrClientManager(reader).current();

        assertNotNull(pipeline.corrector(), "配置了文本矫正模型后必须装配到管线（配置要生效）");
    }

    /**
     * 设置页模型选择器实际保存的形态是 {@code providerId:model}（如 onnx:macbert4csc），
     * 注册表按裸名精确匹配——不归一会静默查空（配置了却不生效），锁死该回归。
     */
    @Test
    @DisplayName("配置 onnx:macbert4csc（选择器形态）：剥前缀后装配生效")
    void prefixedCorrectorIdIsNormalized() {
        MapSettingReader reader = baseReader().put("corrector_model", "onnx:macbert4csc");

        OcrPipeline pipeline = new OcrClientManager(reader).current();

        assertNotNull(pipeline.corrector(),
                "带提供商前缀的选择器值必须归一为裸名并装配到管线（否则配置了却不生效）");
    }

    /**
     * 裸名归一语义：首冒号前为提供商前缀；无前缀、空串、null 原样保留留空语义。
     */
    @Test
    @DisplayName("ModelIds.bare：剥提供商前缀，空值语义保持")
    void bareNormalizesProviderPrefix() {
        assertEquals("macbert4csc", ModelIds.bare("onnx:macbert4csc"), "选择器值剥前缀取裸名");
        assertEquals("macbert4csc", ModelIds.bare("macbert4csc"), "已是裸名原样返回");
        assertEquals("", ModelIds.bare(""), "空串原样（留空=关闭）");
        assertNull(ModelIds.bare(null), "null 原样（留空=关闭）");
    }

    /**
     * 取设置项在列表中的下标，不存在时返回 -1。
     *
     * @param items 设置项列表
     * @param field 字段名
     * @return 下标；不存在返回 -1
     */
    private static int indexOf(List<GlobalSettingProvider.SettingItem> items, String field) {
        for (int i = 0; i < items.size(); i++) {
            if (field.equals(items.get(i).field())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 构造仅含必填占位模型（detector/recognizer=none）的 ocr 分组设置。
     *
     * @return 设置读取器
     */
    private static MapSettingReader baseReader() {
        return new MapSettingReader()
                .put("detector_model", "none")
                .put("recognizer_model", "none");
    }

    /**
     * 按内存键值模拟 ocr 分组全局设置的读取器（不落库、不读缓存、不触发用户覆盖）。
     *
     * <p>基类的 getBool / getDecimalOrNull 均经由 getStr 取值，覆盖 getStr 即可。</p>
     */
    private static final class MapSettingReader extends GlobalSettingReader {

        /**
         * 字段 → 值
         */
        private final Map<String, String> values = new HashMap<>();

        private MapSettingReader() {
            // 第 4 参 = YAML 覆盖层（plugin.ai.settings.*）：本测试双轨无 yaml 语义，
            // 传 null —— 读取器的 yamlValueOf 对 null 判空即跳过，链路行为与从前一致
            super(null, null, null, null);
        }

        /**
         * 登记一个字段值（链式）。
         *
         * @param field 字段名
         * @param value 值
         * @return this
         */
        private MapSettingReader put(String field, String value) {
            values.put(field, value);
            return this;
        }

        @Override
        public String getStr(String group, String field) {
            return "ocr".equals(group) ? values.get(field) : null;
        }
    }
}
