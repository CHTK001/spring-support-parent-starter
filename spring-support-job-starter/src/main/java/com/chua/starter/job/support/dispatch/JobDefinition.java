package com.chua.starter.job.support.dispatch;

import java.util.Objects;

/**
 * 调度任务 (内存模型)。
 *
 * @author CH
 * @since 4.0.0.42
 */
public class JobDefinition {

    /**
     * 任务名称 (与 {@link com.chua.starter.job.support.annotation.Job#value()} 对齐)
     */
    private String name;

    /**
     * cron 表达式
     */
    private String cron;

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

    @Override
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
    public int hashCode() {
        return Objects.hash(name);
    }
}
