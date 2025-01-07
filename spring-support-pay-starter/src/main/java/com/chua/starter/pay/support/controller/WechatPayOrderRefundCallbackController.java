package com.chua.starter.pay.support.controller;

import com.chua.starter.pay.support.pojo.WechatOrderCallbackResponse;
import com.chua.starter.pay.support.pojo.WechatOrderRefundCallbackRequest;
import com.chua.starter.pay.support.service.PayOrderService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 退款回调
 * @author CH
 * @since 2024/12/30
 */
@Api(tags = "退款回调")
@Tag(name = "退款回调")
@RestController
@RequestMapping("/v3/pay/callback/wechat/refund")
@Slf4j
@RequiredArgsConstructor
public class WechatPayOrderRefundCallbackController {


    final PayOrderService payOrderService;

    @PostMapping(value = "/notify", produces = MediaType.APPLICATION_XML_VALUE)
    @ApiOperation("订单结果通知")
    public WechatOrderCallbackResponse refundOrder(@RequestBody WechatOrderRefundCallbackRequest wechatOrderRefundCallbackRequest) {
//        return payOrderService.refundOrder(wechatOrderRefundCallbackRequest, "wechat_" + wechatOrderRefundCallbackRequest.getTradeType());
        return null;
    }
}
