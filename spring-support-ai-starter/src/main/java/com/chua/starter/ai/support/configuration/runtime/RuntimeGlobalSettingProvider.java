package com.chua.starter.ai.support.configuration.runtime;

import com.chua.common.support.ai.rag.GlobalSettingProvider;
import com.chua.common.support.spi.annotations.Spi;

import java.util.List;

/**
 * 全局对话路由（runtime 分组）全局设置提供者 SPI。
 *
 * <p>定义 {@code runtime} 分组的全部配置项，由 {@link com.chua.starter.ai.support.configuration.client.GlobalSettingInitializer}
 * 启动时自动注册到 {@code sys_ai_global_setting} 表（仅首次）。LLM 供应商地址、API Key、默认模型等
 * 全部通过表管理，由 {@link com.chua.starter.ai.support.chat.ChatClientSettingsResolver} 读取并构建全局
 * {@code ChatClient}。</p>
 *
 * <p>该分组配置数据完全来自数据库表，不依赖任何环境变量。修改表后可通过
 * {@code GlobalChatClientManager.rebuild()} 热重建全局客户端，实时生效。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
@Spi("runtime")
public class RuntimeGlobalSettingProvider implements GlobalSettingProvider {

    /**
     * 配置分组标识
     */
    public static final String GROUP = "runtime";

    /**
     * Agent 引擎实现字段标识
     */
    public static final String FIELD_AGENT_IMPL = "agent_impl";

    /**
     * Agent 引擎默认实现
     */
    public static final String DEFAULT_AGENT_IMPL = "agentscope";

    /**
     * 分组显示名称
     */
    private static final String GROUP_NAME = "对话路由";

    /**
     * 配置分组标识。
     *
     * @return runtime
     */
    @Override
    public String group() {
        return GROUP;
    }

    /**
     * 配置分组显示名称。
     *
     * @return 对话路由
     */
    @Override
    public String groupName() {
        return GROUP_NAME;
    }

    /**
     * 对话路由分组全部配置项。
     *
     * <p>核心链路（必填）：provider / factory / provider_api_key / default_model。
     * 辅助配置（选填）：超时、温度、最大令牌、输入优化、上下文压缩等。</p>
     *
     * @return 配置项列表
     */
    @Override
    public List<SettingItem> getItems() {
        return List.of(
                new SettingItem("enabled", "true", "BOOLEAN", "是否启用全局对话路由（false 时 ChatClient 禁用）", 1),
                new SettingItem("provider", "", "STRING", "LLM 供应商（openai / zhipu / deepseek / qwen 等）", 2),
                new SettingItem("factory", "default", "STRING", "LLM 工厂实现（default 使用供应商默认实现）", 3),
                new SettingItem("provider_base_url", "", "STRING", "LLM 供应商接口地址（如 https://api.openai.com/v1）", 4),
                new SettingItem("provider_api_key", "", "STRING", "LLM API Key", 5),
                new SettingItem("provider_app_key", "", "STRING", "LLM App Key（部分供应商使用）", 6),
                new SettingItem("provider_app_secret", "", "STRING", "LLM App Secret（部分供应商使用）", 7),
                new SettingItem("default_model", "", "STRING", "默认对话模型（留空=自动使用模型管理中第一个启用的模型）", 8),
            new SettingItem("default_image_feature_model", "", "STRING", "默认图像特征模型（ONNX 模型标识；以图搜图未单独配置时兜底）", 30),
            new SettingItem("default_face_model", "", "STRING", "默认人脸模型（ONNX 模型标识；人脸检测/特征未单独配置时兜底）", 31),
                new SettingItem("compression_model", "", "STRING", "上下文压缩模型（空=使用默认模型）", 9),
                new SettingItem("system_prompt", "", "STRING", "默认系统提示词", 10),
                new SettingItem("temperature", "0.7", "NUMBER", "默认生成温度", 11),
                new SettingItem("max_tokens", "4096", "NUMBER", "默认最大输出 tokens", 12),
                new SettingItem("timeout_millis", "30000", "NUMBER", "请求超时时间（毫秒）", 13),
                new SettingItem("input_optimization_enabled", "false", "BOOLEAN", "是否启用对话输入自动优化", 14),
                new SettingItem("context_compression_enabled", "false", "BOOLEAN", "是否启用上下文压缩", 15),
                new SettingItem("context_compression_threshold", "12", "NUMBER", "上下文压缩消息数阈值", 16),
                new SettingItem("context_compression_retain_messages", "6", "NUMBER", "压缩后保留最近消息数", 17),
                new SettingItem("protocol_server_enabled", "false", "BOOLEAN", "是否启用聚合协议服务（独立端口，提供 OpenAI/Claude/Gemini 三协议端点）", 18),
                new SettingItem("protocol_server_port", "8090", "NUMBER", "聚合协议服务端口（OpenAI/Claude/Gemini 三协议统一入口）", 19),
                new SettingItem("protocol_server_host", "0.0.0.0", "STRING", "聚合协议服务绑定地址", 20),
                new SettingItem("extra_headers", "", "JSON", "自定义请求头（JSON 格式，如 {\"X-Custom\": \"value\"}）", 21),
                // ==================== Agent 工具权限 ====================
                // 管理员专属：普通账号不得看到、不得修改。默认 false 表示普通账号的 agent 不启用任何工具。
                // 管理员自己的工具能力不受本开关影响。
                // 服务端三处必须配合：定义出口按角色过滤、取值出口剔除、写入出口拒绝（否则普通账号
                // 可以走 /v2/settings/{group} 写自己的覆盖层把权限给自己打开）。
                new SettingItem("user_tool_access", "false", "BOOLEAN",
                        "是否允许普通账号调用工具（关闭时普通账号的 agent 不启用任何工具；管理员不受影响）",
                        22, true),
                new SettingItem(FIELD_AGENT_IMPL, DEFAULT_AGENT_IMPL, "STRING",
                        "Agent 引擎实现（agentscope / opencode）", 23, true)
        );
    }
}
