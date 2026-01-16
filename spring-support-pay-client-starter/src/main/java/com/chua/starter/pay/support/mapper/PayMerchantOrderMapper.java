package com.chua.starter.pay.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.pojo.PayMerchantOrderPageRequest;
import com.chua.starter.pay.support.pojo.PayMerchantOrderVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 订单表 Mapper
 * 作者: CH
 * 创建时间: 2025-10-14
 * 版本: 1.0.0
 */
@Mapper
public interface PayMerchantOrderMapper extends BaseMapper<PayMerchantOrder> {

    /**
     * 分页查询订单（关联商户名等）
     * @param page 分页
     * @param entity 条件实体
     * @param cond 额外条件
     * @return 列表
     */
    IPage<PayMerchantOrderVO> selectPageForOrder(Page<?> page,
                                                @Param("entity") PayMerchantOrder entity,
                                                @Param("cond") PayMerchantOrderPageRequest cond);
}
