// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.chua.common.support.ai.chat.ChatClient;
import com.chua.common.support.ai.chat.aggregate.RouterGroupSetting;
import com.chua.common.support.ai.chat.aggregate.RouterGroupSetting.BusyPolicy;
import com.chua.common.support.ai.chat.aggregate.RouterGroupSetting.ContextPolicy;
import com.chua.common.support.ai.chat.aggregate.RouterGroupSetting.Member;
import com.chua.common.support.ai.chat.aggregate.RouterGroupSetting.RateLimitPolicy;
import com.chua.common.support.ai.chat.aggregate.RouterGroupSetting.SlowPolicy;
import com.chua.common.support.ai.chat.aggregate.RouterGroupSetting.Strategy;
import com.chua.starter.ai.support.manage.mapper.SysAiProviderModelMapper;
import com.chua.starter.ai.support.manage.store.SysAiProvider;
import com.chua.starter.ai.support.manage.store.SysAiProviderModel;
import com.chua.starter.ai.support.util.ProviderRouteTargets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 路由组业务：成员表读写、顺序调整、成员模型删除级联、配置解析。
 *
 * <p><b>成员表存哪</b>：存放在路由组供应商行的 {@code extra.router_group} 里。路由组不是
 * 真实上游，它没有自己的模型行 —— 它的「模型」就是模型库里已有的行，把成员再抄一份进
 * {@code sys_ai_provider_model} 会造成「同名两份、删一处留一处」的脏数据。成员引用一律用
 * {@code providerId:model} 复合主键指向模型库里的真实行。</p>
 *
 * <p><b>成员被删了怎么办</b>：模型库里删掉某一行后，本类会把它从所有路由组成员表里摘掉
 * （{@link #pruneMemberDeletions}）；读成员时也会再做一次存在性过滤，避免「引用已删除的
 * 模型」这种悬空成员把整组带崩。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RouterGroupService {

    /**
     * 供应商 extra 中承载路由组配置的键
     */
    public static final String EXTRA_KEY = "router_group";

    /**
     * 模型行 extra 中承载「每模型 effort」的键（写在成员模型行自己的 extra 上）
     */
    public static final String EFFORT_KEY = "router_effort";

    /**
     * 候选成员检索上限（防止一次把整库拉进下拉框）
     */
    private static final int MEMBER_SEARCH_LIMIT = 300;

    /**
     * 供应商模型 Mapper
     */
    private final SysAiProviderModelMapper providerModelMapper;

    /**
     * 供应商 Mapper（成员展示需要供应商名称与平台）
     */
    private final com.chua.starter.ai.support.manage.mapper.SysAiProviderMapper providerMapper;

    /**
     * 是否为路由组供应商
     *
     * @param provider 供应商
     * @return true 表示路由组
     */
    public boolean isRouterGroup(SysAiProvider provider) {
        return provider != null && ProviderRouteTargets.isRouterGroup(provider.getPlatform());
    }

    /**
     * 读取供应商行上的路由组配置。
     *
     * @param provider 路由组供应商
     * @return 配置；未配置时返回默认配置
     */
    public RouterGroupSetting readSetting(SysAiProvider provider) {
        RouterGroupSetting setting = new RouterGroupSetting();
        if (provider == null || provider.getExtra() == null) {
            return setting;
        }
        Object raw = provider.getExtra().get(EXTRA_KEY);
        if (!(raw instanceof Map<?, ?> map)) {
            return setting;
        }
        setting.setStrategy(Strategy.of(str(map, "strategy")));
        setting.setOnRateLimit(RateLimitPolicy.of(str(map, "on_rate_limit")));
        setting.setOnSlow(SlowPolicy.of(str(map, "on_slow")));
        setting.setOnBusy(BusyPolicy.of(str(map, "on_busy")));
        setting.setOnContext(ContextPolicy.of(str(map, "on_context")));
        setting.setContextWindow(intOrNull(map, "context_window"));
        setting.setSlowMillis(longOrNull(map, "slow_ms"));
        setting.setBusyMillis(longOrNull(map, "busy_ms"));
        Integer longChars = intOrNull(map, "long_prompt_chars");
        if (longChars != null && longChars > 0) {
            setting.setLongPromptChars(longChars);
        }
        setting.setMembers(readMembers(map, provider.getProviderId()));
        return setting;
    }

    /**
     * 解析成员列表（跳过指向已删除模型行的悬空成员）。
     *
     * @param map        配置 Map
     * @param groupId    路由组 ID
     * @return 成员列表
     */
    private List<Member> readMembers(Map<?, ?> map, String groupId) {
        List<Member> result = new ArrayList<>();
        Object raw = map.get("members");
        if (!(raw instanceof List<?> list)) {
            return result;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                continue;
            }
            String providerId = str(m, "provider_id");
            String model = str(m, "model");
            if (providerId == null || model == null) {
                continue;
            }
            // 自己不能引用自己，否则会递归构建
            if (groupId != null && groupId.equals(providerId)) {
                log.warn("[RouterGroup] 忽略自引用成员 {}:{}", providerId, model);
                continue;
            }
            Member member = new Member(providerId, model,
                    Boolean.TRUE.equals(boolOrNull(m, "enabled")),
                    intOrDefault(m, "weight", 1),
                    str(m, "effort"),
                    boolOrNull(m, "free"),
                    longOrNull(m, "expire_at"));
            // 成员模型已被删除的引用不再出现在组里
            if (!memberExists(providerId, model)) {
                log.info("[RouterGroup] 成员模型已不存在，跳过 {}:{}", providerId, model);
                continue;
            }
            result.add(member);
        }
        return result;
    }

    /**
     * 成员是否仍然存在于模型库中且启用。
     *
     * @param providerId 成员供应商
     * @param model      成员模型
     * @return true 表示可用
     */
    public boolean memberExists(String providerId, String model) {
        try {
            SysAiProviderModel row = providerModelMapper.selectOne(
                    Wrappers.<SysAiProviderModel>lambdaQuery()
                            .eq(SysAiProviderModel::getProviderId, providerId)
                            .eq(SysAiProviderModel::getModel, model));
            return row != null && Boolean.TRUE.equals(row.getEnabled());
        } catch (Exception e) {
            log.debug("[RouterGroup] 校验成员失败 {}:{} {}", providerId, model, e.getMessage());
            return false;
        }
    }

    /**
     * 成员模型行 → 组成员（effort 从模型行的 extra 读取）。
     *
     * @param row 模型行
     * @return 成员
     */
    public Member toMember(SysAiProviderModel row) {
        return new Member(row.getProviderId(), row.getModel(), Boolean.TRUE.equals(row.getEnabled()), 1,
                readEffort(row), null, null);
    }

    /**
     * 读取模型行上配置的 effort。
     *
     * @param row 模型行
     * @return effort；未配置返回 null
     */
    public String readEffort(SysAiProviderModel row) {
        if (row == null || row.getMetrics() == null) {
            return null;
        }
        Object raw = row.getMetrics().get(EFFORT_KEY);
        return raw == null || raw.toString().isBlank() ? null : raw.toString();
    }

    /**
     * 写出路由组配置到供应商 extra（返回新的 extra Map，调用方负责落库）。
     *
     * @param provider 路由组供应商
     * @param setting  配置
     * @return 新的 extra
     */
    public Map<String, Object> writeSetting(SysAiProvider provider, RouterGroupSetting setting) {
        Map<String, Object> extra = provider.getExtra() == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(provider.getExtra());
        Map<String, Object> group = new LinkedHashMap<>();
        group.put("strategy", setting.getStrategy() == null ? Strategy.SMART.code() : setting.getStrategy().code());
        group.put("on_rate_limit", setting.getOnRateLimit() == null
                ? RateLimitPolicy.DEFAULT.code() : setting.getOnRateLimit().code());
        group.put("on_slow", setting.getOnSlow() == null ? SlowPolicy.DEFAULT.code() : setting.getOnSlow().code());
        group.put("on_busy", setting.getOnBusy() == null ? BusyPolicy.DEFAULT.code() : setting.getOnBusy().code());
        group.put("on_context", setting.getOnContext() == null
                ? ContextPolicy.MAX.code() : setting.getOnContext().code());
        group.put("context_window", setting.getContextWindow());
        group.put("slow_ms", setting.getSlowMillis());
        group.put("busy_ms", setting.getBusyMillis());
        group.put("long_prompt_chars", setting.getLongPromptChars());
        List<Map<String, Object>> members = new ArrayList<>();
        for (Member m : setting.getMembers() == null ? List.<Member>of() : setting.getMembers()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("provider_id", m.providerId());
            item.put("model", m.model());
            item.put("enabled", m.enabled());
            item.put("weight", m.weight());
            item.put("effort", m.effort());
            item.put("free", m.free());
            item.put("expire_at", m.expireAt());
            members.add(item);
        }
        group.put("members", members);
        extra.put(EXTRA_KEY, group);
        return extra;
    }

    /**
     * 成员模型被删除时，从所有路由组的成员表里摘掉该成员。
     *
     * <p>返回的是「待落库的新 extra」，本类不直接写库：调用方（模型删除接口）在同一个
     * 事务里把新 extra 写回供应商行，保证「删模型」与「清成员」一起成功或一起回滚。</p>
     *
     * @param providers 全部供应商（调用方已查询）
     * @param providerId 被删模型所属供应商
     * @param model      被删模型
     * @return 被裁剪的路由组；无影响时为空列表
     */
    public List<PrunedGroup> pruneMemberDeletions(List<SysAiProvider> providers, String providerId, String model) {
        List<PrunedGroup> result = new ArrayList<>();
        if (providerId == null || model == null || providers == null) {
            return result;
        }
        for (SysAiProvider provider : providers) {
            if (!isRouterGroup(provider) || !references(provider.getExtra(), providerId, model)) {
                continue;
            }
            RouterGroupSetting setting = readSetting(provider);
            List<Member> kept = new ArrayList<>();
            int removed = 0;
            for (Member m : setting.getMembers()) {
                if (providerId.equals(m.providerId()) && model.equals(m.model())) {
                    removed++;
                    continue;
                }
                kept.add(m);
            }
            if (removed == 0) {
                continue;
            }
            setting.setMembers(kept);
            result.add(new PrunedGroup(provider.getProviderId(), writeSetting(provider, setting), removed));
            log.info("[RouterGroup] 成员被删除，已从路由组摘除 provider={} member={}:{} 剩余={}",
                    provider.getProviderId(), providerId, model, kept.size());
        }
        return result;
    }

    /**
     * 被裁剪的路由组：新 extra + 被摘掉的成员数。
     *
     * @param providerId 路由组 ID
     * @param extra      新的 extra
     * @param removed    摘掉的成员数
     */
    public record PrunedGroup(String providerId, Map<String, Object> extra, int removed) {
    }

    /**
     * 判断模型行是否被某个路由组引用（用于删除前提示）。
     *
     * @param extra      路由组 extra
     * @param providerId 成员供应商
     * @param model      成员模型
     * @return true 表示引用
     */
    public static boolean references(Map<String, Object> extra, String providerId, String model) {
        if (extra == null) {
            return false;
        }
        Object raw = extra.get(EXTRA_KEY);
        if (!(raw instanceof Map<?, ?> group) || !(group.get("members") instanceof List<?> members)) {
            return false;
        }
        for (Object item : members) {
            if (item instanceof Map<?, ?> m
                    && providerId != null && providerId.equals(str(m, "provider_id"))
                    && model != null && model.equals(str(m, "model"))) {
                return true;
            }
        }
        return false;
    }

    // ======================== 小工具 ========================

    /**
     * 把配置转成前端契约（策略等一律用短代码，面板按代码选中）。
     *
     * @param setting 配置
     * @return 响应 Map
     */
    public Map<String, Object> toResponse(RouterGroupSetting setting) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("strategy", setting.getStrategy() == null ? Strategy.SMART.code() : setting.getStrategy().code());
        // 一律下发枚举的短代码（30s / 2m / move_to_end）：前端下拉按短代码选中，
        // 用 name() 会得到 THIRTY_SECONDS / MOVE_TO_END，回显时选不中。
        map.put("on_rate_limit", setting.getOnRateLimit() == null
                ? RateLimitPolicy.DEFAULT.code() : setting.getOnRateLimit().code());
        map.put("on_slow", setting.getOnSlow() == null
                ? SlowPolicy.DEFAULT.code() : setting.getOnSlow().code());
        map.put("on_busy", setting.getOnBusy() == null
                ? BusyPolicy.DEFAULT.code() : setting.getOnBusy().code());
        map.put("on_context", setting.getOnContext() == null
                ? ContextPolicy.MAX.code() : setting.getOnContext().code());
        map.put("context_window", setting.getContextWindow());
        map.put("slow_ms", setting.getSlowMillis());
        map.put("busy_ms", setting.getBusyMillis());
        map.put("long_prompt_chars", setting.getLongPromptChars());
        return map;
    }

    /**
     * 成员 + 模型库行的展示信息（名称、上下文、价格、effort），供组页面直接渲染。
     *
     * @param setting 配置
     * @return 成员展示列表
     */
    public List<Map<String, Object>> describeMembers(RouterGroupSetting setting) {
        List<Map<String, Object>> items = new ArrayList<>();
        if (setting == null || setting.getMembers() == null) {
            return items;
        }
        for (Member m : setting.getMembers()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("provider_id", m.providerId());
            item.put("model", m.model());
            item.put("label", m.label());
            item.put("enabled", m.enabled());
            item.put("weight", m.weight());
            item.put("effort", m.effort());
            item.put("free", m.free());
            item.put("expire_at", m.expireAt());
            SysAiProvider owner = providerMapper.selectById(m.providerId());
            item.put("provider_name", owner == null ? m.providerId() : owner.getName());
            item.put("platform", owner == null ? null : owner.getPlatform());
            SysAiProviderModel row = providerModelMapper.selectOne(
                    Wrappers.<SysAiProviderModel>lambdaQuery()
                            .eq(SysAiProviderModel::getProviderId, m.providerId())
                            .eq(SysAiProviderModel::getModel, m.model()));
            item.put("exists", row != null);
            if (row != null) {
                item.put("alias", row.getAlias());
                item.put("context_window", row.getContextWindow());
                item.put("capabilities", row.getCapabilities());
                item.put("metrics", row.getMetrics());
            }
            items.add(item);
        }
        return items;
    }

    /**
     * 把保存请求合并进配置：请求里出现的键才覆盖，未出现保持原值。
     *
     * @param provider 路由组供应商
     * @param body     请求体
     * @param members  已校验的成员表（null 表示不改成员）
     * @return 合并后的配置
     */
    public RouterGroupSetting applyRequest(SysAiProvider provider, Map<String, Object> body, List<Member> members) {
        RouterGroupSetting setting = readSetting(provider);
        if (body != null) {
            if (body.containsKey("strategy")) {
                setting.setStrategy(Strategy.of(str(body, "strategy")));
            }
            if (body.containsKey("on_rate_limit")) {
                setting.setOnRateLimit(RateLimitPolicy.of(str(body, "on_rate_limit")));
            }
            if (body.containsKey("on_slow")) {
                setting.setOnSlow(SlowPolicy.of(str(body, "on_slow")));
            }
            if (body.containsKey("on_busy")) {
                setting.setOnBusy(BusyPolicy.of(str(body, "on_busy")));
            }
            if (body.containsKey("on_context")) {
                setting.setOnContext(ContextPolicy.of(str(body, "on_context")));
                setting.setContextWindow(intOrNull(body, "context_window"));
            }
            if (body.containsKey("slow_ms")) {
                setting.setSlowMillis(longOrNull(body, "slow_ms"));
            }
            if (body.containsKey("busy_ms")) {
                setting.setBusyMillis(longOrNull(body, "busy_ms"));
            }
            if (body.containsKey("long_prompt_chars")) {
                Integer chars = intOrNull(body, "long_prompt_chars");
                if (chars != null && chars > 0) {
                    setting.setLongPromptChars(chars);
                }
            }
        }
        if (members != null) {
            setting.setMembers(members);
        }
        return setting;
    }

    /**
     * 按模型名检索成员所在模型行（供前端在模型库里搜索可选成员）。
     *
     * @param keyword 关键字（为空返回前 {@value #MEMBER_SEARCH_LIMIT} 条）
     * @return 模型行
     */
    public List<SysAiProviderModel> searchMemberCandidates(String keyword) {
        try {
            LambdaQueryWrapper<SysAiProviderModel> qw = Wrappers.<SysAiProviderModel>lambdaQuery()
                    .eq(SysAiProviderModel::getEnabled, true)
                    .orderByAsc(SysAiProviderModel::getModel)
                    .last("LIMIT " + MEMBER_SEARCH_LIMIT);
            if (keyword != null && !keyword.isBlank()) {
                qw.like(SysAiProviderModel::getModel, keyword.trim());
            }
            return providerModelMapper.selectList(qw);
        } catch (Exception e) {
            log.warn("[RouterGroup] 检索候选成员失败 {}: {}", keyword, e.getMessage());
            return List.of();
        }
    }

    /**
     * 读取 Map 中的字符串
     *
     * @param map 配置
     * @param key 键
     * @return 字符串；缺失返回 null
     */
    private static String str(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 读取布尔
     *
     * @param map          配置
     * @param key          键
     * @param defaultValue 缺省
     * @return 布尔值
     */
    @SuppressWarnings("unused")
    private static boolean boolOrDefault(Map<?, ?> map, String key, boolean defaultValue) {
        Boolean value = boolOrNull(map, key);
        return value == null ? defaultValue : value;
    }

    /**
     * 读取布尔（可空）
     *
     * @param map 配置
     * @param key 键
     * @return 布尔值；缺失返回 null
     */
    private static Boolean boolOrNull(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(value.toString());
    }

    /**
     * 读取整数
     *
     * @param map          配置
     * @param key          键
     * @param defaultValue 缺省
     * @return 整数
     */
    private static int intOrDefault(Map<?, ?> map, String key, int defaultValue) {
        Integer value = intOrNull(map, key);
        return value == null ? defaultValue : value;
    }

    /**
     * 读取整数（可空）
     *
     * @param map 配置
     * @param key 键
     * @return 整数；缺失返回 null
     */
    private static Integer intOrNull(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 读取长整数（可空）
     *
     * @param map 配置
     * @param key 键
     * @return 长整数；缺失返回 null
     */
    private static Long longOrNull(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 取供应商客户端构建所需的真实模型名。
     *
     * @param member 成员
     * @return {@code providerId:model}
     */
    public static String qualified(Member member) {
        return member.providerId() + ":" + member.model();
    }

    /**
     * 供 {@link ModelClientResolver} 构建成员客户端时使用的入口（避免循环依赖）。
     *
     * @param member  成员
     * @param factory 解析器提供的构建函数
     * @return 客户端
     */
    public ChatClient buildMember(Member member, java.util.function.Function<Member, ChatClient> factory) {
        return factory.apply(member);
    }
}
