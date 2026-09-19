package com.chua.starter.ai.support.controller.settings;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.ai.support.manage.common.ApiResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局设置定义控制器
 * 返回所有 GlobalSettingProvider SPI 定义的分组和配置项元数据
 *
 * @author CH
 * @since 4.0.0.42
 */
@RestController
@RequestMapping("/v2/global-setting-definitions")
public class GlobalSettingDefinitionController {

    /**
     * 获取所有分组定义
     *
     * @return 分组列表，每个分组包含 group, groupName, items（配置项列表）
     */
    @GetMapping
    public ApiResult<List<Map<String, Object>>> listDefinitions() {
        ServiceProvider<GlobalSettingProvider> provider = ServiceProvider.of(GlobalSettingProvider.class);
        List<GlobalSettingProvider> providers = provider.collect();

        List<Map<String, Object>> result = new ArrayList<>();
        for (GlobalSettingProvider gsp : providers) {
            Map<String, Object> groupMap = new LinkedHashMap<>();
            groupMap.put("group", gsp.group());
            groupMap.put("groupName", gsp.groupName());

            List<Map<String, Object>> items = new ArrayList<>();
            for (GlobalSettingProvider.SettingItem item : gsp.getItems()) {
                Map<String, Object> itemMap = new LinkedHashMap<>();
                itemMap.put("field", item.field());
                itemMap.put("defaultValue", item.defaultValue());
                itemMap.put("valueType", item.valueType());
                itemMap.put("description", item.description());
                itemMap.put("sort", item.sort());
                items.add(itemMap);
            }
            groupMap.put("items", items);
            result.add(groupMap);
        }

        return ApiResult.ok(result);
    }
}
