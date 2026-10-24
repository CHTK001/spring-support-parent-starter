package com.chua.starter.ai.support.manage.api;

import com.chua.starter.ai.support.util.PrivateAddressGuard;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.starter.ai.support.configuration.client.GlobalSettingDefinitions;
import com.chua.starter.ai.support.configuration.face.FaceSettingsChangedEvent;
import com.chua.starter.ai.support.configuration.ocr.OcrSettingsChangedEvent;
import com.chua.starter.ai.support.configuration.rag.RagSettingsChangedEvent;
import com.chua.starter.ai.support.configuration.runtime.AgentProviderFactory;
import com.chua.starter.ai.support.configuration.runtime.RuntimeGlobalSettingProvider;
import org.springframework.context.ApplicationEventPublisher;
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
import java.util.LinkedHashMap;
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
    /**
     * 整组放行的**纯个人偏好**分组：这些组里没有凭据/地址/系统级配置，全部字段都可用户自管。
     */
    /**
     * 「会话行为」分组：只放**行为开关**（是否启用 AI 联想下一个任务、最多联想几条）。
     *
     * <p>同口径整组放行，理由与 appearance / browser 一致：组内是布尔/整数开关，
     * <b>没有任何凭据、地址、路径</b>，它决定的是「我的会话要不要出现联想面板」，
     * 属于纯个人偏好。真正生效时仍按「会话保留自己的原值」落库 ——
     * 写这里的只是<b>默认值</b>，已有会话不会被回溯改写（见 SessionNextTaskService）。</p>
     */
    private static final Set<String> USER_WRITABLE_GROUPS = Set.of("appearance", "browser", "session");

    /**
     * 「按用户隔离的默认模型」分组：**只放行模型类字段**，其余仍管理员专属。
     *
     * <p>这些分组改为每个用户管理自己的默认模型：写入落到当前用户的覆盖层
     * （{@code sys_ai_user_setting}），读取时用户覆盖优先、全局默认兜底。</p>
     *
     * <p><b>为什么不能整组放行</b>：这些组里同时装着模型选择项与凭据/地址 ——
     * {@code provider_api_key} / {@code provider_base_url} / {@code provider_app_secret} /
     * {@code rag_embedding_api_key} / {@code image_api_key} / {@code workspaceBaseDir} 等。
     * 整组放行等于让普通账号把请求指向自己的地址与凭据（提示词外送）。
     * 实测这些分组的字段中，含 {@code model} 的 35 个全部是模型选择项，
     * 无一涉及地址、密钥、路径。</p>
     */
    private static final Set<String> USER_MODEL_GROUPS = Set.of(
            "runtime", "rag", "generate", "ocr", "image_search", "face",
            "image", "memory", "layout", "input", "bot", "asr", "tts");

    /**
     * 判断某分组是否按用户隔离写入（纯偏好组 ∪ 默认模型组）。
     *
     * @param group 设置分组
     * @return true 表示写入当前用户的覆盖层
     */
    private static boolean isUserScopedGroup(String group) {
        return USER_WRITABLE_GROUPS.contains(group) || USER_MODEL_GROUPS.contains(group);
    }

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
     * 事件发布器：用户覆盖写完后触发热更新（与全局写入同口径）
     */
    private final ApplicationEventPublisher eventPublisher;
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
        // 所有角色统一读「自己的覆盖 + 全局默认兜底」：管理员此前直接读全局层，
        // 于是管理员改自己的默认模型后看不到效果（写入的是覆盖层、读出的是全局层）。
        // 默认模型相关分组本就要求「每个用户管理自己的默认」，读出侧必须同源。
        Map<String, Object> effective = effectiveSettingsFor(AiUserContext.requireUserId(), group);
        if (!AiUserContext.isAdmin()) {
            // 取值出口也要剔除管理员专属项：普通账号的合并层里本来就可能有该字段
            // （历史数据、或别人替他写过），不回显才谈得上「仅限管理员能看到」。
            Set<String> hidden = adminOnlyFieldSet(group);
            if (!hidden.isEmpty()) {
                effective = new HashMap<>(effective);
                hidden.forEach(effective::remove);
            }
        }
        return ApiResult.ok(effective);
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

        // 写到哪一层，按「分组的语义」而不是「是否用户隔离」判定：
        //   · 纯个人偏好组（appearance / browser / session）：与角色无关，永远写本人覆盖层；
        //   · 系统级分组（runtime / ocr / rag / paper …）：管理员写「全局默认层」。
        //     运行时消费端（如 eduai 的 EduAiService、ChatClientSettingsResolver）读的是
        //     GlobalSettingReader#getStrDirect —— 只读全局层、不读用户覆盖。若管理员保存
        //     runtime 的 provider/密钥时落到覆盖层，就会「界面保存成功、运行时却读不到」，
        //     这正是此前「配置中心显示未配置/改了不生效」的根因。
        //   · 普通账号：一律写自己的覆盖层（并走白名单守卫）。
        boolean personalOnly = USER_WRITABLE_GROUPS.contains(group);
        if (!AiUserContext.isAdmin() || personalOnly) {
            if (!AiUserContext.isAdmin()) {
                // 普通账号：先校验分组白名单与管理员专属字段。
                // 两处 PUT（/{group} 与 /{group}/overrides）必须走同一道守卫。
                assertWritableByUser(group, settings);
            }
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

        return ApiResult.ok(getGroupSettings(group).data());
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
        // ① 纯个人偏好组（appearance / browser）：整组放行
        boolean wholeGroupAllowed = USER_WRITABLE_GROUPS.contains(group);
        // ② 默认模型组：**只放行模型类字段**（按字段名判定，不按分组）
        boolean modelGroup = USER_MODEL_GROUPS.contains(group);
        List<String> notAllowed = settings.keySet().stream()
                .filter(f -> !wholeGroupAllowed && !(modelGroup && isUserWritableField(f)))
                .toList();
        if (!notAllowed.isEmpty()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN,
                    "以下配置项仅管理员可修改：" + String.join("、", notAllowed));
        }
        List<String> forbidden = adminOnlyFields(group);
        List<String> hit = settings.keySet().stream().filter(forbidden::contains).toList();
        if (!hit.isEmpty()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN,
                    "以下配置项仅管理员可修改：" + String.join("、", hit));
        }

        // ③ 字段名层面再加一条**否决**规则。
        //    「含 model 就放行」是按名字猜语义：将来出现 model_base_url / model_host
        //    这类字段会被直接放行，而它是地址。人工审计会随字段增删悄悄失效，
        //    所以补一条「名字里带地址/凭据/路径语义就不放行」，把这类字段挡在门外。
        if (modelGroup) {
            List<String> addressLike = settings.keySet().stream()
                    .filter(SettingsController::isAddressLikeField)
                    .toList();
            if (!addressLike.isEmpty()) {
                throw new ApiException(ApiErrorCode.FORBIDDEN,
                        "以下配置项仅管理员可修改：" + String.join("、", addressLike));
            }
        }

        // ④ 值层面：不论字段叫什么，只要取值指向本机 / 内网就拒绝。
        //    前两道都是「按字段名」判定，而模型 id 本身就能被写成带地址的形式
        //    （http://127.0.0.1:11434/v1），字段名完全看不出来。这一道看值。
        List<String> localTargets = settings.entrySet().stream()
                .filter(e -> PrivateAddressGuard.looksLikePrivateTarget(
                        e.getValue() == null ? null : e.getValue().toString()))
                .map(Map.Entry::getKey)
                .toList();
        if (!localTargets.isEmpty()) {
            throw new ApiException(ApiErrorCode.FORBIDDEN,
                    "以下配置项不允许指向本机或内网地址：" + String.join("、", localTargets));
        }
    }

    /**
     * 「按字段名放行」之外的**显式**用户可写字段白名单。
     *
     * <p>{@link #isUserWritableField(String)} 的判据是字段名含 {@code model}，覆盖的是
     * 「选哪个模型」这一类。这里补的是**行为/时长类**字段：它们同样不含凭据、地址、路径，
     * 决定的是「我的会话等多久算超时」「我的生成任务多久算卡住」，属于个人偏好。
     * 不走字段名判定而列在这里，是为了避免把白名单写成「含 timeout 就放行」——
     * 那会把将来任何带 timeout 字样的凭据类字段一起放开。</p>
     */
    private static final Set<String> USER_WRITABLE_FIELDS = Set.of(
            "stream_idle_timeout_minutes", "task_timeout_minutes");

    /**
     * 判断某字段是否允许普通账号写入自己的覆盖层。
     *
     * <p>判据是**字段名含 {@code model}**，或命中 {@link #USER_WRITABLE_FIELDS} 显式白名单，
     * 而不是按分组：分组里同时装着模型选择项与凭据，按分组放行会连带放开凭据。
     * 实测全仓分组字段中，含 {@code model} 的 35 个
     * （{@code default_model} / {@code detector_model} / {@code rag_embedding_model} …）
     * 全部是模型选择项，没有一个是地址、密钥、路径。</p>
     *
     * @param field 字段名
     * @return true 表示普通账号可写
     */
    private static boolean isUserWritableField(String field) {
        if (field == null) {
            return false;
        }
        String lower = field.toLowerCase(Locale.ROOT);
        return lower.contains("model") || USER_WRITABLE_FIELDS.contains(lower);
    }

    /**
     * 字段名里带地址 / 凭据 / 路径语义的关键字。
     *
     * <p>这是一条**否决**规则：即便字段落在放行分组内、名字里也确实含 {@code model}
     * （例如 {@code model_base_url}），只要命中这里就仍按管理员专属处理。
     * 判据故意放宽到「名字里出现即否决」，因为误杀只是让管理员去改一次，
     * 漏放则是把地址与凭据交给普通账号。</p>
     */
    private static final String[] ADDRESS_LIKE_KEYWORDS = {
            "url", "uri", "host", "endpoint", "addr", "port", "proxy", "socket",
            "path", "dir", "folder", "file",
            "key", "secret", "token", "password", "credential", "auth",
    };

    /**
     * 判断字段名是否带地址 / 凭据 / 路径语义。
     *
     * @param field 字段名
     * @return true 表示普通账号不得写入
     */
    private static boolean isAddressLikeField(String field) {
        if (field == null) {
            return false;
        }
        String lower = field.toLowerCase(Locale.ROOT);
        for (String keyword : ADDRESS_LIKE_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
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
        publishUserSettingsChanged(group, userId);
    }

    /**
     * 用户覆盖写入后发布配置变更事件，使依赖该分组的缓存/派生实例失效。
     *
     * <p><b>为什么放到独立线程发布：</b>本应用的 {@code FaceClientManager}、
     * {@code RagClientManager} 持有全局单例并按事件重建，而它们读取配置走
     * {@code GlobalSettingReader}（用户覆盖优先）。若在请求线程直接发布，
     * 重建会把「发起人这一名用户的覆盖」固化成全局实例，等于把个人配置泄漏给所有人。
     * 换到无请求身份的新线程发布，重建只读到全局配置，行为与管理员改全局时一致；
     * 用户级差异仍由消费端按请求身份实时解析。</p>
     *
     * @param group 设置分组名称
     */
    private void publishUserSettingsChanged(String group, String userId) {
        if (group == null || group.isBlank()) {
            return;
        }
        try {
            // 同步发布：处理链需要当前用户身份才能按「该用户的覆盖」重建其账号级实例。
            // 标记 userScoped=true 后，全局共享实例（FaceClientManager / RagClientManager）会
            // 主动跳过，只由按账号维度的持有者（FaceStores / ImageSearchStores）定向重建，
            // 因此不会把发起人的配置串到其他用户。
            eventPublisher.publishEvent(new FaceSettingsChangedEvent(group, true, userId));
            if ("ocr".equals(group)) {
                eventPublisher.publishEvent(new OcrSettingsChangedEvent(group, true, userId));
            }
            if ("rag".equals(group)) {
                eventPublisher.publishEvent(new RagSettingsChangedEvent(group, true, userId));
            }
        } catch (Exception e) {
            log.warn("[用户覆盖设置] 发布分组 {} 变更事件失败: {}", group, e.getMessage());
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
        // 值来源（USER / GLOBAL / SYSTEM_DEFAULT）单独放一个键，不混进 field->value 映射：
        // 那个映射的键会被前端当成配置字段名遍历，混进去会被当成一个不存在的设置项。
        // SYSTEM_DEFAULT 必须能传到前端——实测 2026-09-30 那批一次性种子（onnx 等）从来没人配过，
        // 界面上只显示「继承全局：onnx」会把系统默认说成别人配的。
        Map<String, Object> origins = new LinkedHashMap<>();
        for (Map<String, Object> item : userSettingService.listMerged(userId, group)) {
            String field = String.valueOf(item.get("field"));
            boolean overridden = Boolean.TRUE.equals(item.get("overridden"));
            result.put(field, overridden || !isSecretField(field) ? item.get("value") : MASKED);
            Object origin = item.get("origin");
            if (origin != null) {
                Map<String, Object> meta = new LinkedHashMap<>(2);
                meta.put("origin", origin);
                meta.put("overridden", overridden);
                if (item.get("configuredBy") != null) {
                    meta.put("configuredBy", item.get("configuredBy"));
                }
                origins.put(field, meta);
            }
        }
        result.put("__meta", origins);
        // runtime 分组额外回带「界面选择」与「实际生效运行时」：被收敛的兼容引擎自本版本起
        // 是全功能平替，实例化时复用完整工具链运行时，管理端与验收需要能直接看到这层映射，
        // 而不是只凭界面文案。与 __meta 一样用双下划线前缀，不混进 field->value。
        if (RuntimeGlobalSettingProvider.GROUP.equals(group)) {
            Object selectedRaw = result.get(RuntimeGlobalSettingProvider.FIELD_AGENT_IMPL);
            String selected = selectedRaw == null ? null : String.valueOf(selectedRaw);
            Map<String, Object> runtime = new LinkedHashMap<>(2);
            runtime.put("selected", selected);
            runtime.put("effective", AgentProviderFactory.effectiveRuntime(selected));
            result.put("__runtime", runtime);
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
        publishUserSettingsChanged(group, userId);
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
