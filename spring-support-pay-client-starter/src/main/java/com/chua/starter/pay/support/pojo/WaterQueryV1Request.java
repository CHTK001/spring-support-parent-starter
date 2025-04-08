package com.chua.starter.pay.support.pojo;

import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.pay.support.entity.PayMerchantOrderWater;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.util.Set;

/**
 * 流水查询
 * @author CH
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class WaterQueryV1Request extends Query<PayMerchantOrderWater> implements Serializable {

    /**
     * 用户id
     */
    private Set<String> userIds;

    /**
     * 流水类型; 1:消费 2:充值
     */
    private Integer type;
    /**
     * 开始时间
     */
    private Long startTime;
    /**
     * 结束时间
     */
    private Long endTime;
}
