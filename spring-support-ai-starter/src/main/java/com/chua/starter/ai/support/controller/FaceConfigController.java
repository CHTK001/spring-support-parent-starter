package com.chua.starter.ai.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.configuration.face.FaceClientManager;
import com.chua.starter.ai.support.service.AiFaceService;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.common.support.annotations.Permission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 人脸识别配置管理控制器。
 *
 * <p>提供 {@code face} 分组全局配置的读取与更新，以及可用模型清单。
 * 配置更新后 {@link FaceClientManager} 监听事件热重建，实时生效。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/face/config")
@RequiredArgsConstructor
@Tag(name = "AI 人脸识别 - 配置管理")
public class FaceConfigController {

    /**
     * 人脸配置分组
     */
    private static final String GROUP = "face";

    /**
     * 全局设置服务
     */
    private final SysAiGlobalSettingService settingService;

    /**
     * 人脸识别服务
     */
    private final AiFaceService faceService;

    /**
     * 获取人脸识别配置项定义与当前值。
     *
     * @return 配置列表（field/value/valueType/description/sort）
     */
    @Permission("admin")
    @Operation(summary = "获取人脸识别配置", description = "返回 face 分组全部配置项及当前值")
    @GetMapping
    public ReturnResult<List<Map<String, Object>>> config() {
        List<Map<String, Object>> fields = new ArrayList<>();

        List<Map<String, Object>> current = settingService.list(GROUP);
        Map<String, Object> valueMap = current.stream()
                .collect(Collectors.toMap(m -> String.valueOf(m.get("field")), m -> m, (a, b) -> a));

        List<GlobalSettingProvider> providers = ServiceProvider.of(GlobalSettingProvider.class).collect();
        for (GlobalSettingProvider provider : providers) {
            if (!GROUP.equals(provider.group())) {
                continue;
            }
            for (GlobalSettingProvider.SettingItem item : provider.getItems()) {
                Map<String, Object> field = new LinkedHashMap<>();
                field.put("field", item.field());
                field.put("valueType", item.valueType());
                field.put("description", item.description());
                field.put("sort", item.sort());
                Map<String, Object> existing = (Map<String, Object>) valueMap.get(item.field());
                field.put("value", existing != null ? existing.get("value") : item.defaultValue());
                fields.add(field);
            }
        }

        // 补充数据库中已存在但 SPI 未声明的字段（保留兼容）
        for (Map<String, Object> m : current) {
            String f = String.valueOf(m.get("field"));
            if (fields.stream().noneMatch(x -> f.equals(x.get("field")))) {
                Map<String, Object> field = new LinkedHashMap<>();
                field.put("field", f);
                field.put("valueType", m.get("valueType"));
                field.put("description", m.get("description"));
                field.put("sort", m.get("sort"));
                field.put("value", m.get("value"));
                fields.add(field);
            }
        }

        fields.sort((a, b) -> Integer.compare(toInt(a.get("sort")), toInt(b.get("sort"))));
        return ReturnResult.ok(fields);
    }

    /**
     * 更新人脸识别配置（整组替换，触发实例热重建）。
     *
     * @param body 配置请求（群体 + 字段）
     * @return 更新结果
     */
    @Permission("admin")
    @Operation(summary = "更新人脸识别配置", description = "整组替换 face 配置并触发热重建")
    @PutMapping
    public ReturnResult<?> update(@RequestBody Map<String, Object> body) {
        Map<String, Object> replaceBody = new LinkedHashMap<>();
        replaceBody.put("group", GROUP);
        replaceBody.put("fields", body.getOrDefault("fields", body));
        settingService.replace(replaceBody);
        log.info("[FaceConfig] face 分组配置已更新并触发热重建");
        return ReturnResult.ok("人脸识别配置已更新");
    }

    /**
     * 可用人脸模型清单（复用 aiface服务.列表模型）。
     *
     * @return 模型清单
     */
    @Permission("admin")
    @Operation(summary = "人脸模型清单", description = "枚举可用的人脸检测/特征/活体/超分等模型")
    @GetMapping("/models")
    public ReturnResult<Map<String, List<String>>> models() {
        try {
            return ReturnResult.ok(faceService.listModels());
        } catch (Throwable e) {
            // 未配置人脸 provider 时，底层可能抛 RuntimeException，也可能是
            // NoClassDefFoundError / ExceptionInInitializerError 这类 Error（能力实现没装配）。
            // 对调用方来说都等价于「没有可用模型」，返回空清单即可，不该把设置页带成 500。
            log.warn("[FaceConfig] 人脸模型清单不可用（可能未配置 provider）：{}", e.toString());
            return ReturnResult.ok(Map.of());
        }
    }

    /**
     * 安全转换整数。
     *
     * @param obj 值
     * @return int，非法返回 0
     */
    private static int toInt(Object obj) {
        if (obj instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (Exception e) {
            return 0;
        }
    }
}
