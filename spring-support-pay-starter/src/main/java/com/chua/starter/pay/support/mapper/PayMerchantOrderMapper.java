package com.chua.starter.pay.support.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.pojo.PayOrderQueryRequest;
import com.chua.starter.pay.support.pojo.PayOrderV1Request;
import com.chua.starter.pay.support.pojo.PayReOrderQueryV1Request;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Set;
@Mapper
public interface PayMerchantOrderMapper extends BaseMapper<PayMerchantOrder> {
    /**
     * 分页查询
     *
     * @param page    分页
     * @param request 请求
     * @return
     */
    IPage<PayMerchantOrder> pageForOrder(@Param("page") Page<PayMerchantOrder> page,
                                         @Param("request") PayOrderQueryRequest request);
    /**
     * 分页查询机构订单
     *
     * @param page    分页
     * @param request 查询条件
     * @return
     */
    IPage<PayMerchantOrder> pageForOrderDept(@Param("page") Page<PayMerchantOrder> page,
                                             @Param("request") PayOrderQueryRequest request);

    /**
     * 分页查询机构订单
     *
     * @param page    分页
     * @param request 查询条件
     * @return
     */
    IPage<PayMerchantOrder> pageForOrderDeptOrganizer(@Param("page") Page<PayMerchantOrder> page,
                                                      @Param("request") PayOrderQueryRequest request);
    /**
     * 分页查询
     *
     * @param page      分页
     * @param request   查询条件
     * @param sysUserIds 账单所属用户
     * @return
     */
    IPage<PayMerchantOrder> order(@Param("page") Page<PayMerchantOrder> page,
                                  @Param("request") PayOrderV1Request request,
                                  @Param("sysUserIds") Set<String> sysUserIds);

    /**
     * 分页查询
     *
     * @param page    分页
     * @param request 查询条件
     * @return
     */
    IPage<PayMerchantOrder> reOrder(@Param("page") Page<PayMerchantOrder> page, @Param("request") PayReOrderQueryV1Request request);


}