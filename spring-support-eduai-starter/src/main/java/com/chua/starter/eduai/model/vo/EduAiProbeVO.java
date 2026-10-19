package com.chua.starter.eduai.model.vo;

/**
 * AI 连通性测试结果。
 *
 * <p>与 {@link EduAiCapabilityVO} 的区别：能力探测只看「有没有可用的客户端」，
 * 不产生任何外部调用；本结果来自一次**真实的最小对话请求**，用于回答
 * 「现在这套配置到底能不能跑通」——比如密钥过期、网关不可达、模型名写错，
 * 能力探测都是「可用」，只有真正发一次请求才会失败。</p>
 *
 * <p>因此前端把它做成「测试连通」按钮的结果：成功时回显耗时与模型原话片段，
 * 失败时回显可读的错误原因。</p>
 *
 * @param ok         是否连通
 * @param message    结论说明；失败时是具体原因
 * @param provider   本次调用使用的提供商，可能为 {@code null}
 * @param model      本次调用使用的模型，可能为 {@code null}
 * @param costMillis 本次调用耗时（毫秒）
 * @param sample     模型返回内容的片段（最多 120 字），失败时为 {@code null}
 * @author CH
 * @since 4.0.0.42
 */
public record EduAiProbeVO(
        boolean ok,
        String message,
        String provider,
        String model,
        long costMillis,
        String sample
) {
}
