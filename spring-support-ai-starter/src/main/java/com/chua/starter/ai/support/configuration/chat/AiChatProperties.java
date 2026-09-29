package com.chua.starter.ai.support.configuration.chat;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 对话客户端的 YAML 配置。
 *
 * <p>此前对话客户端只能由数据库（{@code sys_ai_provider}）驱动装配，
 * 部署时无法用配置文件声明「用哪个厂商、走哪个网关、用哪个模型」。
 * 本类把这件事搬到 {@code application.yml}，便于按环境（开发 / 测试 / 生产）替换，
 * 也便于在容器里用环境变量覆盖：</p>
 *
 * <pre>
 * chua:
 *   ai:
 *     chat:
 *       enabled: true
 *       provider: zhipu                 # openai / siliconflow / sensetime / github / gitee / dots / zhipu / glm / zai
 *       base-url: https://open.bigmodel.cn/api/paas/v4
 *       app-key: ${AI_CHAT_APP_KEY}
 *       model: glm-4-flash
 * </pre>
 *
 * <p>所有组件均可为空：为空时用各实现自身的默认值（例如 {@code base-url} 为空则用该厂商的官方地址）。
 * 因此这里不做非空约束 —— 缺配置应当表现为「该参数走默认」，而不是启动失败。</p>
 *
 * <p>{@code enabled} 为 {@code true} 时才注册对话客户端 bean；默认关闭，
 * 避免在没配密钥的环境里凭空造出一个必然调用失败的客户端。</p>
 *
 * @param enabled       是否启用 YAML 配置的对话客户端
 * @param provider      厂商标识，决定用哪个实现类
 * @param baseUrl       API 基地址；为空时用实现类内置默认地址
 * @param appKey        访问密钥
 * @param appSecret     密钥对应的 secret（部分厂商需要）
 * @param model         模型名
 * @param temperature   采样温度
 * @param maxTokens     单次最大生成 token 数
 * @param timeoutMillis 超时（毫秒）
 * @param authScheme    鉴权方案
 * @param authHeader    自定义鉴权头名
 * @param authPrefix    鉴权头前缀（如 {@code Bearer }）
 * @param proxy         代理地址
 * @author CH
 * @since 4.0.0.42
 */
@ConfigurationProperties(prefix = "chua.ai.chat")
public record AiChatProperties(
        boolean enabled,
        String provider,
        String baseUrl,
        String appKey,
        String appSecret,
        String model,
        Double temperature,
        Integer maxTokens,
        Long timeoutMillis,
        String authScheme,
        String authHeader,
        String authPrefix,
        String proxy
) {
}
