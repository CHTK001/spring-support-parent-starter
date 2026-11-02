package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.model.ChannelCircuitSnapshot;

import java.util.List;

/**
 * 请求级熔断器（对标 Sub2API Circuit Breaker）。
 *
 * <h2>与渠道健康探针的分工</h2>
 * <p>阶段 1 的探针（{@link ChannelHealthService}）是<b>低频主动探测</b>：每 30/60/300 秒
 * 对每个连接发一次轻量请求，连续失败达阈值把连接判为 {@code DOWN}，路由层剔除。
 * 它擅长发现「整条通道挂了」，但对「某个模型在当前账号下持续 500」这类<b>请求级</b>故障
 * 反应慢一拍 —— 探针可能还在返回 200，真实对话已经连续失败。</p>
 *
 * <p>本熔断器补上这一层：按 <b>{@code providerId::model}</b> 统计<b>真实对话请求</b>的
 * 成功/失败，连续失败达阈值即打开熔断（{@code OPEN}），在冷却期内<b>不再向上游发请求</b>
 * （fail-closed，直接 503）。冷却结束进入 {@code HALF_OPEN}，放行<b>一个</b>试探请求：
 * 成功则复位为 {@code CLOSED}，失败则重新打开并重置冷却计时。</p>
 *
 * <h2>为什么按模型而不是按连接</h2>
 * <p>同一个连接上不同模型的表现可以完全不同（某模型被上游降级、某模型配额耗尽）。
 * 按 {@code providerId::model} 统计，既不会因为一个模型坏掉而误杀整条通道，也不会
 * 因为其他模型正常而掩盖坏模型。连接级的自动下线仍由探针负责，两层叠加。</p>
 *
 * <h2>状态存储</h2>
 * <p>全部在内存中（不落库）：熔断是秒级/分钟级的短周期状态，写库既慢又会产生大量
 * 无意义的历史行。重启后归零，等价于「重启即复位」，符合运维直觉。</p>
 *
 * <h2>失败语义</h2>
 * <p>读设置失败、或内部统计异常时一律 <b>fail-open</b>（放行），熔断器自身绝不能成为
 * 新的故障点。</p>
 *
 * @author CH
 * @since 4.0.0.58
 */
public interface ChannelCircuitService {

    /**
     * 熔断关闭（正常路由）
     */
    String STATE_CLOSED = "CLOSED";

    /**
     * 熔断打开（拒绝路由）
     */
    String STATE_OPEN = "OPEN";

    /**
     * 半开（冷却结束，放行一个试探请求）
     */
    String STATE_HALF_OPEN = "HALF_OPEN";

    /**
     * 本次请求是否允许路由到该「供应商 + 模型」。
     *
     * <p>熔断总开关关闭、或统计异常时返回 {@code true}（fail-open）。</p>
     *
     * @param providerId 供应商标识，可为空
     * @param model      模型名，可为空
     * @return true 表示允许向上游发请求
     */
    boolean allowRoute(String providerId, String model);

    /**
     * 记录一次真实对话成功（复位熔断）。
     *
     * @param providerId 供应商标识
     * @param model      模型名
     */
    void recordSuccess(String providerId, String model);

    /**
     * 记录一次真实对话失败（累计到阈值则打开熔断）。
     *
     * @param providerId 供应商标识
     * @param model      模型名
     * @param error      失败原因（仅用于日志与快照展示）
     */
    void recordFailure(String providerId, String model, String error);

    /**
     * 当前熔断状态快照（管理页/接口展示用）。
     *
     * @return 快照列表，按打开时间倒序
     */
    List<ChannelCircuitSnapshot> snapshots();

    /**
     * 人工复位指定「供应商 + 模型」（等价于记一次成功）。
     *
     * @param providerId 供应商标识
     * @param model      模型名
     * @return 复位前是否存在该条目
     */
    boolean reset(String providerId, String model);

    /**
     * 复位全部熔断条目（运维操作）。
     *
     * @return 被复位的条目数
     */
    int resetAll();
}
