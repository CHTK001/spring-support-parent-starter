package com.chua.starter.ai.support.controller;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.common.support.annotations.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
* 通用能力配置控制器基类。
*
* <p>按指定分组（如 image / layout）读取 GlobalSettingProvider SPI 定义的配置项，
* 返回 {字段,值,值类型,description,排序}，供前端 {@code CapabilityConfigForm} 数据驱动渲染。
* 更新时整组替换。</p>
*
* <p>子类只需声明分组，并配置 {@code @RequestMapping} 即可。</p>
*
* @since 4.0.0.42
* @author CH
 */
@RequiredArgsConstructor
public abstract class AbstractCapabilityConfigController {

    /**
    * 全局设置服务
    */
    protected final SysAiGlobalSettingService settingService;

    /**
    * 配置分组（如 ocr / face / 镜像 / layout）
    * @return 群体的结果
    */
    protected abstract String group();

    /**
    * 获取配置项定义与当前值。
    *
    * @return 配置列表（field/value/valueType/description/sort）
    */
    @Permission("admin")
    @GetMapping
    public ReturnResult<List<Map<String, Object>>> config() {
        List<Map<String, Object>> fields = new ArrayList<>();

        List<Map<String, Object>> current = settingService.list(group());
        Map<String, Object> valueMap = current.stream()
                .collect(Collectors.toMap(m -> String.valueOf(m.get("field")), m -> m, (a, b) -> a));

        List<GlobalSettingProvider> providers = ServiceProvider.of(GlobalSettingProvider.class).collect();
        for (GlobalSettingProvider provider : providers) {
            if (!group().equals(provider.group())) {
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
    * 更新配置（整组替换）。
    *
    * @param body 配置请求（群体 + 字段）
    * @return 更新结果
    */
    @Permission("admin")
    @PutMapping
    public ReturnResult<?> update(@RequestBody Map<String, Object> body) {
        Map<String, Object> replaceBody = new LinkedHashMap<>();
        replaceBody.put("group", group());
        replaceBody.put("fields", body.getOrDefault("fields", body));
        settingService.replace(replaceBody);
        return ReturnResult.ok(group() + " 配置已更新");
    }

    /**
    * 安全转换整数。
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
