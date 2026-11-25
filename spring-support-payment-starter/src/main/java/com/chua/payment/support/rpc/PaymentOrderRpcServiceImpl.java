package com.chua.payment.support.rpc;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.network.rpc.RpcService;
import com.chua.payment.support.channel.PaymentResult;
import com.chua.payment.support.dto.OrderCreateDTO;
import com.chua.payment.support.dto.OrderPayDTO;
import com.chua.payment.support.dto.PaymentOrderPageRequest;
import com.chua.payment.support.dto.RefundApplyDTO;
import com.chua.payment.support.service.PaymentOrderService;
import com.chua.payment.support.vo.OrderStateLogVO;
import com.chua.payment.support.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 支付订单 RPC 提供者：把 {@link PaymentOrderService} 的核心能力委托暴露为
 * {@link PaymentOrderRpcService} 契约。
 *
 * <p>注册与发现规约：<br>
 * 1. 仅在 {@code plugin.rpc.enable=true} 时对外注册（默认关闭）；<br>
 * 2. 扫描包路径 {@code plugin.rpc.scan}（缺省扫描应用入口类所在包，需覆盖
 * {@code com.chua.payment.support.rpc}）；<br>
 * 3. 消费者侧用 {@code @RpcResource(version = "1.0.0", degrade = "springbean")} 注入，
 * RPC 层（Dubbo）不可用时自动降级到本应用的本地 Bean。</p>
 *
 * <p>本类只做参数透传与结果包装，不实现任何订单业务规则；业务归属
 * {@code PaymentOrderService} 及其下游（R1：可部署宿主不重复实现 starter 业务）。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@Service
@RequiredArgsConstructor
@RpcService(interfaceClass = PaymentOrderRpcService.class, version = "1.0.0")
public class PaymentOrderRpcServiceImpl implements PaymentOrderRpcService {

    private final PaymentOrderService paymentOrderService;

    @Override
    public OrderVO createOrder(OrderCreateDTO dto) {
        return paymentOrderService.createOrder(dto);
    }

    @Override
    public OrderVO getOrder(Long id) {
        return paymentOrderService.getOrder(id);
    }

    @Override
    public Page<OrderVO> listOrders(PaymentOrderPageRequest request) {
        return paymentOrderService.listOrders(request);
    }

    @Override
    public PaymentResult payOrder(Long id, OrderPayDTO dto) {
        return paymentOrderService.payOrder(id, dto);
    }

    @Override
    public boolean paySuccess(Long id, BigDecimal paidAmount, String thirdPartyOrderNo, String operator) {
        return paymentOrderService.paySuccess(id, paidAmount, thirdPartyOrderNo, operator);
    }

    @Override
    public boolean payFail(Long id, String reason, String operator) {
        return paymentOrderService.payFail(id, reason, operator);
    }

    @Override
    public boolean cancelOrder(Long id, String operator, String reason) {
        return paymentOrderService.cancelOrder(id, operator, reason);
    }

    @Override
    public boolean refundOrder(Long id, RefundApplyDTO dto) {
        return paymentOrderService.refundOrder(id, dto);
    }

    @Override
    public boolean refundSuccess(Long id, BigDecimal refundAmount, String operator) {
        return paymentOrderService.refundSuccess(id, refundAmount, operator);
    }

    @Override
    public boolean refundFail(Long id, String reason, String operator) {
        return paymentOrderService.refundFail(id, reason, operator);
    }

    @Override
    public List<OrderStateLogVO> listOrderLogs(Long id) {
        return paymentOrderService.listOrderLogs(id);
    }

    @Override
    public String generateOrderNo() {
        return paymentOrderService.generateOrderNo();
    }
}
