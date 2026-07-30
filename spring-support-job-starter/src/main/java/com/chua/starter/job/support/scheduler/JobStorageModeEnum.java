package com.chua.starter.job.support.scheduler;

/**
 * 任务元数据存储模式。
 * <p>
 * 当前默认 DATABASE，后续通过 SPI 扩展 Redis 等模式。
 * </p>
 */
public enum JobStorageModeEnum {
    DATABASE,
    REDIS;

    /**
     * 解析存储模式，默认 DATABASE。
     */
    public static JobStorageModeEnum match(String value) {
        if (value == null || value.trim().isEmpty()) {
            return DATABASE;
        }
        for (JobStorageModeEnum item : values()) {
            if (item.name().equalsIgnoreCase(value.trim())) {
                return item;
            }
        }
        return DATABASE;
    }
}
