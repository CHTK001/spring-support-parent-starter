package com.chua.starter.pay.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.core.utils.ServiceProvider;
import com.chua.common.support.base.validator.group.SelectGroup;
import com.chua.starter.pay.support.pojo.TradebillV2Request;
import com.chua.starter.pay.support.pojo.TradebillV2Response;
import com.chua.starter.pay.support.tradebill.TradebillAdaptor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 交易账单
 * @author CH
 * @since 2025/10/15 16:15
 */
@RestController
@RequestMapping("/v2/pay/tradebill")
@Tag(name = "交易账单")
@RequiredArgsConstructor
public class PayTradebillController {

    /**
     * 交易账单
     * @param request 请求参数
     * @return 账单信息
     */
    @PutMapping("download")
    @Operation(summary = "交易账单")
    public ReturnResult<TradebillV2Response> tradebill(@Validated(SelectGroup.class) TradebillV2Request request, BindingResult bindingResult) {
        return ServiceProvider.of(TradebillAdaptor.class).getNewExtension(request.getPayTradeType()).download(request);
    }
}
