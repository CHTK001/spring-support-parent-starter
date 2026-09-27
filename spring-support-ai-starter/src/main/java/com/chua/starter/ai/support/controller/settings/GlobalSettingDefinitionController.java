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
 * 全局设置定义控制器。
 *
 * <p>返回所有全局设置服务提供者定义的分组和配置项元数据。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@RestController
@RequestMapping("/v2/global-setting-definitions")
public class GlobalSettingDefinitionController {

    /**
     * 获取所有分组定义（按 group 去重，合并同名分组的 items）
     *
     * @return 分组列表，每个分组包含 group, groupName, items（配置项列表）
     */
    @GetMapping
    public ApiResult<List<Map<String, Object>>> listDefinitions() {
        ServiceProvider<GlobalSettingProvider> provider = ServiceProvider.of(GlobalSettingProvider.class);
        List<GlobalSettingProvider> providers = provider.collect();

        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Map<String, Object>> byGroup = new LinkedHashMap<>();
        for (GlobalSettingProvider gsp : providers) {
            Map<String, Object> groupMap = byGroup.get(gsp.group());
            if (groupMap == null) {
                groupMap = new LinkedHashMap<>();
                groupMap.put("group", gsp.group());
                groupMap.put("groupName", gsp.groupName());
                groupMap.put("items", new ArrayList<Map<String, Object>>());
                byGroup.put(gsp.group(), groupMap);
                result.add(groupMap);
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) groupMap.get("items");
            for (GlobalSettingProvider.SettingItem item : gsp.getItems()) {
                // 同 group 内按 field 去重
                boolean exists = items.stream().anyMatch(m -> item.field().equals(m.get("field")));
                if (exists) {
                    continue;
                }
                Map<String, Object> itemMap = new LinkedHashMap<>();
                itemMap.put("field", item.field());
                itemMap.put("defaultValue", item.defaultValue());
                itemMap.put("valueType", item.valueType());
                itemMap.put("description", item.description());
                itemMap.put("sort", item.sort());
                items.add(itemMap);
            }
        }

        return ApiResult.ok(result);
    }
}
