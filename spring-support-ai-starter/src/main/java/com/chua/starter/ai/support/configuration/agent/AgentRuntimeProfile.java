package com.chua.starter.ai.support.configuration.agent;

import com.chua.starter.ai.support.service.SysAiGlobalSettingService;
import com.chua.starter.ai.support.manage.store.SysAiAgent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.chua.common.support.utils.CollectionUtils;

/**
 * 单个 managed Agent 的运行时配置不可变快照。
 *
 * <p>配置中心为每条 managed 记录分别保存 MCP 工具、子代理委派、
 * 默认技能、记忆体、规划、工具循环轮数与 Token 预算。每个字段都由
 * {@code SysAiAgentServiceImpl#rebuildAgent} 在构建托管实例时读取。
 * Spring 消费方经 {@code AgentManager.get(id)} 使用该实例；旧会话全局基线仍走 resolve。</p>
 *
 * <h2>三态能力开关的优先级</h2>
 * <p>{@code agent_mcp_mode} / {@code agent_sub_agent_mode} 取
 * {@link CapabilitySwitch#INHERIT} / {@code ON} / {@code OFF} 三态，判定顺序为：</p>
 * <pre>
 *   ① 平台 OFF        → 强制关闭（管理员禁用，角色无法自行打开）
 *   ② 角色/会话显式值  → 以角色为准（角色设置了 mcp.enabled=false 就是关）
 *   ③ 平台 ON         → 角色未配置时打开
 *   ④ 其余            → 关闭（与引入本档案之前完全一致）
 * </pre>
 * <p>默认值全部为 {@code INHERIT} / 0 / {@code memory=true}，因此<b>不动配置时
     * 不主动打开工具能力</b>；具体运行时行为仍需分别验证。</p>
 *
 * @param agentName         运行时 Agent 唯一名称（可为空，表示未配置）
 * @param mcp               MCP 工具基线开关
 * @param mcpServers        MCP 服务器 ID 清单；null 表示继承，空列表表示禁用
 * @param subAgent          子代理委派基线开关
 * @param defaultSkills     默认技能名清单；null 表示继承，空列表表示不注入
 * @param memory            是否启用记忆体（仅 agentscope / langchain4j 运行时生效，
 *                          opencode 自管会话记忆）
 * @param planning          是否允许规划能力
 * @param maxToolIterations 工具调用循环轮数上限，0 表示沿用运行时默认（不接管）
 * @param maxTotalTokens    Agent 累计 Token 预算上限，0 表示不设预算
 * @author CH
 * @since 4.0.0.71
 */
public record AgentRuntimeProfile(
        String agentName,
        CapabilitySwitch mcp,
        List<String> mcpServers,
        CapabilitySwitch subAgent,
        List<String> defaultSkills,
        boolean memory,
        boolean planning,
        int maxToolIterations,
        long maxTotalTokens) {

    /**
     * 未配置任何基线时的档案：与引入本档案之前的会话行为完全一致。
     */
    public static final AgentRuntimeProfile DEFAULT = new AgentRuntimeProfile(
            null, CapabilitySwitch.INHERIT, null, CapabilitySwitch.INHERIT, null,
            true, false, 0, 0L);

    /**
     * 紧凑构造器：集合字段做防御性拷贝，空值归一。
     */
    public AgentRuntimeProfile {
        agentName = agentName == null || agentName.isBlank() ? null : agentName.trim();
        mcp = mcp == null ? CapabilitySwitch.INHERIT : mcp;
        subAgent = subAgent == null ? CapabilitySwitch.INHERIT : subAgent;
        mcpServers = mcpServers == null ? null : List.copyOf(mcpServers);
        defaultSkills = defaultSkills == null ? null : List.copyOf(defaultSkills);
        if (maxToolIterations < 0 || maxTotalTokens < 0L) {
            throw new IllegalArgumentException("执行边界必须为非负整数");
        }
    }

    /**
     * 复制档案并替换 Agent 名称（供保存前预览）。
     *
     * @param newAgentName 新名称；空值表示清空
     * @return 新档案
     */
    public AgentRuntimeProfile withAgentName(String newAgentName) {
        return new AgentRuntimeProfile(newAgentName, mcp, mcpServers, subAgent, defaultSkills,
                memory, planning, maxToolIterations, maxTotalTokens);
    }

    /**
     * 三态能力开关。
     */
    public enum CapabilitySwitch {
        /**
         * 沿用角色/会话配置（缺省，行为与改造前一致）
         */
        INHERIT,
        /**
         * 角色未显式配置时打开
         */
        ON,
        /**
         * 强制关闭（平台级禁用，角色无法自行打开）
         */
        OFF;

        /**
         * 解析配置文本为开关值，无法识别时回落 {@link #INHERIT}。
         *
         * @param raw 配置文本（on / off / inherit，大小写不敏感）
         * @return 开关值
         */
        public static CapabilitySwitch parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return INHERIT;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "on", "true", "1", "yes" -> ON;
                case "off", "false", "0", "no" -> OFF;
                default -> INHERIT;
            };
        }
    }

    /**
     * 按「平台 OFF → 角色显式 → 平台 ON → 关闭」的优先级判定最终开关。
     *
     * @param platformSwitch   平台基线开关
     * @param roleConfigured   角色/会话是否显式配置了该项
     * @param roleEnabled      角色/会话显式配置的值（{@code roleConfigured=false} 时无意义）
     * @return 最终是否启用
     */
    public static boolean resolve(CapabilitySwitch platformSwitch,
                                  boolean roleConfigured, boolean roleEnabled) {
        if (platformSwitch == CapabilitySwitch.OFF) {
            return false;
        }
        if (roleConfigured) {
            return roleEnabled;
        }
        return platformSwitch == CapabilitySwitch.ON;
    }

    /**
     * 是否需要在「无工具能力」时仍然构建 Agent 运行时。
     *
     * <p>会话路径原本在「MCP / 子代理 / Plan 全关且管理员无工作间」时直连模型，
     * 避免给纯聊天凭空加一层 Agent 开销。基线档案要求了任何一项 Agent 能力时
     * 就不能再直连，否则「配置了却不生效」。</p>
     *
     * @return true 表示必须构建 Agent
     */
    public boolean requiresAgent() {
        return requiresAgent(true);
    }

    /**
     * 是否需要在「无工具能力」时仍然构建 Agent 运行时（按工具权限收敛）。
     *
     * <p>工具权限总闸关闭时（普通账号默认），MCP / 子代理 / 默认技能 / 规划
     * 都属于「工具能力」，不得由平台基线绕过；名称解析、记忆关闭与资源约束
     * 仍要求构建独立实例，本身不授予工具权限。</p>
     *
     * @param toolsAllowed 工具权限总闸是否放行
     * @return true 表示必须构建 Agent
     */
    public boolean requiresAgent(boolean toolsAllowed) {
        boolean capability = toolsAllowed
                && (mcp == CapabilitySwitch.ON
                || subAgent == CapabilitySwitch.ON
                || planning
                 || (CollectionUtils.isNotEmpty(defaultSkills)));
        return agentName != null || !memory || capability || maxToolIterations > 0 || maxTotalTokens > 0L;
    }

    /**
     * 从当前 managed 记录解析能力和预算，不读取其它 Agent 或全局九字段。
     *
     * @param agent 当前记录；null 表示没有选择 Agent
     * @return 该记录的配置快照
     */
    public static AgentRuntimeProfile fromManaged(SysAiAgent agent) {
        if (agent == null) {
            return DEFAULT;
        }
        Map<String, Object> strategy = agent.getStrategy() == null ? Map.of() : agent.getStrategy();
        Object mcpMode = strategy.containsKey("mcp_mode") ? strategy.get("mcp_mode") : strategy.get("mcp");
        return new AgentRuntimeProfile(agent.getName(),
                managedSwitch(mcpMode, "mcp_mode"), agent.getMcpIds(),
                managedSwitch(strategy.get("sub_agent_mode"), "sub_agent_mode"), agent.getSkillIds(),
                managedBoolean(strategy.get("memory"), "memory", true),
                managedBoolean(strategy.get("planning"), "planning", false),
                iterations(strategy),
                strategy.get("max_total_tokens") != null ? number(strategy, "max_total_tokens", 0L)
                        : number(strategy, "max_tokens", 0L));
    }

    /**
     * 工具轮数必须可由运行时 int 契约无损表示。
     * @param strategy 策略
     * @return 工具轮数
     */
    private static int iterations(Map<String, Object> strategy) {
        long value = number(strategy, "max_tool_iterations", 0L);
        if (value > Integer.MAX_VALUE) throw new IllegalArgumentException("max_tool_iterations 超出 int 范围");
        return (int) value;
    }

    /**
     * 读取策略文本。
     * @param strategy 策略
     * @param key 字段
     * @param fallback 缺省值
     * @return 字段文本
     */
    private static CapabilitySwitch managedSwitch(Object raw, String key) {
        if (raw == null) return CapabilitySwitch.INHERIT;
        if (!(raw instanceof String) && !(raw instanceof Boolean)) {
            throw new IllegalArgumentException(key + " 必须为 on / off / inherit");
        }
        String text = raw.toString().trim().toLowerCase(Locale.ROOT);
        if ("inherit".equals(text)) return CapabilitySwitch.INHERIT;
        if ("on".equals(text) || "true".equals(text)) return CapabilitySwitch.ON;
        if ("off".equals(text) || "false".equals(text)) return CapabilitySwitch.OFF;
        throw new IllegalArgumentException(key + " 必须为 on / off / inherit");
    }

    /**
     * 严格解析托管实例布尔策略，错误输入不得静默变为 false。
     * @param raw 原值
     * @param key 字段名
     * @param fallback 缺省值
     * @return 布尔值
     */
    private static boolean managedBoolean(Object raw, String key, boolean fallback) {
        if (raw == null) return fallback;
        if (raw instanceof Boolean value) return value;
        if (raw instanceof String text) {
            if ("true".equalsIgnoreCase(text.trim())) return true;
            if ("false".equalsIgnoreCase(text.trim())) return false;
        }
        throw new IllegalArgumentException(key + " 必须为布尔值");
    }

    /**
     * 精确读取非负整数，避免浮点数截断或 int 溢出静默取消预算。
     * @param strategy 策略
     * @param key 字段
     * @param fallback 缺省值
     * @return 非负长整数
     */
    private static long number(Map<String, Object> strategy, String key, long fallback) {
        Object raw = strategy.get(key);
        if (raw == null) return fallback;
        try {
            if (!(raw instanceof Number) && !(raw instanceof String)) throw new NumberFormatException();
            long result = new java.math.BigDecimal(raw.toString()).longValueExact();
            if (result < 0) throw new NumberFormatException();
            return result;
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException(key + " 必须为 Long 范围内的非负整数", e);
        }
    }

    /**
     * 从全局设置读取并解析档案。
     *
     * <p>读取走 {@link SysAiGlobalSettingService#getValue}（与 {@code user_tool_access}
     * 同口径，带缓存）；{@code agent} 分组不在用户可写白名单内，因此读到的就是
     * 平台基线，普通账号无法用覆盖层把它改掉。</p>
     *
     * @param settings 全局设置服务
     * @return 解析后的档案；服务不可用或字段缺失时返回 {@link #DEFAULT}
     */
    public static AgentRuntimeProfile resolve(SysAiGlobalSettingService settings) {
        if (settings == null) {
            return DEFAULT;
        }
        return new AgentRuntimeProfile(
                text(settings, AgentGlobalSettingProvider.FIELD_AGENT_NAME),
                CapabilitySwitch.parse(text(settings, AgentGlobalSettingProvider.FIELD_MCP_MODE)),
                list(settings, AgentGlobalSettingProvider.FIELD_MCP_SERVERS),
                CapabilitySwitch.parse(text(settings, AgentGlobalSettingProvider.FIELD_SUB_AGENT_MODE)),
                list(settings, AgentGlobalSettingProvider.FIELD_DEFAULT_SKILLS),
                bool(settings, AgentGlobalSettingProvider.FIELD_MEMORY, true),
                bool(settings, AgentGlobalSettingProvider.FIELD_PLANNING, false),
                integer(settings, AgentGlobalSettingProvider.FIELD_MAX_TOOL_ITERATIONS, 0),
                longInteger(settings, AgentGlobalSettingProvider.FIELD_MAX_TOTAL_TOKENS, 0L));
    }

    /**
     * 读文本字段。
     *
     * @param settings 全局设置服务
     * @param field    字段名
     * @return 文本值；未配置或读取失败返回 {@code null}
     */
    private static String text(SysAiGlobalSettingService settings, String field) {
        try {
            String value = settings.getValue(AgentGlobalSettingProvider.GROUP, field).orElse(null);
            return value == null || value.isBlank() ? null : value.trim();
        } catch (RuntimeException e) {
            // 设置表尚未初始化 / 缓存不可达时按「未配置」处理，不让整轮对话失败
            return null;
        }
    }

    /**
     * 读逗号分隔清单字段。
     *
     * @param settings 全局设置服务
     * @param field    字段名
     * @return 去空、去重后的清单（保持配置顺序）；未配置返回空列表
     */
    private static List<String> list(SysAiGlobalSettingService settings, String field) {
        String raw = text(settings, field);
        if (raw == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String part : raw.split(",")) {
            String value = part.trim();
            if (!value.isEmpty() && !result.contains(value)) {
                result.add(value);
            }
        }
        return result;
    }

    /**
     * 读布尔字段。
     *
     * @param settings     全局设置服务
     * @param field        字段名
     * @param defaultValue 默认值
     * @return 布尔值
     */
    private static boolean bool(SysAiGlobalSettingService settings, String field, boolean defaultValue) {
        String raw = text(settings, field);
        if (raw == null) {
            return defaultValue;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        if ("true".equals(lower) || "1".equals(lower) || "yes".equals(lower)) {
            return true;
        }
        if ("false".equals(lower) || "0".equals(lower) || "no".equals(lower)) {
            return false;
        }
        return defaultValue;
    }

    /**
     * 读整数字段。
     *
     * @param settings     全局设置服务
     * @param field        字段名
     * @param defaultValue 默认值
     * @return 整数值；解析失败返回默认值
     */
    private static int integer(SysAiGlobalSettingService settings, String field, int defaultValue) {
        String raw = text(settings, field);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 读长整数字段，避免超过 int 上限的 Token 预算被当作无预算。
     *
     * @param settings     全局设置服务
     * @param field        字段名
     * @param defaultValue 默认值
     * @return 长整数值；解析失败返回默认值
     */
    private static long longInteger(SysAiGlobalSettingService settings, String field, long defaultValue) {
        String raw = text(settings, field);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
