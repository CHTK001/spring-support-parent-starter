package com.chua.starter.pay.support.controller;

import com.chua.common.support.lang.code.ReturnPageResult;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.common.support.validator.group.AddGroup;
import com.chua.common.support.validator.group.UpdateGroup;
import com.chua.starter.mybatis.entity.Query;
import com.chua.starter.mybatis.utils.ReturnPageResultUtils;
import com.chua.starter.pay.support.entity.PayMerchant;
import com.chua.starter.pay.support.entity.PayMerchantGoods;
import com.chua.starter.pay.support.service.PayMerchantGoodsService;
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
 * 商品接口
 * @author CH
 * @since 2024/12/30
 */
@Api(tags = "商品商品接口")
@Tag(name = "商品商品接口")
@RestController
@RequestMapping("/v3/pay/merchant/goods")
@Slf4j
@RequiredArgsConstructor
public class PayMerchantGoodsController {

    private final PayMerchantGoodsService payMerchantGoodsService;



    /**
     * 分页查询商品
     * @param query 查询条件
     * @return 商品列表
     */
    @GetMapping("page")
    @Operation(summary = "分页查询商品")
    public ReturnPageResult<PayMerchantGoods> page(@ParameterObject Query<PayMerchantGoods> query) {
        return ReturnPageResultUtils.ok(payMerchantGoodsService.pageForGoods(query));
    }

    /**
     * 删除商品
     * @param goodsId 商品id
     * @return 删除结果
     */
    @Operation(summary = "删除商品")
    @DeleteMapping("delete")
    public ReturnResult<Boolean> delete(Integer goodsId) {
        return payMerchantGoodsService.deletePayGoods(goodsId);
    }

    /**
     * 保存商品
     * @param payMerchantGoods 商品
     * @return 保存结果
     */
    @PostMapping("save")
    @Operation(summary = "新增商品")
    public ReturnResult<PayMerchantGoods> save(@Validated(AddGroup.class) @RequestBody PayMerchantGoods payMerchantGoods, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        return ReturnResult.ok(payMerchantGoodsService.savePayGoods(payMerchantGoods));
    }


    /**
     * 修改商品
     * @param payMerchantGoods 商品
     * @return 修改结果
     */
    @PutMapping("update")
    @Operation(summary = "修改商品")
    public ReturnResult<Boolean> update(@Validated(UpdateGroup.class) @RequestBody PayMerchantGoods payMerchantGoods, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ReturnResult.illegal(bindingResult.getAllErrors().get(0).getDefaultMessage());
        }
        return payMerchantGoodsService.updatePayGoods(payMerchantGoods);
    }
}
