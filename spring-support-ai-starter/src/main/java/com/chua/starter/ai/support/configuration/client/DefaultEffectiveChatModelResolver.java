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
import java.util.Optional;

/**
 * {@link EffectiveChatModelResolver} 的默认实现：按「显式 &gt; Bot 默认 &gt; 对话默认」解析模型，
 * 并以「模型管理」中的启用状态为准剔除不可用候选。
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultEffectiveChatModelResolver implements EffectiveChatModelResolver {

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
     * {@inheritDoc}
     *
     * @param explicitModel 调用方显式指定的模型，可为空
     * @return 生效模型名；各级配置均不可用时返回 {@code null}
     */
    @Override
    public String resolve(String explicitModel) {
        List<String> candidates = candidates(explicitModel);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    /**
     * {@inheritDoc}
     *
     * @param explicitModel 调用方显式指定的模型，可为空
     * @return 按优先级排列的可用模型名列表，可能为空
     */
    @Override
    public List<String> candidates(String explicitModel) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        // ① 调用方显式指定（连接器 / Bot 绑定自己的模型）
        addCandidate(ordered, explicitModel);
        // ② Bot 模型：bot/default_model（用户覆盖优先，其次全局）
        //    连接器未单独配模型时，Bot 模型是它的第一层兜底。
        //    该层「用户覆盖行为空」视为未配置，继续往下走默认模型。
        Optional<String> userBot = userOverride(GROUP_BOT, FIELD_DEFAULT_MODEL);
        if (userBot.isPresent()) {
            addCandidate(ordered, userBot.get());
        } else {
            addCandidate(ordered, globalSettingReader.getStrDirect(GROUP_BOT, FIELD_DEFAULT_MODEL));
        }
        // ③ 默认模型：runtime/default_model（用户覆盖优先，其次全局 + 两个旧键兼容）
        //
        //    判据是「用户层有没有覆盖行」，不是「值是否为空」：
        //    用户**显式清空**（有覆盖行、值为空）时必须**就此停住**，不再回退全局 ——
        //    否则「重置成空」永远做不到（清完又被全局/yaml 顶回来，这正是此前的现象）。
        Optional<String> userDefault = userOverride(GROUP_RUNTIME, FIELD_DEFAULT_MODEL);
        if (userDefault.isPresent()) {
            addCandidate(ordered, userDefault.get());
            return List.copyOf(ordered);
        }
        addCandidate(ordered, globalSettingReader.getStrDirect(GROUP_RUNTIME, FIELD_DEFAULT_MODEL));
        addCandidate(ordered, globalSettingReader.getStrDirect(GROUP_MODELS, "defaultChatModelId"));
        addCandidate(ordered, globalSettingReader.getStrDirect(GROUP_RUNTIME, "default_chat_model"));
        return List.copyOf(ordered);
    }

    /**
     * {@inheritDoc}
     *
     * @param modelName 模型名，可为空
     * @return 任一种写法命中已启用模型时为 true
     */
    @Override
    public boolean isAvailable(String modelName) {
        for (String variant : variants(modelName)) {
            if (registryContains(variant)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 读取当前用户在指定分组的**覆盖**值。
     *
     * <p>返回 {@link Optional#empty()} 表示「该用户没设过」（应回退全局）；
     * 返回 {@code Optional.of("")} 表示「显式清空」。这个区分是「重置成空」能生效的前提。</p>
     *
     * <p>取不到用户上下文（后台线程、未登录）时返回 空，由全局默认兜底。</p>
     *
     * @param group 分组
     * @param field 字段名
     * @return 用户覆盖值；未覆盖或取不到用户时为空
     */
    private Optional<String> userOverride(String group, String field) {
        String userId;
        try {
            userId = AiUserContext.currentUserId();
        } catch (Exception e) {
            return Optional.empty();
        }
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        try {
            return userSettingService.getUserValue(userId, group, field);
        } catch (Exception e) {
            log.warn("[ModelResolver] 读取用户覆盖失败 userId={} {}/{}: {}", userId, group, field, e.getMessage());
            return Optional.empty();
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
