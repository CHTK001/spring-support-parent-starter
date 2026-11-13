package com.chua.starter.ai.support.chat;

import com.chua.common.support.ai.chat.ChatClient;

import java.util.Map;

/**
 * AI 对话网关 —— 所有业务模块调用大模型的统一出口。
 *
 * <p>业务模块（eduai 等）只描述「说什么」，不再自己解析客户端、拼装供应商配置、
 * 判断限流、实现重试退避。这些都属于 AI 模块职责，散落在业务模块里会带来两个后果：
 * 每处实现的口径慢慢分叉（有的读 runtime、有的只读注册表），且 AI 配置形式一变就要
 * 改业务代码。本网关把这些收敛为一处：</p>
 *
 * <ul>
 *   <li>{@link #resolveClient()} —— 客户端解析链：AI 控制台 runtime 主链路 &gt; 静态
 *       YAML 指定厂商 &gt; 默认模型注册 &gt; 容器聚合客户端；</li>
 *   <li>{@link #complete(String, String, long)} —— 单次同步调用（system + user 合并）；</li>
 *   <li>{@link #completeWithRetry(String, String, AiChatOptions)} —— 限流/瞬时故障重试；</li>
 *   <li>{@link #capability()} / {@link #probe(long)} —— 能力探测与真实连通性测试。</li>
 * </ul>
 *
 * <p>接口不读任何业务配置：超时、重试次数等由调用方以 {@link AiChatOptions} 传入，
 * 因此同一套传输逻辑可被阅卷、组卷、问答等不同业务按各自预算复用。</p>
 *
 * <p>业务模块只依赖本接口；实现由 {@code DefaultAiChatGateway} 提供，更换实现只需替换
 * 容器内的 Bean，业务代码不动。</p>
 *
 * @author CH
 * @since 4.0.0.44
 */
public interface AiChatGateway {

    /**
     * 默认超时：90 秒（与历史阅卷单次调用预算一致）
     */
    long DEFAULT_TIMEOUT_MILLIS = 90_000L;

    /**
     * 默认重试次数（不含首次）
     */
    int DEFAULT_MAX_RETRIES = 5;

    /**
     * 是否已有可用的对话客户端。
     *
     * @return true 表示可以发起模型调用
     */
    boolean available();

    /**
     * 解析对话客户端。
     *
     * <p>解析顺序（第一个可用者胜出）：</p>
     * <ol>
     *   <li>AI 控制台「系统设置」的 {@code runtime} 分组（provider / api_key / base_url / model），
     *       配置齐全时直接按它构建 —— UI 配置优先于 YAML；</li>
     *   <li>YAML 显式指定 {@code plugin.ai.chat.provider} 时对应的客户端；</li>
     *   <li>默认对话客户端（AI 控制台「默认模型」保存时热更新登记）；</li>
     *   <li>容器注入的聚合 {@link ChatClient}（UI 未配置时的兜底）。</li>
     * </ol>
     *
     * @return 可用的对话客户端；都没有时返回 null
     */
    ChatClient resolveClient();

    /**
     * 探测 AI 对话能力，给出「可用与否 + 不可用的具体原因」。
     *
     * <p>判定只看 {@link #resolveClient()} 能否解析出客户端；回显的厂商/模型取「实际生效」值，
     * 保证界面显示与实际调用一致。</p>
     *
     * @return 能力探测结果
     */
    AiChatCapability capability();

    /**
     * 测试连通性：用当前生效的客户端真实发一条最小请求。
     *
     * <p>能力探测只判断「解析得出客户端」，密钥过期、网关不可达、模型名写错时仍会显示可用；
     * 这里把同一条解析链跑一遍并立刻调用，让配置问题在配置页暴露，而不是等到业务链路里
     * 才以「判分掉 0 分」的形式出现。</p>
     *
     * @param timeoutMillis 单次探测超时（毫秒），小于 5000 时按 5000 处理
     * @return 连通性测试结果
     */
    AiChatProbe probe(long timeoutMillis);

    /**
     * 单次同步调用。
     *
     * @param systemPrompt  系统提示词，可为空白
     * @param userPrompt    用户提示词
     * @param timeoutMillis 超时（毫秒），小于等于 0 时按 {@value #DEFAULT_TIMEOUT_MILLIS} 处理
     * @return 模型输出；客户端不可用时返回空串
     */
    String complete(String systemPrompt, String userPrompt, long timeoutMillis);

    /**
     * 带重试的同步调用：限流与瞬时故障按退避曲线重试，其余异常立即抛出。
     *
     * @param systemPrompt 系统提示词
     * @param userPrompt   用户提示词
     * @param options      调用选项，为 null 时用默认值
     * @return 模型输出
     * @throws RuntimeException 重试耗尽或不可重试的失败
     */
    String completeWithRetry(String systemPrompt, String userPrompt, AiChatOptions options);

    /**
     * 当前 AI 对话运行状态（供运维排查）。
     *
     * @return 状态键值
     */
    Map<String, Object> status();

    /**
     * 判断异常链是否为「瞬时故障」：超时 / 502 / 503 / 连接重置等。
     *
     * @param error 异常
     * @return true 表示值得重试
     */
    static boolean isTransientFailure(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            String name = t.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
            if (name.contains("timeout") || name.contains("connect")) {
                return true;
            }
            String message = t.getMessage();
            if (message == null) {
                continue;
            }
            String lower = message.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("timeout") || lower.contains("timed out")
                    || lower.contains(" 502") || lower.contains(" 503") || lower.contains(" 504")
                    || lower.contains("connection reset") || lower.contains("temporarily")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断异常链里是否出现限流（429 / rate limit / 配额）。
     *
     * @param error 异常
     * @return true 表示是限流
     */
    static boolean isRateLimited(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT).contains("ratelimit")) {
                return true;
            }
            String message = t.getMessage();
            if (message == null) {
                continue;
            }
            String lower = message.toLowerCase(java.util.Locale.ROOT);
            if (lower.contains("rate limit") || lower.contains("rate_limit")
                    || lower.contains("too many requests") || lower.contains(" 429 ")
                    || lower.contains("tpm limit") || lower.contains("rpm limit")
                    || lower.contains("rpm exhausted") || lower.contains("tpm exhausted")
                    || lower.contains("quota") || lower.contains("throttl")
                    || lower.contains("限流")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 能力探测结果。
     *
     * @param available 是否可用
     * @param reason    可读原因（不可用时给出可操作提示）
     * @param provider  实际生效的厂商
     * @param model     实际生效的模型
     */
    record AiChatCapability(boolean available, String reason, String provider, String model) {
    }

    /**
     * 连通性测试结果。
     *
     * @param success    是否成功
     * @param message    结果说明
     * @param provider   实际生效的厂商
     * @param model      实际生效的模型
     * @param costMillis 耗时（毫秒）
     * @param sample     模型回复样例（截断到 120 字）
     */
    record AiChatProbe(boolean success, String message, String provider, String model,
                       long costMillis, String sample) {
    }

    /**
     * 单次调用选项。
     *
     * @param timeoutMillis     单次调用超时（毫秒）
     * @param maxRetries        重试次数（不含首次）
     * @param backoffMillis     首次退避（毫秒）
     * @param backoffMultiplier 退避倍数
     * @param maxBackoffMillis  退避上限（毫秒）
     * @param retryOnTransient  瞬时故障是否重试
     */
    record AiChatOptions(long timeoutMillis, int maxRetries, long backoffMillis,
                         double backoffMultiplier, long maxBackoffMillis, boolean retryOnTransient) {

        /**
         * 默认选项：90 秒超时、5 次重试、1 秒起、2 倍退避、上限 20 秒、瞬时故障重试。
         *
         * @return 默认选项
         */
        public static AiChatOptions defaults() {
            // [P3C A20 豁免]
            return new AiChatOptions(DEFAULT_TIMEOUT_MILLIS, DEFAULT_MAX_RETRIES, 1000L,
                    2.0D, 20_000L, true);
        }
    }
}
