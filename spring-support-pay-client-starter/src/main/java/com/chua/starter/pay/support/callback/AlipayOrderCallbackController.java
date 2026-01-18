package com.chua.starter.pay.support.callback;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.chua.starter.pay.support.entity.PayMerchantConfigAlipay;
import com.chua.starter.pay.support.entity.PayMerchantOrder;
import com.chua.starter.pay.support.enums.PayOrderStatus;
import com.chua.starter.pay.support.pojo.PayMerchantConfigAlipayWrapper;
import com.chua.starter.pay.support.service.PayMerchantConfigAlipayService;
import com.chua.starter.pay.support.service.PayMerchantFailureRecordService;
import com.chua.starter.pay.support.service.PayMerchantOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 支付宝支付回调控制器
 *
 * @author CH
 * @since 2025/10/15 18:00
 */
@Tag(name = "支付宝支付回调(后端内部使用)")
@RestController
@RequestMapping("/v2/pay/callback/alipay/order")
@Slf4j
@RequiredArgsConstructor
public class AlipayOrderCallbackController {

    private final PayMerchantOrderService payMerchantOrderService;
    private final PayMerchantConfigAlipayService payMerchantConfigAlipayService;
    private final PayMerchantFailureRecordService payMerchantFailureRecordService;

    /**
     * 支付宝支付订单结果通知
     *
     * @param request        请求对象
     * @param payMerchantCode 订单编号
     * @return 响应结果
     */
    @PostMapping(value = "/{orderCode}")
    @Operation(summary = "订单结果通知")
    public ResponseEntity<String> notifyOrder(
            HttpServletRequest request,
            @PathVariable("orderCode") String payMerchantCode
    ) {
        log.info("[支付][回调]支付宝订单结果通知，订单编号: {}", payMerchantCode);
        try {
            PayMerchantOrder merchantOrder = payMerchantOrderService.getByCode(payMerchantCode);
            if (merchantOrder == null) {
                log.error("[支付][回调]订单不存在，订单编号: {}", payMerchantCode);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("fail");
            }

            PayMerchantConfigAlipayWrapper configWrapper = payMerchantConfigAlipayService.getByCodeForPayMerchantConfigAlipay(
                    merchantOrder.getPayMerchantId(),
                    merchantOrder.getPayMerchantTradeType().getName()
            );
            if (!configWrapper.hasConfig()) {
                log.error("[支付][回调]商户未开启配置，订单编号: {}", payMerchantCode);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("fail");
            }

            PayMerchantConfigAlipay config = configWrapper.getPayMerchantConfigAlipay();
            Map<String, String> params = getRequestParams(request);

            // 验证签名
            boolean signVerified = verifySign(params, config);
            if (!signVerified) {
                log.error("[支付][回调]签名验证失败，订单编号: {}", payMerchantCode);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("fail");
            }

            // 处理订单状态
            String tradeStatus = params.get("trade_status");
            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                merchantOrder.setPayMerchantOrderStatus(PayOrderStatus.PAY_SUCCESS);
                merchantOrder.setPayMerchantOrderTransactionId(params.get("trade_no"));
                merchantOrder.setPayMerchantOrderPayTime(LocalDateTime.now());
                merchantOrder.setPayMerchantOrderFinishedTime(LocalDateTime.now());
                payMerchantOrderService.finishWechatOrder(merchantOrder);
                log.info("[支付][回调]订单支付成功，订单编号: {}", payMerchantCode);
            }

            return ResponseEntity.ok("success");
        } catch (Exception e) {
            log.error("[支付][回调]处理支付宝回调异常，订单编号: {}", payMerchantCode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("fail");
        }
    }

    /**
     * 获取请求参数
     *
     * @param request HTTP请求
     * @return 参数Map
     */
    private Map<String, String> getRequestParams(HttpServletRequest request) {
        Map<String, String> params = new java.util.HashMap<>();
        Map<String, String[]> requestParams = request.getParameterMap();
        for (String name : requestParams.keySet()) {
            String[] values = requestParams.get(name);
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i] : valueStr + values[i] + ",";
            }
            params.put(name, valueStr);
        }
        return params;
    }

    /**
     * 验证签名
     *
     * @param params 参数
     * @param config 配置
     * @return 是否验证通过
     */
    private boolean verifySign(Map<String, String> params, PayMerchantConfigAlipay config) {
        try {
            String signType = config.getPayMerchantConfigAlipaySignType();
            if (signType == null || signType.isEmpty()) {
                signType = "RSA2";
            }
            String charset = config.getPayMerchantConfigAlipayCharset();
            if (charset == null || charset.isEmpty()) {
                charset = "UTF-8";
            }
            return AlipaySignature.rsaCheckV1(
                    params,
                    config.getPayMerchantConfigAlipayPublicKey(),
                    charset,
                    signType
            );
        } catch (AlipayApiException e) {
            log.error("[支付][回调]签名验证异常", e);
            return false;
        }
    }
}

