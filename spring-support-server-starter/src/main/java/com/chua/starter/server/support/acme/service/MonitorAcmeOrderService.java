package com.chua.starter.server.support.acme.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.chua.starter.server.support.acme.entity.MonitorAcmeOrder;

import java.util.List;

/**
 * ACME 订单服务
 *
 * <p>记录 ACME 订单(pending/ready/processing/valid/invalid)及其关联 challenge。</p>
 *
 * @since 4.0.0.42
 * @author CH
 */
public interface MonitorAcmeOrderService extends IService<MonitorAcmeOrder> {

    /**
     * 查询账户的所有订单。
     *
     * @param accountId 账户主键
     * @return 订单列表
     */
    List<MonitorAcmeOrder> listByAccountId(Integer accountId);

    /**
     * 查询关联到某证书的所有订单。
     *
     * @param certId 证书主键
     * @return 订单列表
     */
    List<MonitorAcmeOrder> listByCertId(Integer certId);

    /**
     * 按状态查询订单。
     *
     * @param status 订单状态
     * @return 订单列表
     */
    List<MonitorAcmeOrder> listByStatus(String status);

    /**
     * 按 ACME 订单 URL 查找订单(去重/幂等)。
     *
     * @param orderUrl ACME 服务下发的 订单 URL
     * @return 订单或 空
     */
    MonitorAcmeOrder getByOrderUrl(String orderUrl);

    /**
     * 保存订单。
     *
     * @param order 订单
     * @return 保存后的对象
     */
    MonitorAcmeOrder saveOrUpdateOrder(MonitorAcmeOrder order);

    /**
     * 更新订单状态。
     *
     * @param orderId   订单主键
     * @param newStatus 新状态
     * @return 是否成功
     */
    boolean updateStatus(Integer orderId, String newStatus);

    /**
     * 绑定订单到已签发的证书。
     *
     * @param orderId 订单主键
     * @param certId  证书主键
     * @return 是否成功
     */
    boolean bindCert(Integer orderId, Integer certId);

    /**
     * 删除订单。
     *
     * @param orderId 订单主键
     * @return 是否成功
     */
    boolean removeOrder(Integer orderId);

    /**
     * 统计订单总数。
     *
     * @return 数量
     */
    long countOrders();
}
