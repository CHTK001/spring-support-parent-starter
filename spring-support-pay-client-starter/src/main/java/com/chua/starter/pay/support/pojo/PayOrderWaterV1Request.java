package com.chua.starter.pay.support.pojo;

import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.pay.support.entity.PayMerchantOrderWater;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 支付流水
 * @author CH
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class PayOrderWaterV1Request extends Query<PayMerchantOrderWater> implements Serializable {


    /**
     * 开始时间
     */
    private LocalDate startDate;

    /**
     * 结束时间
     */
    private LocalTime endDate;
}
