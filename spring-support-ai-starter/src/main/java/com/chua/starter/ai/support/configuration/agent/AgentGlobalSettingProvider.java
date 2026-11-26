// [P3C A20 豁免] A20/A30/A31: 含界面/描述字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.configuration.agent;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
 * Agent 运行时基线全局设置提供者 SPI（分组：agent）。
 *
 * <p>会话底层就是 Agent，所以本分组不是「选一个 Agent 名称」的占位页，而是
 * <b>所有会话 Agent 的运行时基线</b>：MCP 工具、子代理委派、默认技能、记忆体、
 * 规划、工具循环轮数与 Token 预算。每一项都由
 * {@code AiChatFacadeServiceImpl#maybeWrapAgent} 构建会话 Agent 时真实读取
 * （见 {@link AgentRuntimeProfile}），改完下一轮对话即生效。</p>
 *
 * <p>默认值刻意全部取「不接管」语义（{@code inherit} / 0 / {@code memory=true}）：
 * 不动配置时行为与引入本分组之前完全一致，避免升级后所有会话突然开始调工具。</p>
 *
 * <p>该分组为管理员专属：全部 {@link SettingItem} 标记 {@code adminOnly=true}，
 * 普通用户读不到这些字段；同时 {@code agent} 不在 SettingsController 的
 * 用户可写白名单内，普通用户保存会被服务端直接拒绝。</p>
 *
 * @author CH
 * @since 4.0.0.70
 */
@Spi("agent")
public class AgentGlobalSettingProvider implements GlobalSettingProvider {

    /**
     * 配置分组标识
     */
    public static final String GROUP = "agent";

    /**
     * 分组显示名称
     */
    public static final String GROUP_NAME = "Agent 配置";

    /**
     * Agent 唯一名称：后端按它从 AgentRegistry 解析运行时 Agent
     */
    public static final String FIELD_AGENT_NAME = "agent_name";

    /**
     * MCP 工具基线开关（inherit / on / off）
     */
    public static final String FIELD_MCP_MODE = "agent_mcp_mode";

    /**
     * 基线 MCP 服务器 ID 清单（逗号分隔；空=全部启用服务器）
     */
    public static final String FIELD_MCP_SERVERS = "agent_mcp_servers";

    /**
     * 子代理委派基线开关（inherit / on / off）
     */
    public static final String FIELD_SUB_AGENT_MODE = "agent_sub_agent_mode";

    /**
     * 基线默认技能名清单（逗号分隔；角色未选技能时注入）
     */
    public static final String FIELD_DEFAULT_SKILLS = "agent_default_skills";

    /**
     * 是否启用记忆体
     */
    public static final String FIELD_MEMORY = "agent_memory";

    /**
     * 是否允许规划能力
     */
    public static final String FIELD_PLANNING = "agent_planning";

    /**
     * 工具调用循环轮数上限（0=沿用运行时默认）
     */
    public static final String FIELD_MAX_TOOL_ITERATIONS = "agent_max_tool_iterations";

    /**
     * Agent 累计 Token 预算上限（0=不设预算）
     */
    public static final String FIELD_MAX_TOTAL_TOKENS = "agent_max_total_tokens";

    @Override
    public String group() {
        return GROUP;
    }

    @Override
    public String groupName() {
        return GROUP_NAME;
    }

    @Override
    public List<SettingItem> getItems() {
        return List.of(
                new SettingItem(FIELD_AGENT_NAME, "", "STRING",
                        "Agent 唯一名称：后端按该名称从 AgentRegistry 解析运行时 Agent，须与已注册 Agent 的名称一致",
                        1, true),
                new SettingItem(FIELD_MCP_MODE, "inherit", "STRING",
                        "MCP 工具基线开关：inherit=沿用角色/会话配置，on=角色未配置时打开，"
                                + "off=强制关闭（角色无法自行打开）", 2, true),
                new SettingItem(FIELD_MCP_SERVERS, "", "STRING",
                        "基线 MCP 服务器 ID 清单（逗号分隔）：留空表示连接全部已启用服务器；"
                                + "角色/会话显式选择优先于本项", 3, true),
                new SettingItem(FIELD_SUB_AGENT_MODE, "inherit", "STRING",
                        "子代理委派基线开关：inherit=沿用角色配置，on=角色未配置时打开，off=强制关闭", 4, true),
                new SettingItem(FIELD_DEFAULT_SKILLS, "", "STRING",
                        "基线默认技能清单（逗号分隔的技能名）：角色未选技能时注入这些技能；"
                                + "角色已选技能时以角色为准", 5, true),
                new SettingItem(FIELD_MEMORY, "true", "BOOLEAN",
                        "是否启用记忆体（仅 agentscope / langchain4j 运行时生效；"
                                + "opencode 在独立进程内自管会话记忆）", 6, true),
                new SettingItem(FIELD_PLANNING, "false", "BOOLEAN",
                        "是否允许规划能力：开启后会话可进入只读规划模式并请求批准后执行", 7, true),
                new SettingItem(FIELD_MAX_TOOL_ITERATIONS, "0", "NUMBER",
                        "工具调用循环轮数上限：0=沿用运行时默认（一般 5 轮），"
                                + "数值越大单轮可做的工具调用越多、耗时与费用越高", 8, true),
                new SettingItem(FIELD_MAX_TOTAL_TOKENS, "0", "NUMBER",
                        "Agent 累计 Token 预算上限（输入+输出）：0=不设预算；"
                                + "超限即熔断收尾，会话目标自带的预算优先于本项", 9, true)
        );
    }
}
