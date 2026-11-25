package com.chua.payment.support.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chua.payment.support.entity.Merchant;
import com.chua.payment.support.mapper.MerchantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 支付分表开关闸门：判断是否存在启用分表的商户。
 *
 * <p>分表是按业务类型（订单/交易流水）维护的全局分区表基础设施，而
 * {@code pay_merchant.pay_merchant_payment_split_table_enabled} 是商户级开关；
 * 只要任意商户开启分表，分区创建/迁移任务就需要运转，否则自动任务整体跳过。
 * 手动触发端点不经过本闸门，便于运维在无商户开启时仍可演练分表流程。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@Component
@RequiredArgsConstructor
public class PaymentSplitTableGate {

    /**
     * 商户Mapper
     */
    private final MerchantMapper merchantMapper;

    /**
     * 是否存在启用支付分表的商户
     *
     * @return true=至少一个商户开启分表；false=全部未开启或查询无结果
     */
    public boolean anyMerchantEnabled() {
        Long count = merchantMapper.selectCount(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getPaymentSplitTableEnabled, Boolean.TRUE));
        return count != null && count > 0;
    }
}
