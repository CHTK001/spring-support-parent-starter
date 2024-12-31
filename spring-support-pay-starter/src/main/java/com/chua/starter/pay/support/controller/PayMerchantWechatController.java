package com.chua.starter.pay.support.controller;

import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.ReturnPageResultUtils;
import com.chua.starter.pay.support.entity.PayMerchant;
import com.chua.starter.pay.support.entity.PayMerchantConfigWechat;
import com.chua.starter.pay.support.service.PayMerchantConfigWechatService;
import io.swagger.annotations.Api;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 商户接口
 * @author CH
 * @since 2024/12/30
 */
@Api(tags = "商户微信支付接口")
@Tag(name = "商户微信支付接口")
@RestController
@RequestMapping("/v2/pay/merchant/wechat")
@Slf4j
@RequiredArgsConstructor
public class PayMerchantWechatController {

    private final PayMerchantConfigWechatService payMerchantConfigWechatService;



    /**
     * 删除商户
     * @param merchantId 商户id
     * @return 删除结果
     */
    @Operation(summary = "删除商户微信设置")
    @DeleteMapping("delete")
    public ReturnResult<Boolean> delete(Integer payMerchantConfigWechatId) {
        return ReturnResult.ok(payMerchantConfigWechatService.removeById(payMerchantConfigWechatId));
    }

    /**
     * 保存商户
     * @param payMerchantConfigWechat 商户
     * @return 保存结果
     */
    @PostMapping("save")
    @Operation(summary = "新增商户微信设置")
    public ReturnResult<PayMerchantConfigWechat> save(@Validated(AddGroup.class) @RequestBody PayMerchantConfigWechat payMerchantConfigWechat, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        return payMerchantConfigWechatService.savePayMerchantConfigWechat(payMerchantConfigWechat);
    }


    /**
     * 修改商户
     * @param payMerchantConfigWechat 商户
     * @return 修改结果
     */
    @PutMapping("update")
    @Operation(summary = "修改商户微信设置")
    public ReturnResult<Boolean> update(@Validated(UpdateGroup.class) @RequestBody PayMerchantConfigWechat payMerchantConfigWechat, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        return payMerchantConfigWechatService.updatePayMerchantConfigWechat(payMerchantConfigWechat);
    }
}