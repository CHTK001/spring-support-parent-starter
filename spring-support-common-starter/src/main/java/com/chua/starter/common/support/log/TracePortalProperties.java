package com.chua.starter.common.support.log;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 链路日志汇聚（TraceLogPortal）配置属性。
 *
 * <p>默认关闭：只有 {@code plugin.log.portal.enabled=true} 才会装配汇聚 Appender。
 * 关闭时对宿主零影响（不分配缓冲、不起线程、不改任何现有 appender）。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@ConfigurationProperties(prefix = "plugin.log.portal", ignoreInvalidFields = true)
public class TracePortalProperties {

    /**
     * 是否启用链路日志汇聚。
     */
    private boolean enabled = false;

    /**
     * 汇聚块输出文件路径，默认 ./logs/trace-portal.log。
     */
    private String file = "./logs/trace-portal.log";

    /**
     * 单链路最大缓冲条数，达到即收口，默认 200。
     */
    private int maxEventsPerTrace = 200;

    /**
     * 链路空闲多久（毫秒）自动收口，默认 3000。
     */
    private long idleMillis = 3000L;

    /**
     * 最大待收口链路数，超过淘汰最老的，默认 512。
     */
    private int maxPendingTraces = 512;

    /**
     * 空闲清扫周期（毫秒），默认 1000。
     */
    private long sweepIntervalMillis = 1000L;

    /**
     * 判断是否启用。
     *
     * @return 是否启用
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置是否启用。
     *
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取输出文件路径。
     *
     * @return 路径
     */
    public String getFile() {
        return file;
    }

    /**
     * 设置输出文件路径。
     *
     * @param file 路径
     */
    public void setFile(String file) {
        this.file = file;
    }

    /**
     * 获取单链路最大缓冲条数。
     *
     * @return 条数
     */
    public int getMaxEventsPerTrace() {
        return maxEventsPerTrace;
    }

    /**
     * 设置单链路最大缓冲条数。
     *
     * @param maxEventsPerTrace 条数
     */
    public void setMaxEventsPerTrace(int maxEventsPerTrace) {
        this.maxEventsPerTrace = maxEventsPerTrace;
    }

    /**
     * 获取空闲收口阈值。
     *
     * @return 毫秒
     */
    public long getIdleMillis() {
        return idleMillis;
    }

    /**
     * 设置空闲收口阈值。
     *
     * @param idleMillis 毫秒
     */
    public void setIdleMillis(long idleMillis) {
        this.idleMillis = idleMillis;
    }

    /**
     * 获取最大待收口链路数。
     *
     * @return 数量
     */
    public int getMaxPendingTraces() {
        return maxPendingTraces;
    }

    /**
     * 设置最大待收口链路数。
     *
     * @param maxPendingTraces 数量
     */
    public void setMaxPendingTraces(int maxPendingTraces) {
        this.maxPendingTraces = maxPendingTraces;
    }

    /**
     * 获取清扫周期。
     *
     * @return 毫秒
     */
    public long getSweepIntervalMillis() {
        return sweepIntervalMillis;
    }

    /**
     * 设置清扫周期。
     *
     * @param sweepIntervalMillis 毫秒
     */
    public void setSweepIntervalMillis(long sweepIntervalMillis) {
        this.sweepIntervalMillis = sweepIntervalMillis;
    }

    /**
     * 转成内核配置。
     *
     * @return 内核配置
     */
    public TraceLogPortal.Config toConfig() {
        return new TraceLogPortal.Config(maxEventsPerTrace, idleMillis,
                maxPendingTraces, sweepIntervalMillis);
    }
}
