package com.chua.starter.ai.support.controller;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
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
 * AI OCR 配置管理控制器。
 *
 * <p>提供 {@code ocr} 分组全局配置的读取与更新。
 * OCR 管线（detector/recognizer/direction/restorer/corrector）与检测置信度阈值均可配置，
 * 其中 corrector（文本矫正，如 macbert4csc）非必填，留空关闭。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Slf4j
@RestController
@RequestMapping("/v2/ocr/config")
@RequiredArgsConstructor
@Tag(name = "AI OCR - 配置管理")
public class OcrConfigController {

    /**
     * OCR 配置分组
     */
    private static final String GROUP = "ocr";

    /**
     * 全局设置服务
     */
    private final SysAiGlobalSettingService settingService;

    /**
     * 获取 OCR 配置项定义与当前值。
     *
     * @return 配置列表（field/value/valueType/description/sort）
     */
    @Permission("admin")
    @Operation(summary = "获取 OCR 配置", description = "返回 ocr 分组全部配置项及当前值（detector/recognizer/threshold/direction/restorer/corrector）")
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

        fields.sort((a, b) -> Integer.compare(toInt(a.get("sort")), toInt(b.get("sort"))));
        return ReturnResult.ok(fields);
    }

    /**
     * 更新 OCR 配置（整组替换）。
     *
     * @param body 配置请求（群体 + 字段）
     * @return 更新结果
     */
    @Permission("admin")
    @Operation(summary = "更新 OCR 配置", description = "整组替换 ocr 配置（detector/recognizer/threshold/corrector 等）")
    @PutMapping
    public ReturnResult<?> update(@RequestBody Map<String, Object> body) {
        Map<String, Object> replaceBody = new LinkedHashMap<>();
        replaceBody.put("group", GROUP);
        replaceBody.put("fields", body.getOrDefault("fields", body));
        settingService.replace(replaceBody);
        log.info("[OcrConfig] ocr 分组配置已更新");
        return ReturnResult.ok("OCR 配置已更新");
    }

    /**
     * 转为int
     *
     * @param obj obj
     * @return 转为int的结果
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
