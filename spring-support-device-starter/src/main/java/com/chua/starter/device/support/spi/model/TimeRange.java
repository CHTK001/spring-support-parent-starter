package com.chua.starter.device.spi.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
* 时间范围对象
*
* @since 4.0.0.42
* @author CH
 */
@Data
@AllArgsConstructor
public class TimeRange {

    /**
    * 起始时间
     */
    private LocalDateTime start;

    /**
    * 结束时间
     */
    private LocalDateTime end;
}