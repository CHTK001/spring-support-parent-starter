package com.chua.starter.server.support.enums;

import lombok.Getter;

/**
   * 主机采集方式枚举（提供者）
 * <p>
 * 定义 {@link com.chua.starter.server.support.spi.HostCollectProvider} 的实现分类，
 * 对应主机字段 {@code ms_server_host.server_host_collect_mode}（主机级采集方式）。
   * 新增采集方式时：追加枚举 + 新增 提供者 实现即可，调度与路由零改动。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Getter
public enum HostCollectType {

    /**
     * 定时 SSH 采集：服务端周期性连接目标主机执行探测命令
     * @param "SCHEDULED" "SCHEDULED"
     * @param "定时SSH采集" "定时SSH采集"
     * @return 调度的结果
     */
    SCHEDULED("SCHEDULED", "定时SSH采集"),
    /**
      * 智能体 主动上报：目标主机 智能体 按 智能体钥匙 推送数据
     * @param "AGENT" "智能体"
     * @param "Agent主动上报" "智能体主动上报"
     * @return 智能体的结果
     */
    AGENT("AGENT", "Agent主动上报"),
    /**
     * HTTP 接口拉取：目标主机暴露采集 HTTP 端点，服务端定时拉取
     * @param "HTTP" "HTTP"
     * @param "HTTP接口拉取" "HTTP接口拉取"
     * @return HTTP的结果
     */
    HTTP("HTTP", "HTTP接口拉取"),
    /**
     * SNMP 采集：网络设备/主机暴露 SNMP，服务端轮询 OID
     * @param "SNMP" "SNMP"
     * @param "SNMP采集" "SNMP采集"
     * @return SNMP的结果
     */
    SNMP("SNMP", "SNMP采集"),
    /**
      * Prometheus 拉取：从 /指标 端点抓取指标
     * @param "PROMETHEUS" "Prometheus"
     * @param "Prometheus拉取" "Prometheus拉取"
     * @return PROMETHEUS的结果
     */
    PROMETHEUS("PROMETHEUS", "Prometheus拉取");

    /**
     * 方式编码
     */
    private final String code;
    /**
     * 中文描述
     */
    private final String desc;

    /**
     * 构造
     *
     * @param code 编码
     * @param desc 描述
     */
    HostCollectType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
      * 通过编码获取枚举（找不到返回 空，由注册中心感知未注册实现）
     *
     * @param code 编码
     * @return 枚举值
     */
    public static HostCollectType of(String code) {
        if (code == null) {
            return null;
        }
        for (HostCollectType value : values()) {
            if (value.code.equalsIgnoreCase(code)) {
                return value;
            }
        }
        return null;
    }
}
