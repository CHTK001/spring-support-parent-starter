package com.chua.starter.ai.support.registry;

import com.chua.common.support.ai.chat.ChatClientSetting;
import com.chua.starter.account.channel.free.FreeChannelChatClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 多 Agent 模型共存与独立注销回归。
 * @author CH
 */
class ChatClientRegistryTest {
    /** 注册第二个模型不应删除第一个；注销大小写不敏感且保留普通模型。 */
    @Test
    void agentModelsHaveIndependentLifecycle() {
        ChatClientRegistry registry = new ChatClientRegistry();
        registry.registerAgentModel("agent-one", new FreeChannelChatClient(ChatClientSetting.builder().build()), "chat");
        registry.registerAgentModel("agent-two", new FreeChannelChatClient(ChatClientSetting.builder().build()), "chat");
        assertNotNull(registry.getByModel("agent-one"));
        assertNotNull(registry.getByModel("agent-two"));
        assertEquals(2, registry.size());
        registry.unregisterAgentModel("AGENT-ONE");
        assertFalse(registry.isAgentModelRegistered("agent-one"));
        assertNotNull(registry.getByModel("agent-two"));
        registry.register("normal", "ordinary", new FreeChannelChatClient(ChatClientSetting.builder().build()), "chat");
        registry.unregisterAgentModel("ordinary");
        assertNotNull(registry.getByModel("ordinary"));
        registry.closeAll();
        assertEquals(0, registry.getRegisteredAgentModels().size());
    }
}
