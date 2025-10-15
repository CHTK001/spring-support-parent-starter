package com.chua.starter.pay.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.starter.common.support.annotations.Permission;
import com.chua.starter.pay.support.pojo.CreateTransferV2Response;
import com.chua.starter.pay.support.pojo.CreateTransferV2Request;
import com.chua.starter.pay.support.service.PayMerchantTransferRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.chua.starter.oauth.client.support.contants.AuthConstant.ADMIN;
import static com.chua.starter.oauth.client.support.contants.AuthConstant.SUPER_ADMIN;

/**
 *
 * 转账接口
 * @author CH
 * @since 2025/10/14 11:34
 */
@RestController
@RequestMapping("/v2/transfer")
@Tag(name = "转账接口")
@RequiredArgsConstructor
public class TransferController {


    final PayMerchantTransferRecordService payMerchantTransferRecordService;

    /**
     * 转账
     * @param request 转账参数
     * @return 转账结果
     */
    @PutMapping("transfer")
    @Operation(summary = "转账")
    @Permission(value = "sys:pay:transfer", role = {ADMIN, SUPER_ADMIN})
    public ReturnResult<CreateTransferV2Response> transfer(@Validated(AddGroup.class) @RequestBody CreateTransferV2Request request, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getFieldError().getDefaultMessage());
        }
        return payMerchantTransferRecordService.transfer(request);
    }
}
