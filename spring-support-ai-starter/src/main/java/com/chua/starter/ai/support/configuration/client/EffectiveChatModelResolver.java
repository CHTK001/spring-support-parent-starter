package com.chua.starter.ai.support.configuration.client;

import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
 *   <li>配置中心 Bot 全局默认：{@code bot/default_model}</li>
 *   <li>配置中心「对话路由」的默认对话模型：{@code runtime/default_model}
 *       （兼容旧键 {@code models/defaultChatModelId}、{@code runtime/default_chat_model}）</li>
 * </ol>
 *
 * <p><b>刻意不做的两件事：</b></p>
 * <ul>
 *   <li><b>不写死任何模型名。</b>以前多处把兜底写成字面量 {@code agnes-2.5-flash} / {@code gpt-4o}，
 *       现场没启用该模型时整条链路必然失败，且报错指名一个用户从未配过的模型，极难排查。</li>
 *   <li><b>不自动挑模型。</b>三级配置都不可用时返回 {@code null}，由调用方给出明确、可执行的错误提示，
 *       而不是悄悄用「模型管理里第一个启用的模型」——那会让用户以为用的是自己配的那个。</li>
 * </ul>
 *
 * <p>读取一律走 {@link GlobalSettingReader#getStrDirect}（不走 Redis 缓存）：
 * 模型解析错一次就整条链路失败，缓存里残留的旧模型名会让报错与库里的配置完全对不上。</p>
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
     * 全局配置读取器
     */
    private final GlobalSettingReader globalSettingReader;

    /**
     * 模型管理仓储（用于判断模型是否已启用）
     */
    private final SysAiProviderModelMapper providerModelMapper;

    /**
     * 解析生效对话模型。
     *
     * @param explicitModel 调用方显式指定的模型，可为空
     * @return 生效模型名；三级配置均不可用时返回 {@code null}
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
        // ② 配置中心 Bot 分组全局默认
        addCandidate(ordered, globalSettingReader.getStrDirect(GROUP_BOT, "default_model"));
        // ③ 配置中心「对话路由」的默认对话模型（含两个旧键兼容）
        addCandidate(ordered, globalSettingReader.getStrDirect(GROUP_RUNTIME, "default_model"));
        addCandidate(ordered, globalSettingReader.getStrDirect(GROUP_MODELS, "defaultChatModelId"));
        addCandidate(ordered, globalSettingReader.getStrDirect(GROUP_RUNTIME, "default_chat_model"));

        LinkedHashSet<String> usable = new LinkedHashSet<>();
        for (String candidate : ordered) {
            if (isAvailable(candidate)) {
                usable.add(candidate);
            } else {
                log.warn("[ModelResolver] 候选模型 {} 未在模型管理中配置或已停用，跳过", candidate);
            }
        }
        return List.copyOf(usable);
    }

    /**
     * 判断模型是否已在「模型管理」中配置且启用。
     *
     * <p>查询异常时返回 true（不拦截），让调用阶段暴露真实错误，
     * 避免把一次数据库抖动变成「模型不存在」的误判。</p>
     *
     * @param modelName 模型名，可为空
     * @return 已配置且启用时为 true
     */
    public boolean isAvailable(String modelName) {
        String name = normalize(modelName);
        if (name == null) {
            return false;
        }
        try {
            return providerModelMapper.selectCount(
                    Wrappers.<SysAiProviderModel>lambdaQuery()
                            .eq(SysAiProviderModel::getModel, name)
                            .eq(SysAiProviderModel::getEnabled, true)) > 0;
        } catch (Exception e) {
            log.warn("[ModelResolver] 查询模型是否可用失败 model={}: {}", name, e.getMessage());
            return true;
        }
    }

    /**
     * 加入候选：去掉 {@code providerId:} 前缀、去空白、去重。
     *
     * @param set  候选集合
     * @param raw  原始取值，可为空
     */
    private static void addCandidate(LinkedHashSet<String> set, String raw) {
        String name = normalize(raw);
        if (name != null) {
            set.add(name);
        }
    }

    /**
     * 归一化模型标识：去掉 {@code providerId:model} 的数字前缀、去空白。
     *
     * @param raw 原始取值，可为空
     * @return 归一化后的模型名；无有效内容时返回 {@code null}
     */
    private static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        int colon = value.indexOf(':');
        if (colon > 0 && value.substring(0, colon).chars().allMatch(Character::isDigit)) {
            value = value.substring(colon + 1).trim();
        }
        return value.isEmpty() ? null : value;
    }
}
