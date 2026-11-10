package com.chua.starter.ai.support.configuration.runtime;

import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Agent 引擎默认值迁移：把历史版本种下的「系统默认 agentscope」升级为当前默认 opencode-server。
 *
 * <p>{@link com.chua.starter.ai.support.configuration.client.GlobalSettingInitializer} 只在
 * 分组为空时写入默认值，已有分组只补缺失字段、绝不覆盖取值——这是「不吞管理员配置」的正确行为。
 * 但 {@code agent_impl} 在旧版本里被种子成 {@code agentscope} 后，升级到本版本时这一行
 * 依然存在，于是「默认使用 opencode」对任何存量部署都不生效，只能在设置页手工再选一次。</p>
 *
 * <p>迁移条件必须同时成立，避免覆盖真实的管理员选择：</p>
 * <ol>
 *     <li>取值仍是旧版本默认值 {@code agentscope}；</li>
 *     <li>该行 {@code create_name} 为空，即 {@code systemDefault=true}——从来没有人配过它。
 *     管理员在设置页改过的行会带上操作人，属于 {@code GLOBAL}，一律不动。</li>
 * </ol>
 *
 * <p>迁移只发生在启动期一次：写入后该行落到管理员全局层语义（{@code GLOBAL}），后续启动不再命中。
 * 迁移后管理员把它改回 {@code agentscope} 也不会被再次改写。</p>
 *
 * @since 4.0.0.51
 * @author CH
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentEngineDefaultMigrator {

    /**
     * 旧版本注册进 {@code sys_ai_global_setting} 的 Agent 引擎默认值。
     */
    static final String LEGACY_DEFAULT_AGENT_IMPL = "agentscope";

    /**
     * 全局设置服务
     */
    private final SysAiGlobalSettingService globalSettingService;

    /**
     * 启动就绪后执行一次默认值迁移；失败只记日志，不阻断应用启动。
     *
     * <p>挂在 {@link ApplicationReadyEvent} 而不是 {@code ApplicationRunner}：
     * {@link com.chua.starter.ai.support.configuration.client.GlobalSettingInitializer} 是
     * runner，只有 runner 全部跑完才发布就绪事件，因此这里读到的 {@code runtime} 分组一定已经
     * 完成注册。顺序再排在 {@link AgentProviderFactory#warmUpOpencode()} 之前，避免预热
     * 读到迁移前的旧默认值。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE - 100)
    public void onApplicationReady() {
        try {
            if (migrate()) {
                log.info("[AgentEngineDefaultMigrator] Agent 引擎默认值已从 {} 迁移为 {}",
                        LEGACY_DEFAULT_AGENT_IMPL, RuntimeGlobalSettingProvider.DEFAULT_AGENT_IMPL);
            }
        } catch (Exception e) {
            log.warn("[AgentEngineDefaultMigrator] Agent 引擎默认值迁移失败（不影响启动）: {}", e.getMessage());
        }
    }

    /**
     * 执行一次迁移判定与写入。
     *
     * @return true 表示本次真正改写了取值
     */
    boolean migrate() {
        String current = readSystemDefaultValue();
        if (current == null || !LEGACY_DEFAULT_AGENT_IMPL.equalsIgnoreCase(current.trim())) {
            return false;
        }
        globalSettingService.upsertValue(RuntimeGlobalSettingProvider.GROUP,
                RuntimeGlobalSettingProvider.FIELD_AGENT_IMPL,
                RuntimeGlobalSettingProvider.DEFAULT_AGENT_IMPL,
                RuntimeGlobalSettingProvider.DEFAULT_AGENT_IMPL_DESCRIPTION);
        return true;
    }

    /**
     * 读取 {@code agent_impl} 的系统默认值：不存在或已被人工配置过时返回 {@code null}。
     *
     * @return 仍属系统默认层时的当前取值
     */
    private String readSystemDefaultValue() {
        for (Map<String, Object> item : globalSettingService.list(RuntimeGlobalSettingProvider.GROUP)) {
            if (!RuntimeGlobalSettingProvider.FIELD_AGENT_IMPL.equals(String.valueOf(item.get("field")))) {
                continue;
            }
            if (!Boolean.TRUE.equals(item.get("systemDefault"))) {
                // 已被管理员改过：这是真实选择，不能再按「旧默认值」对待。
                return null;
            }
            Object value = item.get("value");
            return value == null ? null : String.valueOf(value);
        }
        return null;
    }
}
