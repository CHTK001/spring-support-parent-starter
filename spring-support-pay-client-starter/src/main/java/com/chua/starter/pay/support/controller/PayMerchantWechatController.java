package com.chua.starter.pay.support.controller;

import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.base.validator.group.SaveGroup;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.ReturnPageResultUtils;
import com.chua.starter.pay.support.entity.PayMerchant;
import com.chua.starter.pay.support.entity.PayMerchantConfigWechat;
import com.chua.starter.pay.support.service.PayMerchantConfigWechatService;
import com.chua.starter.pay.support.service.PayMerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 *
 * 商户接口 - 微信配置
 * @author CH
 * @since 2025/10/14 11:34
 */
@RestController
@RequestMapping("/v2/pay/merchant/wechat")
@Tag(name = "商户管理 - 微信配置")
@RequiredArgsConstructor
public class PayMerchantWechatController {

    private final PayMerchantConfigWechatService payMerchantConfigWechatService;

    /**
     * 商户微信配置详情
     * @param payMerchantConfigWechatId 商户ID
     * @return 商户详情
     */
    @GetMapping("{payMerchantId}/{payMerchantConfigWechatId}")
    @Operation(summary = "商户微信配置详情")
    public ReturnResult<PayMerchantConfigWechat> detail(@PathVariable Integer payMerchantId, @PathVariable Integer payMerchantConfigWechatId) {
        return ReturnResult.ok(payMerchantConfigWechatService.detail(payMerchantId, payMerchantConfigWechatId));
    }


    /**
     * 保存商户微信配置
     * @param payMerchantConfigWechat 商户微信配置
     * @return 是否成功
     */
    @PostMapping("saveOrUpdate")
    @Operation(summary = "保存商户微信配置")
    public ReturnResult<Boolean> saveOrUpdate(@Validated(SaveGroup.class) @RequestBody PayMerchantConfigWechat payMerchantConfigWechat, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }

        if(null != payMerchantConfigWechat.getPayMerchantConfigWechatId()) {
            return ReturnResult.ok(payMerchantConfigWechatService.updateForPayMerchantConfigWechat(payMerchantConfigWechat) != null);
        }
        return ReturnResult.ok(payMerchantConfigWechatService.saveForPayMerchantConfigWechat(payMerchantConfigWechat));
    }
}

