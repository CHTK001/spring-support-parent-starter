package com.chua.starter.device.spi.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
* 门禁出入记录
*
* @since 4.0.0.42
* @author CH
 */
@Data
public class AccessRecord {

    /**
    * 门 标识
    */
    private String doorId;

    /**
    * 卡号
    */
    private String cardNo;

    /**
    * 用户名
    */
    private String userName;

    /**
    * 刷卡时间
    */
    private LocalDateTime time;

    /**
    * 事件类型，例如刷卡开门、密码开门、异常
    */
    private String eventType;
}
