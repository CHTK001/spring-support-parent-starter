package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.model.PipelineDetailDTO;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.service.settings.AiModelService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OCR 文本矫正（corrector）交付面的控制器契约测试。
 *
 * <p>{@code OcrCorrectorWiringTest} 锁住「配置 → 管线装配」的提供方与客户端两端，
 * 这里锁住中间那一层——三个对外控制器的载荷契约：
 * <ul>
 *   <li>{@code GET /v2/ocr/config}：corrector_model 必须出现在配置项里、默认空串（留空=关闭）；</li>
 *   <li>{@code PUT /v2/ocr/config}：整组替换必须带上 {@code group=ocr}（replace 是整组语义）；</li>
 *   <li>{@code GET /v2/rag/options}：ocrModels 必须含 macbert4csc（设置页才选得到）；</li>
 *   <li>{@code GET /v2/pipelines/ocr}：描述必须声明「文本矫正…非必填…留空关闭」
 *       （能力页副文案的唯一来源，改掉它页面就撒谎）。</li>
 * </ul>
 *
 * <p>只依赖静态契约（SPI 注册表 + 静态 catalog），不拉起 Spring 上下文——
 * 数据契约级别的断言用整容器既慢又脆，这是本仓库既有控制器测试的取向。
 * 服务依赖用动态 Proxy 桩掉，断言载荷形状而非数据库。
 *
 * @since 4.0.0.42
 * @author CH
 */
@DisplayName("OCR 矫正面控制器契约")
class OcrDeliveryContractTest {

    /**
     * 默认值断言用的关闭态空串。
     */
    private static final String CLOSED = "";

    /**
     * SPI 提供方声明的 corrector 字段名。
     */
    private static final String CORRECTOR = "corrector_model";

    @Test
    @DisplayName("GET /v2/ocr/config：corrector_model 必须对外暴露，默认空串（留空=关闭）")
    void ocrConfigExposesCorrectorWithEmptyDefault() {
        OcrConfigController controller = new OcrConfigController(stubSettingService(List.of(), null));

        ReturnResult<List<Map<String, Object>>> result = controller.config();

        assertTrue(result.isSuccess(), "配置读取必须成功");
        Map<String, Object> corrector = field(result.getData(), CORRECTOR);
        assertNotNull(corrector,
                CORRECTOR + " 必须出现在配置项列表——前端只认这个字段，缺了等于功能没交付");
        assertEquals(CLOSED, corrector.get("value"),
                "默认值必须是空串：留空=关闭，不阻断 detect/recognize 主链路");
        assertTrue(String.valueOf(corrector.get("description")).contains("非必填"),
                "配置项描述必须声明非必填，使用方才敢留空；实际=" + corrector.get("description"));
    }

    @Test
    @DisplayName("PUT /v2/ocr/config：整组替换必须路由到 ocr 分组并携带字段")
    void ocrConfigUpdateRoutesThroughReplaceWithGroup() {
        AtomicReference<Object> captured = new AtomicReference<>();
        OcrConfigController controller = new OcrConfigController(stubSettingService(List.of(), captured));

        controller.update(Map.of(CORRECTOR, "macbert4csc"));

        assertNotNull(captured.get(), "更新必须落到 settingService.replace");
        Map<?, ?> request = asMap(captured.get());
        assertEquals("ocr", request.get("group"), "必须显式带上 group=ocr，replace 按分组整组落库");
        Map<?, ?> fields = asMap(request.get("fields"));
        assertNotNull(fields, "字段载荷必须收敛到 fields 下");
        assertEquals("macbert4csc", fields.get(CORRECTOR),
                "裸字段体（无 fields 包裹）时应取整体作为字段映射（getOrDefault 语义）");
    }

    @Test
    @DisplayName("PUT /v2/ocr/config：显式 fields 包裹时取内层字段")
    void ocrConfigUpdateHonorsExplicitFieldsWrapper() {
        AtomicReference<Object> captured = new AtomicReference<>();
        OcrConfigController controller = new OcrConfigController(stubSettingService(List.of(), captured));

        controller.update(Map.of("fields", Map.of(CORRECTOR, "macbert4csc")));

        Map<?, ?> request = asMap(captured.get());
        assertEquals("ocr", request.get("group"));
        assertEquals("macbert4csc", asMap(request.get("fields")).get(CORRECTOR),
                "带 fields 包裹时必须取内层，不能把整个包装体当字段");
    }

    @Test
    @DisplayName("GET /v2/rag/options：ocrModels 必须含 macbert4csc（设置页选择器候选）")
    void ragOptionsOcrModelsContainMacbertCorrector() {
        AiRagSettingController controller = new AiRagSettingController(stubInterface(SysAiProviderModelMapper.class));

        ReturnResult<Map<String, Object>> result = controller.options();

        assertTrue(result.isSuccess(), "options 必须成功");
        Object ocrModels = result.getData().get("ocrModels");
        assertTrue(ocrModels instanceof List<?>, "ocrModels 必须是列表");
        assertTrue(((List<?>) ocrModels).contains("macbert4csc"),
                "macbert4csc 必须在 OCR 候选中——否则设置页选不到、矫正无从配置；实际=" + ocrModels);
    }

    @Test
    @DisplayName("GET /v2/pipelines/ocr：描述声明文本矫正非必填、留空关闭")
    void pipelineOcrDetailDeclaresOptionalCorrector() {
        AiPipelineController controller = new AiPipelineController(stubInterface(AiModelService.class));

        ReturnResult<PipelineDetailDTO> result = controller.pipelineDetail("ocr");

        assertTrue(result.isSuccess(), "ocr 管线详情必须可读");
        String description = String.valueOf(result.getData().getDescription());
        assertTrue(description.contains("文本矫正"), "描述必须点名文本矫正；实际=" + description);
        assertTrue(description.contains("非必填"), "描述必须声明非必填；实际=" + description);
        assertTrue(description.contains("留空关闭"), "描述必须声明留空关闭语义；实际=" + description);
        assertEquals("/capabilities/ocr", result.getData().getRoute(),
                "能力页路由必须指向 /capabilities/ocr（前端跳转依赖）");
    }

    /**
     * 按字段名查找配置项。
     *
     * @param fields 配置项列表
     * @param field  字段名
     * @return 匹配项，未找到返回 null
     */
    private static Map<String, Object> field(List<Map<String, Object>> fields, String field) {
        return fields.stream()
                .filter(item -> field.equals(item.get("field")))
                .findFirst()
                .orElse(null);
    }

    /**
     * 断言对象是 Map 时原样返回，否则抛类型错误。
     *
     * @param value 任意对象
     * @return Map 视图
     */
    @SuppressWarnings("unchecked")
    private static Map<?, ?> asMap(Object value) {
        assertNotNull(value, "载荷应为 Map，实际为 null");
        assertTrue(value instanceof Map<?, ?>, "载荷应为 Map，实际=" + value.getClass());
        return (Map<?, ?>) value;
    }

    /**
     * {@link SysAiGlobalSettingService} 桩：list 返回既定数据，replace 记录到 captured。
     *
     * @param current  list 返回值
     * @param captured replace 入参记录（可空）
     * @return 桩实现
     */
    private static SysAiGlobalSettingService stubSettingService(List<Map<String, Object>> current,
                                                                AtomicReference<Object> captured) {
        return (SysAiGlobalSettingService) Proxy.newProxyInstance(
                SysAiGlobalSettingService.class.getClassLoader(),
                new Class<?>[]{SysAiGlobalSettingService.class},
                (proxy, method, args) -> {
                    if ("list".equals(method.getName())) {
                        return current;
                    }
                    if ("replace".equals(method.getName())) {
                        if (captured != null) {
                            captured.set(args[0]);
                        }
                        return null;
                    }
                    return zeroValue(method.getReturnType());
                });
    }

    /**
     * 接口空桩：返回类型安全的零值（列表给空列表，其余给 null/零），
     * 方法真被调到也不会 NPE 炸断契约断言。
     *
     * @param type 接口类型
     * @param <T>  接口泛型
     * @return 空桩实例
     */
    private static <T> T stubInterface(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> zeroValue(method.getReturnType())));
    }

    /**
     * 返回类型的类型安全零值。
     *
     * @param type 返回类型
     * @return 零值
     */
    private static Object zeroValue(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == List.class || type == Collection.class || List.class.isAssignableFrom(type)) {
            return new ArrayList<>();
        }
        return null;
    }
}
