package com.chua.starter.ai.support.manage.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.SysAiGlobalSetting;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局设置 Controller
 * 提供按分组获取和更新设置的接口
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@RestController
@RequestMapping("/v2/settings")
@RequiredArgsConstructor
public class SettingsController {

    /** 全局设置服务 */
    private final SysAiGlobalSettingService globalSettingService;

    /**
    * 获取指定分组的所有设置
    *
    * @param group 设置分组名称（如 models）
    * @return 分组下 field -> value 的键值对
    */
    @GetMapping("/{group}")
    public ApiResult<Map<String, Object>> getGroupSettings(@PathVariable String group) {
        List<Map<String, Object>> settings = globalSettingService.list(group);

        Map<String, Object> result = new HashMap<>();
        for (Map<String, Object> setting : settings) {
            String field = (String) setting.get("field");
            Object value = setting.get("value");
            result.put(field, value);
        }

        return ApiResult.ok(result);
    }

    /**
    * 更新指定分组的设置
    *
    * <p>逐字段 upsert 落库，由 Service 发布变更事件触发热更新。</p>
    *
    * @param group    设置分组名称
    * @param settings 待更新的 field -> value 集合
    * @return 更新后的分组设置
    */
    @PutMapping("/{group}")
    public ApiResult<Map<String, Object>> updateGroupSettings(
            @PathVariable String group,
            @RequestBody Map<String, Object> settings) {

        for (Map.Entry<String, Object> entry : settings.entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();
            String valueStr = value == null ? "" : value.toString();

            // 调用Service的upsertValue，会自动发布事件触发热更新
            globalSettingService.upsertValue(group, field, valueStr, null);
        }

        log.info("更新分组设置: group={}, fields={}", group, settings.keySet());

        return ApiResult.ok(getGroupSettings(group).getData());
    }
}
