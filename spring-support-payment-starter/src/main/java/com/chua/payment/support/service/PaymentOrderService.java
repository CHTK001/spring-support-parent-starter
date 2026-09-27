package com.chua.payment.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.channel.PaymentResult;
import com.chua.payment.support.dto.OrderCreateDTO;
import com.chua.payment.support.dto.OrderPayDTO;
import com.chua.payment.support.dto.PaymentOrderPageRequest;
import com.chua.payment.support.dto.RefundApplyDTO;
import com.chua.payment.support.dto.RefundSuccessDTO;
import com.chua.payment.support.vo.OrderStateLogVO;
import com.chua.payment.support.vo.OrderVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 支付订单服务接口，定义订单创建、支付、退款、状态流转等核心操作契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface PaymentOrderService {

    /**
     * 创建支付订单。
     * @param dto dto
     * @return 创建支付订单的结果
     */
    OrderVO createOrder(OrderCreateDTO dto);

    /**
     * 获取订单。
     * @param id 标识
     * @return 获取到的订单
     */
    OrderVO getOrder(Long id);

    /**
     * 分页查询订单。
     * @param request 请求参数
     * @return 订单分页结果
     */
    Page<OrderVO> listOrders(PaymentOrderPageRequest request);

    /**
     * 分页查询商户订单。
     * @param merchantId 商户标识
     * @param page page
     * @param size 大小
     * @param status 状态
     * @return 商户订单分页结果
     */
    Page<OrderVO> listMerchantOrders(Long merchantId, int page, int size, String status);

    /**
     * 查询订单状态日志。
     * @param id 标识
     * @return 订单状态日志列表
     */
    List<OrderStateLogVO> listOrderLogs(Long id);

    /**
     * 发起支付。
     * @param id 标识
     * @param dto dto
     * @return 发起支付的结果
     */
    PaymentResult payOrder(Long id, OrderPayDTO dto);

    /**
     * 同步订单状态。
     * @param id 标识
     * @return 同步后的订单
     */
    OrderVO syncOrder(Long id);

    /**
     * 启动支付。
     * @param id 标识
     * @param operator 操作人
     * @return 是否启动成功
     */
    boolean startPay(Long id, String operator);

    /**
     * 支付成功。
     * @param id 标识
     * @param paidAmount 实付金额
     * @param thirdPartyOrderNo 第三方订单号
     * @param operator 操作人
     * @return 是否处理成功
     */
    boolean paySuccess(Long id, BigDecimal paidAmount, String thirdPartyOrderNo, String operator);

    /**
     * 支付失败。
     * @param id 标识
     * @param reason 失败原因
     * @param operator 操作人
     * @return 是否处理成功
     */
    boolean payFail(Long id, String reason, String operator);

    /**
     * 取消订单
     *
     * @param refund true=退款取消（已支付订单，标记为已取消）；false=不退款取消（未支付/支付中，可直接取消）
     * @param id 标识
     * @param operator 操作人
     * @param reason 取消原因
     * @return 取消订单的结果
     */
    boolean cancelOrder(Long id, String operator, String reason, boolean refund);

    /**
     * 取消订单。
     * @param id 标识
     * @param operator 操作人
     * @param reason 取消原因
     * @return 取消订单的结果
     */
    boolean cancelOrder(Long id, String operator, String reason);

    /**
     * 完成订单。
     * @param id 标识
     * @param operator 操作人
     * @return 是否完成成功
     */
    boolean completeOrder(Long id, String operator);

    /**
     * 发起退款。
     * @param id 标识
     * @param dto dto
     * @return 发起退款的结果
     */
    boolean refundOrder(Long id, RefundApplyDTO dto);

    /**
     * 退款成功。
     * @param id 标识
     * @param refundAmount 退款金额
     * @param operator 操作人
     * @return 是否处理成功
     */
    boolean refundSuccess(Long id, BigDecimal refundAmount, String operator);

    /**
     * 退款成功。
     * @param dto dto
     * @return 是否处理成功
     */
    boolean refundSuccess(RefundSuccessDTO dto);

    /**
     * 退款失败。
     * @param id 标识
     * @param reason 失败原因
     * @param operator 操作人
     * @return 是否处理成功
     */
    boolean refundFail(Long id, String reason, String operator);

    /**
     * 退款失败。
     * @param refundNo 退款单号
     * @param responsePayload 响应报文
     * @param operator 操作人
     * @param reason 失败原因
     * @return 是否处理成功
     */
    boolean refundFail(String refundNo, String responsePayload, String operator, String reason);

    /**
     * 删除订单。
     * @param id 标识
     * @return 是否删除成功
     */
    boolean deleteOrder(Long id);

    /**
     * 生成订单号。
     * @return 生成的订单号
     */
    String generateOrderNo();

    /**
     * 超时关单（发送超时关闭事件，由状态机处理）。
     * @param id 标识
     */
    void timeoutOrder(Long id);
}
