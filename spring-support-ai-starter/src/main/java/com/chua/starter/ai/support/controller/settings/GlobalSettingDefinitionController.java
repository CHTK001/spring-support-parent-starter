package com.chua.starter.ai.support.controller.settings;

import com.chua.starter.ai.support.configuration.client.GlobalSettingDefinitions;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
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
 * <p>返回所有全局设置服务提供者定义的分组和配置项元数据。
 * 数据来源是启动期预热好的缓存，见 {@link GlobalSettingDefinitions}。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@RestController
@RequestMapping("/v2/global-setting-definitions")
@RequiredArgsConstructor
public class GlobalSettingDefinitionController {

    /**
     * 分组定义缓存（启动期由 GlobalSettingInitializer 预热）
     */
    private final GlobalSettingDefinitions globalSettingDefinitions;

    /**
     * 获取所有分组定义（按 group 去重，合并同名分组的 items）
     *
     * @return 分组列表，每个分组包含 group, groupName, items（配置项列表）
     */
    @GetMapping
    public ApiResult<List<Map<String, Object>>> listDefinitions() {
        // 走启动期预热好的缓存：同一句 SPI 发现在请求线程里返回空（见 GlobalSettingDefinitions 说明），
        // 而启动期 main 线程能正常发现，故请求线程直接读那次的结果。
        List<Map<String, Object>> definitions = globalSettingDefinitions.definitions();
        // 管理员专属项按角色过滤。必须在缓存之外做：缓存是启动期建的、与调用者角色无关，
        // 一旦把过滤结果写进缓存，就会按第一个访问者的角色把所有人的可见集固定下来。
        return ApiResult.ok(AiUserContext.isAdmin() ? definitions : withoutAdminOnly(definitions));
    }

    /**
     * 剔除管理员专属配置项。
     *
     * <p>只在副本上删，不动缓存里的原对象：缓存是共享的，就地改会污染所有后续请求。</p>
     *
     * @param definitions 原始分组定义
     * @return 不含管理员专属项的分组定义副本
     */
    private static List<Map<String, Object>> withoutAdminOnly(List<Map<String, Object>> definitions) {
        List<Map<String, Object>> result = new ArrayList<>(definitions.size());
        for (Map<String, Object> group : definitions) {
            Object itemsObj = group.get("items");
            if (!(itemsObj instanceof List<?> items)) {
                result.add(group);
                continue;
            }
            List<Map<String, Object>> kept = new ArrayList<>(items.size());
            for (Object item : items) {
                if (!(item instanceof Map<?, ?> itemMap) || Boolean.TRUE.equals(itemMap.get("adminOnly"))) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> copy = (Map<String, Object>) itemMap;
                kept.add(copy);
            }
            Map<String, Object> copy = new LinkedHashMap<>(group);
            copy.put("items", kept);
            result.add(copy);
        }
        return result;
    }
}
