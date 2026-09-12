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
     * 创建订单。
     * @param dto dto
     * @return 创建订单的结果
     */
    OrderVO createOrder(OrderCreateDTO dto);

    /**
     * 获取订单。
     * @param id 标识
     * @return 获取订单的结果
     */
    OrderVO getOrder(Long id);

    /**
     * 列表订单。
     * @param request 请求
     * @return 列表订单的结果
     */
    Page<OrderVO> listOrders(PaymentOrderPageRequest request);

    /**
     * 列表merchant订单。
     * @param merchantId merchantid
     * @param page page
     * @param size 大小
     * @param status 状态
     * @return 列表merchant订单的结果
     */
    Page<OrderVO> listMerchantOrders(Long merchantId, int page, int size, String status);

    /**
     * 列表订单日志。
     * @param id 标识
     * @return 列表订单日志的结果
     */
    List<OrderStateLogVO> listOrderLogs(Long id);

    /**
     * 薪酬订单。
     * @param id 标识
     * @param dto dto
     * @return 薪酬订单的结果
     */
    PaymentResult payOrder(Long id, OrderPayDTO dto);

    /**
     * 同步订单。
     * @param id 标识
     * @return 同步订单的结果
     */
    OrderVO syncOrder(Long id);

    /**
     * 启动薪酬。
     * @param id 标识
     * @param operator 操作符
     * @return 启动薪酬的结果
     */
    boolean startPay(Long id, String operator);

    /**
     * 薪酬成功。
     * @param id 标识
     * @param paidAmount paidamount
     * @param thirdPartyOrderNo thirdparty订单no
     * @param operator 操作符
     * @return 薪酬成功的结果
     */
    boolean paySuccess(Long id, BigDecimal paidAmount, String thirdPartyOrderNo, String operator);

    /**
     * 薪酬失败。
     * @param id 标识
     * @param reason ReasonMLmlml
     * @param operator 操作符
     * @return 薪酬失败的结果
     */
    boolean payFail(Long id, String reason, String operator);

    /**
     * 取消订单
     *
     * @param refund true=退款取消（已支付订单退款+标取消），false=不退款取消（仅 PENDING/薪酬 可取消）
     * @param id 标识
     * @param operator 操作符
     * @param reason ReasonMLmlml
     * @return cancel订单的结果
     */
    boolean cancelOrder(Long id, String operator, String reason, boolean refund);

    /**
     * cancel订单。
     * @param id 标识
     * @param operator 操作符
     * @param reason ReasonMLmlml
     * @return cancel订单的结果
     */
    boolean cancelOrder(Long id, String operator, String reason);

    /**
     * 完成订单。
     * @param id 标识
     * @param operator 操作符
     * @return 完成订单的结果
     */
    boolean completeOrder(Long id, String operator);

    /**
     * 退税订单。
     * @param id 标识
     * @param dto dto
     * @return 退税订单的结果
     */
    boolean refundOrder(Long id, RefundApplyDTO dto);

    /**
     * 退税成功。
     * @param id 标识
     * @param refundAmount 退税amount
     * @param operator 操作符
     * @return 退税成功的结果
     */
    boolean refundSuccess(Long id, BigDecimal refundAmount, String operator);

    /**
     * 退税成功。
     * @param dto dto
     * @return 退税成功的结果
     */
    boolean refundSuccess(RefundSuccessDTO dto);

    /**
     * 退税失败。
     * @param id 标识
     * @param reason ReasonMLmlml
     * @param operator 操作符
     * @return 退税失败的结果
     */
    boolean refundFail(Long id, String reason, String operator);

    /**
     * 退税失败。
     * @param refundNo 退税no
     * @param responsePayload 响应payload
     * @param operator 操作符
     * @param reason ReasonMLmlml
     * @return 退税失败的结果
     */
    boolean refundFail(String refundNo, String responsePayload, String operator, String reason);

    /**
     * 删除订单。
     * @param id 标识
     * @return 删除订单的结果
     */
    boolean deleteOrder(Long id);

    /**
     * generate订单no。
     * @return generate订单no的结果
     */
    String generateOrderNo();

    /**
      * 超时关单（发送 超时 事件 via 状态机）
     * @param id 标识
     */
    void timeoutOrder(Long id);
}

