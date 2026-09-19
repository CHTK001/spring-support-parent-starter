package com.chua.starter.job.support.dispatch;

import java.util.Objects;

/**
 * 调度任务 (内存模型)。
 *
 * @since 4.0.0.42
 * @author CH
 */
public class JobDefinition {

    /**
     * 任务名称 (与 {@link com.chua.starter.job.support.annotation.Job#value()} 对齐)
     */
    private String name;

    /**
     * cron 表达式 (三选一; 优先级 cron > fixed延迟 > fixedrate)
     */
    private String cron;

    /**
     * 固定延迟, 单位毫秒 (前一次执行完成后等待该时长触发; cron 为空且本值 > 0 时生效)
     */
    private long fixedDelayMs;

    /**
     * 固定频率, 单位毫秒 (以固定间隔触发, 不等前一次完成; 仅 cron 与 fixed延迟 均未配置时生效)
     */
    private long fixedRateMs;

    /**
     * 是否启用
     */
    private boolean enabled;

    /**
     * 任务描述
     */
    private String desc;

    /**
     * 获取任务名称。
     *
     * @return 名称
     */
    public String getName() {
        return name;
    }

    /**
     * 设置任务名称。
     *
     * @param name 名称
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * 获取 cron 表达式。
     *
     * @return cron
     */
    public String getCron() {
        return cron;
    }

    /**
     * 设置 cron 表达式。
     *
     * @param cron cron
     */
    public void setCron(String cron) {
        this.cron = cron;
    }

    /**
     * 是否启用。
     *
     * @return 启用返回 true
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 设置启用。
     *
     * @param enabled 启用
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 获取任务描述。
     *
     * @return 描述
     */
    public String getDesc() {
        return desc;
    }

    /**
     * 设置任务描述。
     *
     * @param desc 描述
     */
    public void setDesc(String desc) {
        this.desc = desc;
    }

    /**
     * 获取固定延迟 (毫秒)。
     *
     * @return 固定延迟毫秒数, 未配置为 0
     */
    public long getFixedDelayMs() {
        return fixedDelayMs;
    }

    /**
     * 设置固定延迟 (毫秒)。
     *
     * @param fixedDelayMs 固定延迟毫秒数
     */
    public void setFixedDelayMs(long fixedDelayMs) {
        this.fixedDelayMs = fixedDelayMs;
    }

    /**
     * 获取固定频率 (毫秒)。
     *
     * @return 固定频率毫秒数, 未配置为 0
     */
    public long getFixedRateMs() {
        return fixedRateMs;
    }

    /**
     * 设置固定频率 (毫秒)。
     *
     * @param fixedRateMs 固定频率毫秒数
     */
    public void setFixedRateMs(long fixedRateMs) {
        this.fixedRateMs = fixedRateMs;
    }

    @Override
    /**
     * 判断相等
    */
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof JobDefinition that)) {
            return false;
        }
        return Objects.equals(name, that.name);
    }

    @Override
    /**
     * 哈希编码
    */
    public int hashCode() {
        return Objects.hash(name);
    }
}
