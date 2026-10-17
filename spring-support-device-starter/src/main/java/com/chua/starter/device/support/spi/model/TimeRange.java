package com.chua.starter.device.spi.model;

import java.time.LocalDateTime;

/**
 * 时间范围对象
 *
 * @param start 起始时间
 * @param end   结束时间
 * @since 4.0.0.42
 * @author CH
 */
public record TimeRange(
        LocalDateTime start,
        LocalDateTime end) {
}
