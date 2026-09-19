package com.chua.starter.ai.support.manage.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.entity.SysAiGlobalSetting;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.service.SysAiUserSettingService;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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

    /**
     * 凭据类字段：值不外发给非管理员
     */
    private static final String MASKED = "***";

    /**
     * 疑似凭据字段名关键字（小写匹配）
     */
    private static final String[] SECRET_KEYWORDS = {"api_key", "apikey", "app_key", "secret", "token", "password"};

    /**
     * 全局设置服务
    */
    private final SysAiGlobalSettingService globalSettingService;

    /**
     * 用户覆盖层服务
     */
    private final SysAiUserSettingService userSettingService;

    /**
     * 获取指定分组对本人生效的设置
     *
     * <p>管理员读全局默认层；普通用户读「全局默认 + 自己的覆盖」的合并结果，响应形状仍是
     * {@code field -> value}。控制台启动期会按分组读自己的外观/浏览器/运行时配置，
     * 该出口若只放管理员，普通用户一进页面每个分组都 403。</p>
     *
     * <p>凭据类字段只有管理员或用户自己覆盖过才回显原值，否则回掩码，避免把全局密钥外发给普通用户。</p>
     *
     * @param group 设置分组名称（如 models）
     * @return 分组下 field -> value 的键值对
     */
    @GetMapping("/{group}")
    public ApiResult<Map<String, Object>> getGroupSettings(@PathVariable String group) {
        if (!AiUserContext.isAdmin()) {
            return ApiResult.ok(effectiveSettingsFor(AiUserContext.requireUserId(), group));
        }
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
     * <p>管理员逐字段 upsert 全局默认层，由 Service 发布变更事件触发热更新；
     * 普通用户写入自己的覆盖层，全局默认不动。</p>
     *
     * @param group    设置分组名称
     * @param settings 待更新的 field -> value 集合
     * @return 更新后的分组设置
     */
    @PutMapping("/{group}")
    public ApiResult<Map<String, Object>> updateGroupSettings(
            @PathVariable String group,
            @RequestBody Map<String, Object> settings) {

        if (!AiUserContext.isAdmin()) {
            String userId = AiUserContext.requireUserId();
            writeOverrides(userId, group, settings);
            return ApiResult.ok(effectiveSettingsFor(userId, group));
        }

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

    /**
     * 当前登录用户在该分组的「全局默认 + 自己的覆盖」合并视图
     *
     * @param group 设置分组名称
     * @return 合并后的配置项列表，每项含 overridden 与 defaultValue
     */
    @GetMapping("/{group}/merged")
    public ApiResult<List<Map<String, Object>>> getMergedSettings(@PathVariable String group) {
        String userId = AiUserContext.requireUserId();
        List<Map<String, Object>> merged = userSettingService.listMerged(userId, group);
        boolean admin = AiUserContext.isAdmin();
        for (Map<String, Object> item : merged) {
            if (!admin && Boolean.FALSE.equals(item.get("overridden")) && isSecretField(item.get("field"))) {
                item.put("value", MASKED);
            }
            item.put("defaultValue", admin || !isSecretField(item.get("field"))
                    ? item.get("defaultValue") : MASKED);
        }
        return ApiResult.ok(merged);
    }

    /**
     * 写入当前登录用户的覆盖项（只影响自己，不改全局默认）
     *
     * @param group    设置分组名称
     * @param settings 待覆盖的 field -> value 集合；值传 null 表示撤销该字段覆盖
     * @return 覆盖后的合并视图
     */
    @PutMapping("/{group}/overrides")
    public ApiResult<List<Map<String, Object>>> updateGroupOverrides(
            @PathVariable String group,
            @RequestBody Map<String, Object> settings) {

        String userId = AiUserContext.requireUserId();
        writeOverrides(userId, group, settings);

        log.info("[用户覆盖设置] user={}, group={}, fields={}", userId, group, settings.keySet());
        return ApiResult.ok(userSettingService.listMerged(userId, group));
    }

    /**
     * 逐字段写入（或撤销）当前用户的覆盖项。
     *
     * <p>值等于掩码的字段直接跳过：控制台把读取接口回显的 {@code ***} 当作原值提交回来时，
     * 会把用户已经保存的凭据覆盖成掩码。</p>
     *
     * @param userId   归属用户标识
     * @param group    设置分组名称
     * @param settings 待覆盖的 field -> value 集合；值传 null 表示撤销该字段覆盖
     */
    private void writeOverrides(String userId, String group, Map<String, Object> settings) {
        for (Map.Entry<String, Object> entry : settings.entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                userSettingService.deleteValue(userId, group, field);
                continue;
            }
            if (MASKED.equals(value.toString())) {
                continue;
            }
            String valueType = value instanceof Boolean ? "BOOLEAN"
                    : value instanceof Number ? "NUMBER" : "STRING";
            userSettingService.upsertValue(userId, group, field, value.toString(), valueType, null);
        }
    }

    /**
     * 当前用户在某分组的生效值，形状与全局层读取一致（{@code field -> value}）。
     *
     * @param userId 归属用户标识
     * @param group  设置分组名称
     * @return 合并后的键值对，凭据类字段未自行覆盖时为掩码
     */
    private Map<String, Object> effectiveSettingsFor(String userId, String group) {
        Map<String, Object> result = new HashMap<>();
        for (Map<String, Object> item : userSettingService.listMerged(userId, group)) {
            String field = String.valueOf(item.get("field"));
            boolean overridden = Boolean.TRUE.equals(item.get("overridden"));
            result.put(field, overridden || !isSecretField(field) ? item.get("value") : MASKED);
        }
        return result;
    }

    /**
     * 撤销当前登录用户在指定分组下的全部覆盖，回到全局默认
     *
     * @param group 设置分组名称
     * @return 撤销后的合并视图
     */
    @DeleteMapping("/{group}/overrides")
    public ApiResult<List<Map<String, Object>>> clearGroupOverrides(@PathVariable String group) {
        String userId = AiUserContext.requireUserId();
        int deleted = userSettingService.clearGroup(userId, group);
        log.info("[用户覆盖设置] user={} 撤销分组 {} 的 {} 项覆盖", userId, group, deleted);
        return ApiResult.ok(userSettingService.listMerged(userId, group));
    }

    /**
     * 判断字段名是否属于凭据类
     *
     * @param field 字段名
     * @return 是否疑似凭据
     */
    private static boolean isSecretField(Object field) {
        if (field == null) {
            return false;
        }
        String lower = String.valueOf(field).toLowerCase(Locale.ROOT);
        for (String keyword : SECRET_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
