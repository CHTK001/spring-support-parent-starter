package com.chua.starter.pay.support.controller;

import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.validator.group.SaveGroup;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.ReturnPageResultUtils;
import com.chua.starter.pay.support.entity.PayMerchant;
import com.chua.starter.pay.support.pojo.PayMerchantWrapper;
import com.chua.starter.pay.support.service.PayMerchantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 *
 * 商户接口
 * @author CH
 * @since 2025/10/14 11:34
 */
@RestController
@RequestMapping("/v2/pay/merchant")
@Tag(name = "商户接口")
@RequiredArgsConstructor
public class PayMerchantController {

    private final PayMerchantService payMerchantService;

    /**
     * 根据商户编号查询商户信息
     * @param payMerchantCode 商户编号
     * @return 商户信息
     */
    @GetMapping("code")
    @Operation(summary = "根据商户编号查询商户信息")
    public ReturnResult<PayMerchant> getByCode(Integer payMerchantCode) {
        return ReturnResult.ok(payMerchantService.getByCodeForPayMerchantCode(payMerchantCode));
    }


    /**
     * 分页查询商户信息
     * @param page 分页参数
     * @param entity 查询参数
     * @return 商户信息
     */
    @GetMapping("page")
    @Operation(summary = "分页查询商户信息")
    public ReturnPageResult<PayMerchant> page(Query<PayMerchant> page, PayMerchant entity) {
        return ReturnPageResultUtils.ok(payMerchantService.pageForMerchant(page, entity));
    }


    /**
     * 保存商户信息
     * @param entity 商户信息
     * @return 是否成功
     */
    @PostMapping("save")
    @Operation(summary = "保存商户信息")
    public ReturnResult<Boolean> saveForMerchant(@Validated(SaveGroup.class) @RequestBody PayMerchant entity, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return ReturnResult.ok(payMerchantService.saveForPayMerchant(entity));
    }


    @PutMapping("update")
    @Operation(summary = "更新商户信息")
    public ReturnResult<Boolean> updateForMerchant(@Validated(SaveGroup.class) @RequestBody PayMerchant entity, BindingResult bindingResult) {
        if(bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return ReturnResult.ok(payMerchantService.updateForPayMerchant(entity));
    }
}

