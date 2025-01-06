package com.chua.starter.pay.support.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.pay.support.entity.PayMerchant;
import com.chua.starter.pay.support.entity.PayMerchantGoods;
import com.chua.starter.pay.support.mapper.PayMerchantGoodsMapper;
import com.chua.starter.pay.support.service.PayMerchantGoodsService;
import org.springframework.stereotype.Service;

/**
 *
 * @since 2024/12/30
 * @author CH    
 */
@Service
public class PayMerchantGoodsServiceImpl extends ServiceImpl<PayMerchantGoodsMapper, PayMerchantGoods> implements PayMerchantGoodsService {


    @Override
    public PayMerchantGoods savePayGoods(PayMerchantGoods payMerchantGoods) {
        save(payMerchantGoods);
        return payMerchantGoods;
    }

    @Override
    public ReturnResult<Boolean> updatePayGoods(PayMerchantGoods payMerchantGoods) {
        return ReturnResult.ok(updateById(payMerchantGoods));
    }

    @Override
    public ReturnResult<Boolean> deletePayGoods(Integer goodsId) {
        return ReturnResult.ok(removeById(goodsId));
    }

    @Override
    public IPage<PayMerchantGoods> pageForGoods(Query<PayMerchantGoods> query) {
        return baseMapper.selectPage(query.createPage(), query.mpjLambda());
    }
}
