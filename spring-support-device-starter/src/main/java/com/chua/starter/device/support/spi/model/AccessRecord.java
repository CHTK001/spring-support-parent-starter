package com.chua.starter.device.spi.model;

import java.time.LocalDateTime;

/**
 * 门禁出入记录
 *
 * @param doorId    门标识
 * @param cardNo    卡号
 * @param userName  用户名
 * @param time      刷卡时间
 * @param eventType 事件类型，例如刷卡开门、密码开门、异常
 * @since 4.0.0.42
 * @author CH
 */
public record AccessRecord(
        String doorId,
        String cardNo,
        String userName,
        LocalDateTime time,
        String eventType) {
}
