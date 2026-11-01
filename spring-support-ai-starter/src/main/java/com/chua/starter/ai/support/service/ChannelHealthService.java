package com.chua.starter.ai.support.service;

import com.chua.starter.ai.support.manage.store.ChannelHealth;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 渠道健康探针服务。
 *
 * <p>对标 Sub2API Channel Monitor：以「提供者 + 连接（Channel）」为粒度进行探活，
 * 把探针结果（成功/失败/延迟/限流）汇正成状态机（UNKNOWN / ONLINE / WARNING / DOWN / RATE_LIMITED），
 * 并对外开放手动探针与间隔控制接口，状态机结果被路由层消费（下线通道断路保护、恢复后自动上浮）。</p>
 *
 * @author CH
 * @since 4.0.0.57
 */
public interface ChannelHealthService {

    /**
     * 未探测（默认）状态
     */
    String STATE_UNKNOWN = "UNKNOWN";

    /**
     * 正常状态
     */
    String STATE_ONLINE = "ONLINE";

    /**
     * 警告状态（延迟超阈值或间歇失败）
     */
    String STATE_WARNING = "WARNING";

    /**
     * 故障状态（连续失败达阈值，路由层应剔除）
     */
    String STATE_DOWN = "DOWN";

    /**
     * 上游限流状态（命中 429，由计费层/重试层处理）
     */
    String STATE_RATE_LIMITED = "RATE_LIMITED";

    /**
     * 列出全部渠道的健康状态（按 provider → connection 维度）。
     *
     * @return 状态列表
     */
    List<ChannelHealth> listStates();

    /**
     * 列出指定提供商的渠道健康状态。
     *
     * @param providerId 提供商标识
     * @return 状态列表
     */
    List<ChannelHealth> listByProvider(String providerId);

    /**
     * 按连接标识取健康状态（探活结果）。
     *
     * @param connectionId 连接标识
     * @return 健康状态
     */
    ChannelHealth getState(String connectionId);

    /**
     * 该连接是否已下线（失效通道，路由层应剔除）。
     *
     * @param connectionId 连接标识
     * @return true 表示连线已下
     */
    boolean isDown(String connectionId);

    /**
     * 连接是否可用（在线 / 警告 / 未探测均可用，DOWN 与 RATE_LIMITED 不可用）。
     *
     * @param connectionId 连接标识
     * @return true 表示可用
     */
    boolean isHealthy(String connectionId);

    /**
     * 取全部已下线的连接标识（路由层剔除）
     *
     * @return 下线的连接标识集合
     */
    java.util.Set<String> downConnections();

    /**
     * 手动触发一次探针（用于前端「立即探针」按钮）。
     *
     * @param connectionId 连接标识
     * @return 探针结果（状态、延迟、错误）
     */
    ChannelHealth probe(String connectionId);

    /**
     * 手动触发全部渠道的探针（用于管理页「全部探针」按钮）
     *
     * @return 探针完成的连接数
     */
    int probeAll();

    /**
     * 记录一次成功探活（含 HTTP 状态码与延迟）
     *
     * @param connectionId 连接标识
     * @param status HTTP 状态码
     * @param latency 延迟（毫秒）
     */
    void markSuccess(String connectionId, int status, int latency);

    /**
     * 记录一次失败探活
     *
     * @param connectionId 连接标识
     * @param status HTTP 状态码（失败时可抛异常则传 0）
     * @param error 错误信息
     */
    void markFailure(String connectionId, int status, String error);

    /**
     * 设定该连接的探针间隔（0 = 走全局设置，否则 30/60/300）
     *
     * @param connectionId 连接标识
     * @param interval 间隔秒数（30/60/300）
     */
    void updateInterval(String connectionId, int interval);

    /**
     * 重置连接的健康状态（启动后手动复位）
     *
     * @param connectionId 连接标识
     */
    void resetState(String connectionId);

    /**
     * 统计维度：指定 provider 下各状态的连接数
     *
     * @param providerId 提供商标识
     * @return stat 摘要
     */
    Map<String, Integer> statByProvider(String providerId);
}
