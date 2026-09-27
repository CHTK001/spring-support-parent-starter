package com.chua.payment.support.controller;

import com.chua.payment.support.common.Result;
import com.chua.payment.support.dto.MerchantPaymentConfigDTO;
import com.chua.payment.support.dto.MerchantWalletLimitDTO;
import com.chua.payment.support.entity.MerchantPaymentConfig;
import com.chua.payment.support.entity.MerchantWalletLimit;
import com.chua.payment.support.service.MerchantPaymentConfigService;
import com.chua.payment.support.service.MerchantService;
import com.chua.payment.support.service.WalletLimitService;
import com.chua.payment.support.vo.MerchantPaymentConfigVO;
import com.chua.payment.support.vo.MerchantWalletLimitVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商户支付设置控制器
 * @author CH
 * @since 4.0.0
 */
@Tag(name = "商户支付设置", description = "商户支付规则、钱包限额等设置接口")
@RestController
@RequestMapping("/merchant")
@RequiredArgsConstructor
public class MerchantSettingController {

    /**
     * 商户服务
     */
    private final MerchantService merchantService;
    /**
     * 商户支付配置服务
     */
    private final MerchantPaymentConfigService merchantPaymentConfigService;
    /**
     * 钱包限额服务
     */
    private final WalletLimitService walletLimitService;

    @Operation(summary = "查询商户支付配置")
    @GetMapping("/{merchantId}/payment-config")
    /**
     * 获取商户支付配置
     *
     * @param merchantId merchantid
     * @return 获取商户支付配置的结果
     */
    public Result<MerchantPaymentConfigVO> getPaymentConfig(@PathVariable Long merchantId) {
        merchantService.getMerchant(merchantId);
        return Result.success(toPaymentConfigVO(merchantId, merchantPaymentConfigService.getConfig(merchantId)));
    }

    @Operation(summary = "保存商户支付配置")
    @PutMapping("/{merchantId}/payment-config")
    /**
     * 保存商户支付配置
     * @param merchantId merchantid
     * @param dto dto
     */
    public Result<MerchantPaymentConfigVO> savePaymentConfig(@PathVariable Long merchantId,
                                                             @RequestBody MerchantPaymentConfigDTO dto) {
        merchantService.getMerchant(merchantId);
        MerchantPaymentConfig config = new MerchantPaymentConfig();
        BeanUtils.copyProperties(dto, config);
        config.setMerchantId(merchantId);
        merchantPaymentConfigService.saveOrUpdate(config);
        return Result.success(toPaymentConfigVO(merchantId, merchantPaymentConfigService.getConfig(merchantId)));
    }

    @Operation(summary = "查询商户钱包限额")
    @GetMapping("/{merchantId}/wallet-limit")
    /**
     * 获取钱包限额
     *
     * @param merchantId merchantid
     * @return 获取钱包限额的结果
     */
    public Result<MerchantWalletLimitVO> getWalletLimit(@PathVariable Long merchantId) {
        merchantService.getMerchant(merchantId);
        return Result.success(toWalletLimitVO(merchantId, walletLimitService.getLimit(merchantId)));
    }

    @Operation(summary = "保存商户钱包限额")
    @PutMapping("/{merchantId}/wallet-limit")
    /**
     * 保存钱包限额
     * @param merchantId merchantid
     * @param dto dto
     */
    public Result<MerchantWalletLimitVO> saveWalletLimit(@PathVariable Long merchantId,
                                                         @RequestBody MerchantWalletLimitDTO dto) {
        merchantService.getMerchant(merchantId);
        MerchantWalletLimit limit = new MerchantWalletLimit();
        BeanUtils.copyProperties(dto, limit);
        limit.setMerchantId(merchantId);
        walletLimitService.saveOrUpdate(limit);
        return Result.success(toWalletLimitVO(merchantId, walletLimitService.getLimit(merchantId)));
    }

    /**
     * 转换为商户配置vo
     *
     * @param merchantId merchantid
     * @param config 配置
     * @return 转换为商户配置vo的结果
     */
    private MerchantPaymentConfigVO toPaymentConfigVO(Long merchantId, MerchantPaymentConfig config) {
        MerchantPaymentConfigVO vo = new MerchantPaymentConfigVO();
        if (config != null) {
            BeanUtils.copyProperties(config, vo);
        }
        vo.setMerchantId(merchantId);
        return vo;
    }

    /**
     * 转换为钱包限额vo
     *
     * @param merchantId merchantid
     * @param limit 限额
     * @return 转换为钱包限额vo的结果
     */
    private MerchantWalletLimitVO toWalletLimitVO(Long merchantId, MerchantWalletLimit limit) {
        MerchantWalletLimitVO vo = new MerchantWalletLimitVO();
        if (limit != null) {
            BeanUtils.copyProperties(limit, vo);
        }
        vo.setMerchantId(merchantId);
        return vo;
    }
}
