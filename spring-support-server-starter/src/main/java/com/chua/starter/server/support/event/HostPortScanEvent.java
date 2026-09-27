package com.chua.starter.server.support.event;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 主机端口扫描事件
 * <p>
 * 端口扫描为后台异步执行：提交扫描请求后立即返回，扫描线程在完成（成功/失败）后发布本事件。
 * 监控模块监听该事件并转发为 Socket {@code host-portscan} 推送，前端据此实时更新端口字段，
 * 全程不需要前端轮询等待。
 * </p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Getter
public class HostPortScanEvent {

    /**
     * 主机标识
     */
    private final Long hostId;

    /**
     * 主机地址
     */
    private final String address;

    /**
     * 是否扫描成功
     */
    private final boolean success;

    /**
     * 发现的开放端口数量
     */
    private final int openCount;

    /**
     * 本次扫描的端口总数
     */
    private final int totalPorts;

    /**
     * 扫描耗时（秒）
     */
    private final long elapsedSeconds;

    /**
     * 端口结果 JSON（成功时写入主机台账的内容）
     */
    private final String portsJson;

    /**
     * 扫描完成时间
     */
    private final LocalDateTime scanTime;

    /**
     * 失败原因（成功时为 null）
     */
    private final String error;

    /**
     * 构造
     *
     * @param hostId         主机标识
     * @param address        主机地址
     * @param success        是否成功
     * @param openCount      开放端口数
     * @param totalPorts     扫描端口总数
     * @param elapsedSeconds 耗时秒
     * @param portsJson      端口结果 JSON
     * @param scanTime       完成时间
     * @param error          失败原因
     */
    public HostPortScanEvent(Long hostId, String address, boolean success, int openCount, int totalPorts,
                             long elapsedSeconds, String portsJson, LocalDateTime scanTime, String error) {
        this.hostId = hostId;
        this.address = address;
        this.success = success;
        this.openCount = openCount;
        this.totalPorts = totalPorts;
        this.elapsedSeconds = elapsedSeconds;
        this.portsJson = portsJson;
        this.scanTime = scanTime;
        this.error = error;
    }
}
