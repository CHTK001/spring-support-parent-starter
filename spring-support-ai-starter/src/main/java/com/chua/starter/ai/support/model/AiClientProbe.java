package com.chua.starter.ai.support.model;

/**
 * AI 对话客户端可用性探测结果。
 *
 * <p>用于在不发起网络请求的前提下回答「这个模型现在能不能对话」，
 * 判据与真实对话走同一条解析链路，避免状态接口与调用接口各说一套。</p>
 *
 * @param available  是否存在可用客户端
 * @param model      解析后的模型名（别名已归一），未解析出时为空
 * @param provider   提供商标识，未解析出时为空
 * @param baseUrl    实际请求地址，用于确认是否指向了预期的服务
 * @param clientType 客户端实现类简名，便于定位走了 SPI 还是聚合兜底
 * @param reason     不可用原因，可用时为空
 * @author CH
 * @since 4.0.0.42
 */
public record AiClientProbe(
        boolean available,
        String model,
        String provider,
        String baseUrl,
        String clientType,
        String reason
) {

    /**
     * 构造可用结果。
     *
     * @param model      解析后的模型名
     * @param provider   提供商标识
     * @param baseUrl    实际请求地址
     * @param clientType 客户端实现类简名
     * @return 可用结果
     */
    public static AiClientProbe available(String model, String provider, String baseUrl, String clientType) {
        return new AiClientProbe(true, model, provider, baseUrl, clientType, null);
    }

    /**
     * 构造不可用结果。
     *
     * @param model  请求的目标模型，可为空
     * @param reason 不可用原因
     * @return 不可用结果
     */
    public static AiClientProbe unavailable(String model, String reason) {
        return new AiClientProbe(false, model, null, null, null,
                reason == null || reason.isBlank() ? "未找到可用的对话客户端" : reason);
    }
}
