package com.chua.payment.support.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.dto.CreateRefundOrderDTO;
import com.chua.payment.support.dto.MarkRefundedDTO;
import com.chua.payment.support.dto.RefundOrderQueryRequest;
import com.chua.payment.support.entity.PaymentOrder;
import com.chua.payment.support.entity.PaymentRefundOrder;
import com.chua.payment.support.vo.RefundOrderVO;

import java.math.BigDecimal;
import java.util.List;

/**
 * 退款单服务接口，定义退款单的创建、状态更新与查询操作契约。
 *
 * @author CH
 * @since 4.0.0.50
 */
public interface PaymentRefundOrderService {

    /**
     * 创建退款订单。
     * @param dto dto
     * @return 创建出的退款订单
     */
    PaymentRefundOrder createRefundOrder(CreateRefundOrderDTO dto);

    /**
     * 根据主键获取退款单。
     * @param id 标识
     * @return 获取到的退款单
     */
    PaymentRefundOrder getById(Long id);

    /**
     * 根据退款单号获取退款单。
     * @param refundNo 退款单号
     * @return 获取到的退款单
     */
    PaymentRefundOrder getByRefundNo(String refundNo);

    /**
     * 根据第三方退款单号获取退款单。
     * @param thirdPartyRefundNo 第三方退款单号
     * @return 获取到的退款单
     */
    PaymentRefundOrder getByThirdPartyRefundNo(String thirdPartyRefundNo);

    /**
     * 获取退款单详情。
     * @param id 标识
     * @return 退款单详情
     */
    RefundOrderVO getDetail(Long id);

    /**
     * 分页查询退款单。
     * @param request 请求参数
     */
    Page<RefundOrderVO> page(RefundOrderQueryRequest request);

    /**
     * 获取订单最新的一笔退款单。
     * @param orderId 订单标识
     * @return 订单最新的一笔退款单
     */
    PaymentRefundOrder getLatestByOrderId(Long orderId);

    /**
     * 获取订单处理中的退款单。
     * @param orderId 订单标识
     * @return 订单处理中的退款单
     */
    PaymentRefundOrder getProcessingByOrderId(Long orderId);

    /**
     * 按订单标识查询退款单列表。
     * @param orderId 订单标识
     * @return 按订单标识查询到的退款单列表
     */
    List<PaymentRefundOrder> listByOrderId(Long orderId);

    /**
     * 按订单标识查询退款单视图列表。
     * @param orderId 订单标识
     * @return 按订单标识查询到的退款单视图列表
     */
    List<RefundOrderVO> listVoByOrderId(Long orderId);

    /**
     * 是否存在处理中的退款单。
     * @param orderId 订单标识
     * @return 是否存在处理中的退款单
     */
    boolean hasProcessingRefund(Long orderId);

    /**
     * 汇总订单已退款金额。
     * @param orderId 订单标识
     * @return 汇总后的已退款金额
     */
    BigDecimal sumRefundedAmount(Long orderId);

    PaymentRefundOrder markProcessing(String refundNo,
                                      String thirdPartyRefundNo,
                                      String responsePayload,
                                      String remark);

    /**
     * 标记退款单为已退款。
     * @param dto dto
     * @return 标记后的退款单
     */
    PaymentRefundOrder markRefunded(MarkRefundedDTO dto);

    PaymentRefundOrder markFailed(String refundNo,
                                  String responsePayload,
                                  String operator,
                                  String remark);
}
