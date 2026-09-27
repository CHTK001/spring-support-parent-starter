package com.chua.starter.ai.support.model;

import com.chua.common.support.ai.chat.ChatTool;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AI 对话请求模型。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Data
public class AiChatRequest {

    /**
     * 模型名称
     */
    private String model;

    /**
     * 用户输入
     */
    private String prompt;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * AI 角色编码（人设，如 Java-dev/ui-Designer），由后端解析为系统提示词
     */
    private String roleCode;

    /**
     * 采样温度
     */
    private Double temperature;

    /**
     * 最大生成 令牌
     */
    private Integer maxTokens;

    /**
     * top_p 采样参数
     */
    private Double topP;

    /**
     * 频率惩罚
     */
    private Double frequencyPenalty;

    /**
     * 历史消息列表
     */
    private List<Map<String, Object>> messages;

    /**
     * 工具（函数调用）定义列表：透传给对话客户端，使模型可发起 tool_calls。
     * 由对外协议适配层（如 Anthropic /v1/messages 的 tools）转换而来。
     */
    private List<ChatTool> tools;

    /**
     * 工具选择策略：auto / none / required / 指定工具名称；为空按服务商默认。
     */
    private String toolChoice;

    /**
     * 图片地址列表
     */
    private List<String> imageUrls;

    /**
     * 扩展参数
     */
    private Map<String, Object> parameters;

    /**
     * 是否流式输出
     */
    private Boolean stream;

    /**
     * 厂商工厂类型
     */
    private String factory;

    /**
     * 厂商提供者
     */
    private String provider;

    /**
     * 思考模式开关（透传 对话客户端，支持深度思考类模型）
     */
    private Boolean think;

    /**
     * 搜索开关（透传 对话客户端，支持联网搜索类模型）
     */
    private Boolean search;

    /**
     * 用量记账的调用来源标识，由调用方声明业务场景（如 host_analyze）；为空按 chat 记账
     */
    private String callType;

    /**
     * 用量来源（写入 sys_ai_usage.sys_ai_usage_source）：
     * service(对外聚合服务)/third_party(三方)/self(自身)；为空按默认 chat 口径。
     */
    private String usageSource;

    /**
     * 是否把已安装技能注入 system prompt，仅用户对话轮次置 true
     */
    private Boolean useSkill;

    /**
     * 显式选中的技能名集合；非空时只注入这些技能，为空且 useSkill=true 时注入全部已启用技能
     */
    private List<String> skillNames;

    /**
     * 绑定角色的完整配置（settings JSON 解析结果）：携带本地工具 / MCP / 子代理开关，
     * 供服务端在「管理员 + 角色启用服务器侧能力」时切换到 Agent 运行时；
     * 普通用户或角色未启用相关能力时忽略，仍走直连 ChatClient。
     */
    private Map<String, Object> roleSettings;

    /**
     * 运行模式：{@code build}（默认，允许执行工具/动作）或 {@code plan}（只读规划，
     * 不产生文件写入、命令执行等副作用，先产出计划待批准）。
     * 仅管理员会话经 Agent 运行时生效；为空按 build 处理。
     */
    private String mode;

    /**
     * 业务会话标识：传入 Agent 作为 defaultSessionId，使同一对话跨轮复用 Agent 状态
     * （进入 Plan / 读取 AgentState 必需）。
     */
    private String sessionId;
}
