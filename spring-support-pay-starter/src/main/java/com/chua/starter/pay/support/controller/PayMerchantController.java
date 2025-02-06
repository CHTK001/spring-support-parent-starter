package com.chua.starter.pay.support.controller;

import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.ReturnPageResultUtils;
import com.chua.starter.pay.support.entity.PayMerchant;
import com.chua.starter.pay.support.service.PayMerchantService;
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
@Api(tags = "商户接口")
@Tag(name = "商户接口")
@RestController
@RequestMapping("/v3/pay/merchant")
@Slf4j
@RequiredArgsConstructor
public class PayMerchantController {

    private final PayMerchantService payMerchantService;



    /**
     * 分页查询商户
     * @param query 查询条件
     * @return 商户列表
     */
    @GetMapping("page")
    @Operation(summary = "分页查询商户")
    public ReturnPageResult<PayMerchant> page(@ParameterObject Query<PayMerchant> query, @ParameterObject PayMerchant payMerchant) {
        return ReturnPageResultUtils.ok(payMerchantService.pageForMerchant(query, payMerchant));
    }

    /**
     * 删除商户
     * @param merchantId 商户id
     * @return 删除结果
     */
    @Operation(summary = "删除商户")
    @DeleteMapping("delete")
    public ReturnResult<Boolean> delete(Integer merchantId) {
        return payMerchantService.deletePayMerchant(merchantId);
    }

    /**
     * 保存商户
     * @param payMerchant 商户
     * @return 保存结果
     */
    @PostMapping("save")
    @Operation(summary = "新增商户")
    public ReturnResult<PayMerchant> save(@Validated(AddGroup.class) @RequestBody PayMerchant payMerchant, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return ReturnResult.ok(payMerchantService.savePayMerchant(payMerchant));
    }


    /**
     * 修改商户
     * @param payMerchant 商户
     * @return 修改结果
     */
    @PutMapping("update")
    @Operation(summary = "修改商户")
    public ReturnResult<Boolean> update(@Validated(UpdateGroup.class) @RequestBody PayMerchant payMerchant, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().getFirst().getDefaultMessage());
        }
        return payMerchantService.updatePayMerchant(payMerchant);
    }
}
