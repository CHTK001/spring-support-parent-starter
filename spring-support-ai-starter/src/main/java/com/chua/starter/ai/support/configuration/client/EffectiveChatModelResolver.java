package com.chua.starter.ai.support.configuration.client;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.chua.starter.ai.support.service.SysAiUserSettingService;
import com.chua.starter.ai.support.util.AiUserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;

/**
 * 生效对话模型解析器：全项目唯一的「该用哪个模型对话」解析入口。
 *
 * <p>优先级（第一个可用者胜出）：</p>
 * <ol>
 *   <li>调用方显式指定的模型（例如连接器/Bot 绑定自己配的模型）</li>
 *   <li><b>当前用户自己的</b>默认对话模型（{@code sys_ai_user_setting}，按 userId 隔离）</li>
 *   <li>配置中心 Bot 全局默认：{@code bot/default_model}</li>
 *   <li>配置中心「对话路由」的默认对话模型：{@code runtime/default_model}
 *       （兼容旧键 {@code models/defaultChatModelId}、{@code runtime/default_chat_model}）</li>
 * </ol>
 *
 * <p><b>刻意不做的两件事：</b></p>
 * <ul>
 *   <li><b>不写死任何模型名。</b>以前多处把兜底写成字面量 {@code agnes-2.5-flash} / {@code gpt-4o}，
 *       现场没启用该模型时整条链路必然失败，且报错指名一个用户从未配过的模型。</li>
 *   <li><b>不自动挑模型。</b>各级配置都不可用时返回 {@code null}，由调用方给出明确、可执行的错误提示。</li>
 * </ul>
 *
 * <p>读取一律直连数据库（不走 Redis 缓存）：模型解析错一次就整条链路失败，
 * 缓存里残留的旧模型名会让报错与库里的配置完全对不上。</p>
 *
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EffectiveChatModelResolver {

    /**
     * 配置中心「运行时」分组（对话路由）
     */
    private static final String GROUP_RUNTIME = "runtime";

    /**
     * 配置中心「Bot」分组
     */
    private static final String GROUP_BOT = "bot";

    /**
     * 配置中心「模型」分组（旧键兼容）
     */
    private static final String GROUP_MODELS = "models";

    /**
     * 默认对话模型字段名
     */
    private static final String FIELD_DEFAULT_MODEL = "default_model";

    /**
     * 全局配置读取器（直读库）
     */
    private final GlobalSettingReader globalSettingReader;

    /**
     * 用户级配置服务（按 userId 隔离）
     */
    private final SysAiUserSettingService userSettingService;

    /**
     * 模型管理仓储（用于判断模型是否已启用）
     */
    private final SysAiProviderModelMapper providerModelMapper;

    /**
     * 解析生效对话模型。
     *
     * @param explicitModel 调用方显式指定的模型，可为空
     * @return 生效模型名；各级配置均不可用时返回 {@code null}
     */
    public String resolve(String explicitModel) {
        List<String> candidates = candidates(explicitModel);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /**
     * 解析候选模型链（已去重、已剔除「模型管理里不存在或未启用」的项）。
     *
     * @param explicitModel 调用方显式指定的模型，可为空
     * @return 按优先级排列的可用模型名列表，可能为空
     */
    public List<String> candidates(String explicitModel) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        // ① 调用方显式指定（连接器 / Bot 绑定自己的模型）
        addCandidate(ordered, explicitModel);
        // ② 当前用户自己的默认对话模型（按 userId 隔离，管理员不代管）
        addCandidate(ordered, userDefaultChatModel());
        // ③ 配置中心 Bot 分组全局默认
        addCandidate(ordered, globalSettingReader.getStrDirect(GROUP_BOT, FIELD_DEFAULT_MODEL));
        // ④ 配置中心「对话路由」的默认对话模型（含两个旧键兼容）
        addCandidate(ordered, globalSettingReader.getStrDirect(GROUP_RUNTIME, FIELD_DEFAULT_MODEL));
        addCandidate(ordered, globalSettingReader.getStrDirect(GROUP_MODELS, "defaultChatModelId"));
        addCandidate(ordered, globalSettingReader.getStrDirect(GROUP_RUNTIME, "default_chat_model"));
        return List.copyOf(ordered);
    }

    /**
     * 判断模型是否已在「模型管理」中配置且启用（多种写法都试）。
     *
     * @param modelName 模型名，可为空
     * @return 任一种写法命中已启用模型时为 true
     */
    public boolean isAvailable(String modelName) {
        for (String variant : variants(modelName)) {
            if (registryContains(variant)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 读取当前用户自己的默认对话模型。
     *
     * <p>取不到用户上下文（后台线程、未登录）时返回 空，由全局默认兜底。</p>
     *
     * @return 用户自己的默认对话模型，未设置时返回 {@code null}
     */
    private String userDefaultChatModel() {
        String userId;
        try {
            userId = AiUserContext.currentUserId();
        } catch (Exception e) {
            return null;
        }
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return userSettingService.getUserValue(userId, GROUP_RUNTIME, FIELD_DEFAULT_MODEL).orElse(null);
        } catch (Exception e) {
            log.warn("[ModelResolver] 读取用户默认模型失败 userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 加入候选：同一条取值可能有多种写法，挑出「模型管理里存在且启用」的那一种。
     *
     * <p>为什么不能无条件截断冒号前缀：配置里既有 {@code 1789547286889:agnes-3.0-flash}
     * （数字 providerId 前缀），也有 {@code agnes:agnes-3.0-flash}（provider 名前缀），
     * 而模型名本身也可能含冒号（如 {@code nvidia/nemotron-3-embed-1b:free}）。
     * 所以对每种写法都做一次「是否已启用」判定，命中哪个就用哪个。</p>
     *
     * @param set 候选集合
     * @param raw 原始取值，可为空
     */
    private void addCandidate(LinkedHashSet<String> set, String raw) {
        for (String variant : variants(raw)) {
            if (registryContains(variant)) {
                set.add(variant);
                return;
            }
        }
        if (raw != null && !raw.isBlank()) {
            log.warn("[ModelResolver] 候选模型 {} 未在模型管理中配置或已停用，跳过", raw.trim());
        }
    }

    /**
     * 列出一条取值的可能写法：原样，以及去掉「前缀:」后的形态。
     *
     * @param raw 原始取值，可为空
     * @return 去重后的写法列表，可能为空
     */
    private static List<String> variants(String raw) {
        if (raw == null) {
            return List.of();
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> variants = new LinkedHashSet<>();
        variants.add(value);
        int colon = value.indexOf(':');
        if (colon > 0) {
            String after = value.substring(colon + 1).trim();
            if (!after.isEmpty()) {
                variants.add(after);
            }
        }
        return List.copyOf(variants);
    }

    /**
     * 查询「模型管理」里是否存在该名称且已启用的模型。
     *
     * <p>查询异常时返回 true（不拦截），让调用阶段暴露真实错误，
     * 避免把一次数据库抖动变成「模型不存在」的误判。</p>
     *
     * @param modelName 模型名（已归一化写法）
     * @return 已配置且启用时为 true
     */
    private boolean registryContains(String modelName) {
        try {
            return providerModelMapper.selectCount(
                    Wrappers.<SysAiProviderModel>lambdaQuery()
                            .eq(SysAiProviderModel::getModel, modelName)
                            .eq(SysAiProviderModel::getEnabled, true)) > 0;
        } catch (Exception e) {
            log.warn("[ModelResolver] 查询模型是否可用失败 model={}: {}", modelName, e.getMessage());
            return true;
        }
    }
}
