package com.chua.starter.job.support.scheduler;

/**
 * 任务分发模式。
 */
public enum JobDispatchModeEnum {

    /**
     * 本地表轮询并执行。
     */
    LOCAL,

    /**
     * 调度中心推送到远程执行器执行。
     */
    REMOTE;

    /**
     * 解析分发模式，默认 LOCAL。
     */
    public static JobDispatchModeEnum match(String value) {
        if (value == null || value.trim().isEmpty()) {
            return LOCAL;
        }
        for (JobDispatchModeEnum item : values()) {
            if (item.name().equalsIgnoreCase(value.trim())) {
                return item;
            }
        }
        return LOCAL;
    }
}
