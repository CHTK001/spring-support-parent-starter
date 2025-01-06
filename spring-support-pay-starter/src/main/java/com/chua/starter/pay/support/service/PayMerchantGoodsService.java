package com.chua.starter.pay.support.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.pay.support.entity.PayMerchant;
import com.chua.starter.pay.support.entity.PayMerchantGoods;

/**
 * @author CH
 * @since 2024/12/30
 */
// 接口文档：支付商户商品服务，提供对支付商户商品的相关操作
public interface PayMerchantGoodsService extends IService<PayMerchantGoods> {

    /**
     * 保存支付商品信息
     *
     * @param payMerchantGoods 支付商品信息
     * @return 保存后的支付商品对象，包括生成的唯一标识
     */
    PayMerchantGoods savePayGoods(PayMerchantGoods payMerchantGoods);

    /**
     * 更新支付商品信息
     *
     * @param payMerchantGoods 需要更新的支付商品信息，包括商品ID
     * @return 操作结果，包括是否更新成功
     */
    ReturnResult<Boolean> updatePayGoods(PayMerchantGoods payMerchantGoods);

    /**
     * 删除指定ID的支付商品信息
     *
     * @param goodsId 商品ID
     * @return 操作结果，包括是否删除成功
     */
    ReturnResult<Boolean> deletePayGoods(Integer goodsId);

    /**
     * 根据查询条件分页查询商品信息
     *
     * @param query 查询条件，包括分页参数和筛选条件
     * @return 分页查询结果，包括商品列表和分页信息
     */
    IPage<PayMerchantGoods> pageForGoods(Query<PayMerchantGoods> query);
}
