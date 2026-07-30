package com.chua.starter.panel.service;

import java.util.List;
import java.util.Map;

/**
 * InfluxDB 2.x 面板服务。
 * 通过 influxdb-client-java 与 InfluxDB 2.x 交互。
 *
 * @author CH
 */
public interface InfluxPanelService {

    /**
     * 打开连接并返回连接标识。
     *
     * @param host 主机地址
     * @param port 端口
     * @param token Token
     * @param org 组织
     * @param bucket 默认 Bucket
     * @return 连接标识
     */
    String open(String host, int port, String token, String org, String bucket);

    /**
     * 关闭连接。
     *
     * @param connectionId 连接标识
     */
    void close(String connectionId);

    /**
     * 列出 Bucket。
     *
     * @param connectionId 连接标识
     * @return Bucket 列表
     */
    List<String> listBuckets(String connectionId);

    /**
     * 列出指定 Bucket 下的 Measurement。
     *
     * @param connectionId 连接标识
     * @param bucket Bucket 名称
     * @return Measurement 列表
     */
    List<String> listMeasurements(String connectionId, String bucket);

    /**
     * 执行 Flux 查询。
     *
     * @param connectionId 连接标识
     * @param fluxQuery Flux 查询语句
     * @return 查询结果
     */
    List<Map<String, Object>> query(String connectionId, String fluxQuery);

    /**
     * 写入 Line Protocol 数据。
     *
     * @param connectionId 连接标识
     * @param bucket Bucket 名称
     * @param lineProtocol Line Protocol 文本
     */
    void write(String connectionId, String bucket, String lineProtocol);

    /**
     * 查询健康状态。
     *
     * @param connectionId 连接标识
     * @return 健康状态
     */
    Map<String, Object> health(String connectionId);
}
