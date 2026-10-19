package com.chua.starter.monitor.support.node;

import java.util.Map;

/**
 * 发现服务实例（对齐前端 DiscoveryInstance 契约）。
 *
 * @param id       实例标识
 * @param host     主机
 * @param port     端口
 * @param protocol 协议
 * @param weight   权重
 * @param env      环境
 * @param uriSpec  服务标识
 * @param metadata 元数据
 * @author CH
 * @since 4.0.0.42
 */
public record DiscoveryInstanceVO(String id,
                                  String host,
                                  Integer port,
                                  String protocol,
                                  Integer weight,
                                  String env,
                                  String uriSpec,
                                  Map<String, Object> metadata) {
}
