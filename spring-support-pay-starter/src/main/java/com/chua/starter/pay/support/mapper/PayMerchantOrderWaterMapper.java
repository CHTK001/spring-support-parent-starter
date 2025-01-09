package com.chua.starter.pay.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.pay.support.entity.PayMerchantOrderWater;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.Set;

@Mapper
public interface PayMerchantOrderWaterMapper extends BaseMapper<PayMerchantOrderWater> {

    /**
     * water
     *
     * @param page      page
     * @param userIds   userIds
     * @param startDate startDate
     * @param endDate   endDate
     * @return PayMerchantOrderWater
     */
    IPage<PayMerchantOrderWater> water(
            @Param("page") Page<PayMerchantOrderWater> page,
            @Param("userIds") Set<String> userIds,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}