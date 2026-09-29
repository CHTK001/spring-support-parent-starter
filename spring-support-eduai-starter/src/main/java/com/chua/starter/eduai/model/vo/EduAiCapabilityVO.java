package com.chua.starter.eduai.model.vo;

/**
 * AI 能力探测结果。
 *
 * <p>用于前端按「后端是否真的具备 AI 组卷能力」来决定入口的可用状态：
 * 只有 {@link #available()} 为 {@code true} 时才允许调用 AI 相关功能，
 * 否则 {@link #reason()} 会给出不可用的具体原因，可直接展示给用户。</p>
 *
 * <p>「具备能力」需要同时满足三个条件：</p>
 * <ol>
 *   <li>AI 总开关打开（{@code eduai.ai.enabled}）</li>
 *   <li>classpath 上有 spring-support-ai-starter 提供的对话客户端注册表</li>
 *   <li>注册表里能解析出一个可用的对话客户端（已配置提供商与密钥）</li>
 * </ol>
 *
 * <p>{@code provider} 与 {@code model} 仅用于前端回显，可能为 {@code null}，
 * 因此不施加非空约束。</p>
 *
 * @param available 是否可用
 * @param reason    结论说明；不可用时给出具体原因，可用时为「AI 组卷可用」
 * @param provider  配置的模型提供商，可能为 {@code null}
 * @param model     配置的模型名，可能为 {@code null}
 * @author CH
 * @since 4.0.0.42
 */
public record EduAiCapabilityVO(boolean available, String reason, String provider, String model) {
}
