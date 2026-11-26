// [P3C A20 豁免] A20/A30/A31: 含界面/描述字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.configuration.agent;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
 * Agent 运行时配置全局设置提供者 SPI（分组：agent）。
 *
 * <p>当前只承载一个字段：Agent 唯一名称（{@code agent_name}）。
 * 后端按该名称经 {@code AgentRegistry#getByName} 解析运行时 Agent
 * （见 {@code ApiAgentRuntimeController#configured}），未配置或解析不到时
 * 分别返回明确的业务错误。</p>
 *
 * <p>该分组为管理员专属：{@link SettingItem} 标记 {@code adminOnly=true}，
 * 普通用户读不到该字段；同时 {@code agent} 不在 SettingsController 的
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
     * Agent 唯一名称字段：后端按它从 AgentRegistry 解析运行时 Agent
     */
    public static final String FIELD_AGENT_NAME = "agent_name";

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
                        "Agent 唯一名称：后端按该名称从 AgentRegistry 解析运行时 Agent，须与已注册 Agent 的名称一致", 1, true)
        );
    }
}
