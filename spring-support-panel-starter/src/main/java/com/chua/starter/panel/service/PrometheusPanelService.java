package com.chua.starter.panel.service;

import java.util.List;
import java.util.Map;

/**
 * Prometheus 面板服务。
 * 通过 HTTP API 与 Prometheus 交互，无需额外依赖。
 *
 * @author CH
 */
public interface PrometheusPanelService {

    /**
     * 打开连接并返回连接标识。
     *
     * @param host 主机地址
     * @param port 端口
     * @param username 用户名
     * @param password 密码
     * @return 连接标识
     */
    String open(String host, int port, String username, String password);

    /**
     * 关闭连接。
     *
     * @param connectionId 连接标识
     */
    void close(String connectionId);

    /**
     * 即时查询（Instant Query）。
     *
     * @param connectionId 连接标识
     * @param promql PromQL
     * @param time 查询时间
     * @return 查询结果
     */
    Map<String, Object> query(String connectionId, String promql, String time);

    /**
     * 范围查询（Range Query）。
     *
     * @param connectionId 连接标识
     * @param promql PromQL
     * @param start 开始时间
     * @param end 结束时间
     * @param step 步长
     * @return 查询结果
     */
    Map<String, Object> queryRange(String connectionId, String promql, String start, String end, String step);

    /**
     * 列出指标名称。
     *
     * @param connectionId 连接标识
     * @return 指标名称列表
     */
    List<String> listMetrics(String connectionId);

    /**
     * 列出 label 名称。
     *
     * @param connectionId 连接标识
     * @return label 列表
     */
    List<String> listLabels(String connectionId);

    /**
     * 查询 Targets。
     *
     * @param connectionId 连接标识
     * @return Targets 数据
     */
    Map<String, Object> listTargets(String connectionId);

    /**
     * 查询当前活跃告警。
     *
     * @param connectionId 连接标识
     * @return 告警数据
     */
    Map<String, Object> alerts(String connectionId);

    /**
     * 查询规则列表。
     *
     * @param connectionId 连接标识
     * @return 规则数据
     */
    Map<String, Object> rules(String connectionId);
}
