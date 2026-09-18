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
    * 创建退税订单。
    * @param dto dto
    * @return 创建退税订单的结果
    */
    PaymentRefundOrder createRefundOrder(CreateRefundOrderDTO dto);

    /**
    * 获取byid。
    * @param id 标识
    * @return 获取byid的结果
    */
    PaymentRefundOrder getById(Long id);

    /**
    * 获取by退税no。
    * @param refundNo 退税no
    * @return 获取by退税no的结果
    */
    PaymentRefundOrder getByRefundNo(String refundNo);

    /**
    * 获取bythirdparty退税no。
    * @param thirdPartyRefundNo thirdparty退税no
    * @return 获取bythirdparty退税no的结果
    */
    PaymentRefundOrder getByThirdPartyRefundNo(String thirdPartyRefundNo);

    /**
    * 获取detail。
    * @param id 标识
    * @return 获取detail的结果
    */
    RefundOrderVO getDetail(Long id);

    /**
    * page。
    * @param request 请求
    * @return page的结果
    */
    Page<RefundOrderVO> page(RefundOrderQueryRequest request);

    /**
    * 获取latestby订单标识。
    * @param orderId 订单标识
    * @return 获取latestby订单id的结果
    */
    PaymentRefundOrder getLatestByOrderId(Long orderId);

    /**
    * 获取处理by订单标识。
    * @param orderId 订单标识
    * @return 获取处理by订单id的结果
    */
    PaymentRefundOrder getProcessingByOrderId(Long orderId);

    /**
    * 列表by订单标识。
    * @param orderId 订单标识
    * @return 列表by订单id的结果
    */
    List<PaymentRefundOrder> listByOrderId(Long orderId);

    /**
    * 列表voby订单标识。
    * @param orderId 订单标识
    * @return 列表voby订单id的结果
    */
    List<RefundOrderVO> listVoByOrderId(Long orderId);

    /**
    * 是否包含处理退税。
    * @param orderId 订单标识
    * @return 是否包含处理退税的结果
    */
    boolean hasProcessingRefund(Long orderId);

    /**
    * sum退税amount。
    * @param orderId 订单标识
    * @return sum退税amount的结果
    */
    BigDecimal sumRefundedAmount(Long orderId);

    PaymentRefundOrder markProcessing(String refundNo,
                                      String thirdPartyRefundNo,
                                      String responsePayload,
                                      String remark);

    /**
    * mark退税。
    * @param dto dto
    * @return mark退税的结果
    */
    PaymentRefundOrder markRefunded(MarkRefundedDTO dto);

    PaymentRefundOrder markFailed(String refundNo,
                                  String responsePayload,
                                  String operator,
                                  String remark);
}

