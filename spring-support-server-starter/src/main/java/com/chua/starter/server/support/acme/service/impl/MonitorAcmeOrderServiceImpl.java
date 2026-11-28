package com.chua.starter.server.support.acme.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.chua.starter.server.support.acme.entity.MonitorAcmeOrder;
import com.chua.starter.server.support.acme.mapper.MonitorAcmeOrderMapper;
import com.chua.starter.server.support.acme.service.MonitorAcmeOrderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import com.chua.common.support.utils.StringUtils;

/**
 * ACME 订单服务实现，提供订单按账户/证书/状态查询、幂等获取、保存、状态更新、证书绑定与删除能力。
 *
 * @since 4.0.0.42
 * @author CH
 */
@Service
public class MonitorAcmeOrderServiceImpl
        extends ServiceImpl<MonitorAcmeOrderMapper, MonitorAcmeOrder>
        implements MonitorAcmeOrderService {

    /**
     * 查询账户的所有订单。
     *
     * @param accountId 账户主键
     * @return 订单列表，账户为空时返回空列表
     */
    @Override
    public List<MonitorAcmeOrder> listByAccountId(Integer accountId) {
        if (accountId == null) {
            return Collections.emptyList();
        }
        return list(Wrappers.<MonitorAcmeOrder>lambdaQuery()
                .eq(MonitorAcmeOrder::getAcmeAccountId, accountId)
                .orderByDesc(MonitorAcmeOrder::getCreateTime));
    }

    /**
     * 查询关联到某证书的所有订单。
     *
     * @param certId 证书主键
     * @return 订单列表，证书为空时返回空列表
     */
    @Override
    public List<MonitorAcmeOrder> listByCertId(Integer certId) {
        if (certId == null) {
            return Collections.emptyList();
        }
        return list(Wrappers.<MonitorAcmeOrder>lambdaQuery()
                .eq(MonitorAcmeOrder::getAcmeCertId, certId));
    }

    /**
     * 按状态查询订单。
     *
     * @param status 订单状态
     * @return 订单列表，状态为空时返回空列表
     */
    @Override
    public List<MonitorAcmeOrder> listByStatus(String status) {
        if (StringUtils.isEmpty(status)) {
            return Collections.emptyList();
        }
        return list(Wrappers.<MonitorAcmeOrder>lambdaQuery()
                .eq(MonitorAcmeOrder::getAcmeOrderStatus, status));
    }

    /**
     * 按 ACME 订单 URL 查找订单（去重/幂等）。订单 URL 为空时返回 空。
     *
     * @param orderUrl ACME 服务下发的 订单 URL
     * @return 订单或 空
     */
    @Override
    public MonitorAcmeOrder getByOrderUrl(String orderUrl) {
        if (StringUtils.isEmpty(orderUrl)) {
            return null;
        }
        return getOne(Wrappers.<MonitorAcmeOrder>lambdaQuery()
                .eq(MonitorAcmeOrder::getAcmeOrderUrl, orderUrl)
                .last("LIMIT 1"));
    }

    /**
     * 保存订单：无主键时新增（回填创建/更新时间），有主键时更新。
     *
     * @param order 订单
     * @return 保存后的对象，参数为空或更新失败时返回 空
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MonitorAcmeOrder saveOrUpdateOrder(MonitorAcmeOrder order) {
        if (order == null) {
            return null;
        }
        if (order.getAcmeOrderId() == null) {
            order.setCreateTime(LocalDateTime.now());
            order.setUpdateTime(LocalDateTime.now());
            save(order);
            return order;
        }
        order.setUpdateTime(LocalDateTime.now());
        boolean ok = updateById(order);
        return ok ? order : null;
    }

    /**
     * 更新订单状态。
     *
     * @param orderId   订单主键
     * @param newStatus 新状态
     * @return 是否成功，参数为空时返回 false
     */
    @Override
    public boolean updateStatus(Integer orderId, String newStatus) {
        if (orderId == null || newStatus == null) {
            return false;
        }
        MonitorAcmeOrder update = new MonitorAcmeOrder();
        update.setAcmeOrderId(orderId);
        update.setAcmeOrderStatus(newStatus);
        update.setUpdateTime(LocalDateTime.now());
        return updateById(update);
    }

    /**
     * 绑定订单到已签发的证书。
     *
     * @param orderId 订单主键
     * @param certId  证书主键
     * @return 是否成功，参数为空时返回 false
     */
    @Override
    public boolean bindCert(Integer orderId, Integer certId) {
        if (orderId == null || certId == null) {
            return false;
        }
        MonitorAcmeOrder update = new MonitorAcmeOrder();
        update.setAcmeOrderId(orderId);
        update.setAcmeCertId(certId);
        update.setUpdateTime(LocalDateTime.now());
        return updateById(update);
    }

    /**
     * 删除订单。
     *
     * @param orderId 订单主键
     * @return 是否成功，参数为空时返回 false
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeOrder(Integer orderId) {
        if (orderId == null) {
            return false;
        }
        return removeById(orderId);
    }

    /**
     * 统计订单总数。
     *
     * @return 订单数量
     */
    @Override
    public long countOrders() {
        return count();
    }
}
