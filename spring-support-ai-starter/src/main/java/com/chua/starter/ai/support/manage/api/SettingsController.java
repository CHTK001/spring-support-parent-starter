package com.chua.starter.ai.support.manage.api;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.configuration.client.GlobalSettingDefinitions;
import com.chua.starter.ai.support.entity.SysAiGlobalSetting;
import com.chua.starter.ai.support.manage.common.ApiErrorCode;
import com.chua.starter.ai.support.manage.common.ApiException;
import com.chua.starter.ai.support.manage.common.ApiResult;
import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.service.SysAiUserSettingService;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
     * 普通账号可写的分组白名单。
     *
     * <p>只放**纯个人偏好**：外观（语言/主题）与浏览器（链接打开方式/侧栏）。
     * 其余分组一律只有管理员能写，理由不是「保守」，而是证据：</p>
     * <ul>
     *   <li>{@code system} —— 里面有 {@code workspaceBaseDir}，可写等于能把工作间基路径挪走；</li>
     *   <li>{@code runtime} / {@code generate} / {@code asr} / {@code bot} 等 —— 模型与供应商配置；</li>
     *   <li>{@code search} —— 里面是搜索供应商的 API Key（bocha / tavily）；</li>
     * </ul>
     * <p>这些字段写进的是「自己的覆盖层」，虽不改全局，但会让该账号把请求指向自己的
     * 地址与凭据（例如 {@code provider_base_url}），等于把提示词外送，必须拦在接口层。</p>
     */
    private static final Set<String> USER_WRITABLE_GROUPS = Set.of("appearance", "browser");

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
     * 分组定义（管理员专属字段清单从这里取，不硬编码字段名）
     */
    private final GlobalSettingDefinitions globalSettingDefinitions;

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
            Map<String, Object> effective = effectiveSettingsFor(AiUserContext.requireUserId(), group);
            // 取值出口也要剔除管理员专属项：普通账号的合并层里本来就可能有该字段
            // （历史数据、或别人替他写过），不回显才谈得上「仅限管理员能看到」。
            Set<String> hidden = adminOnlyFieldSet(group);
            if (!hidden.isEmpty()) {
                effective = new HashMap<>(effective);
                hidden.forEach(effective::remove);
            }
            return ApiResult.ok(effective);
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
            // 普通账号只允许写纯个人偏好的分组，且不得写管理员专属字段。
            // 两处 PUT（/{group} 与 /{group}/overrides）必须走同一道守卫：
            // 后者原先没有任何检查，只堵前者等于留了个旁路。
            assertWritableByUser(group, settings);
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
     * 校验普通账号是否有权写该分组。
     *
     * <p>两道判定缺一不可：</p>
     * <ol>
     *   <li><b>分组白名单</b>：只放纯个人偏好（外观 / 浏览器）。其余分组里有工作间基路径、
     *       供应商凭据、模型地址等系统级配置，普通账号写进自己的覆盖层同样能把请求
     *       指向外部地址，属于必须拦在接口层的事；</li>
     *   <li><b>管理员专属字段</b>：即便分组在白名单内，也逐字段再挡一次
     *       （例如把 {@code user_tool_access} 塞进任何分组的请求体里）。</li>
     * </ol>
     *
     * @param group    设置分组
     * @param settings 待写入的字段集合
     * @throws ApiException 普通账号越权时抛出 403
     */
    private void assertWritableByUser(String group, Map<String, Object> settings) {
        if (!USER_WRITABLE_GROUPS.contains(group)) {
            throw new ApiException(ApiErrorCode.FORBIDDEN,
                    "该配置分组仅管理员可修改：" + group);
        }
        List<String> forbidden = adminOnlyFields(group);
        List<String> hit = settings.keySet().stream().filter(forbidden::contains).toList();
        if (!hit.isEmpty()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN,
                    "以下配置项仅管理员可修改：" + String.join("、", hit));
        }
    }

    /**
     * 列出某分组下仅管理员可改的字段。
     *
     * <p>字段清单来自 SPI 定义（{@code adminOnly} 标记），而不是在这里硬编码字段名：
     * 各模块自己声明哪些项是管理员专属，这里只负责执行。</p>
     *
     * @param group 设置分组
     * @return 管理员专属字段名列表
     */
    private List<String> adminOnlyFields(String group) {
        List<String> fields = new ArrayList<>();
        for (Map<String, Object> item : settingDefinitions(group)) {
            if (Boolean.TRUE.equals(item.get("adminOnly")) && item.get("field") != null) {
                fields.add(String.valueOf(item.get("field")));
            }
        }
        return fields;
    }

    /**
     * 取某分组下管理员专属的字段集合（用于取值出口剔除）。
     *
     * @param group 设置分组
     * @return 管理员专属字段名集合
     */
    private Set<String> adminOnlyFieldSet(String group) {
        return new HashSet<>(adminOnlyFields(group));
    }

    /**
     * 取某分组的分组定义项（来自启动期预热缓存）。
     *
     * @param group 设置分组
     * @return 该分组的 items；分组不存在时返回空列表
     */
    private List<Map<String, Object>> settingDefinitions(String group) {
        for (Map<String, Object> def : globalSettingDefinitions.definitions()) {
            if (group != null && group.equals(def.get("group")) && def.get("items") instanceof List<?> items) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> typed = (List<Map<String, Object>>) items;
                return typed;
            }
        }
        return List.of();
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

        // 与 /{group} 同一道守卫：本接口原先没有任何检查，只堵那一个等于留了旁路
        if (!AiUserContext.isAdmin()) {
            assertWritableByUser(group, settings);
        }
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
