package com.chua.starter.spider.enums;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 采集数据执行结果状态枚举。
 * <p>用于 {@code spider_data_record.spider_data_status} 与 {@code spider_execution_record.spider_execution_status}。
 *
 * @author CH
 * @since 4.0.0.42
 */
@Getter
@Schema(description = "采集执行状态")
public enum SpiderExecutionStatus {

    /**
     * 执行成功
     */
    SUCCESS("SUCCESS", "成功"),

    /**
     * 执行失败
     */
    FAILED("FAILED", "失败"),

    /**
     * 正在执行
     */
    RUNNING("RUNNING", "运行中"),

    /**
     * 跳过本次执行
     */
    SKIPPED("SKIPPED", "已跳过"),

    /**
     * 用户手动停止
     */
    STOPPED("STOPPED", "已停止"),

    /**
     * 已暂停（保留用于未来支持）
     */
    PAUSED("PAUSED", "已暂停");

    /**
     * 状态码
     */
    private final String code;

    /**
     * 中文描述
     */
    private final String desc;

    /**
     * 构造枚举项。
     *
     * @param code 状态码
     * @param desc 中文描述
     */
    SpiderExecutionStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
