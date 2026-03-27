package com.chua.payment.support.controller;

import com.chua.payment.support.common.Result;
import com.chua.payment.support.dto.ChannelConfigDTO;
import com.chua.payment.support.service.MerchantChannelService;
import com.chua.payment.support.vo.MerchantChannelVO;
import com.chua.payment.support.vo.PaymentMethodGuideVO;
import com.chua.payment.support.vo.ProviderSpiOptionVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商户渠道管理控制器
 */
@Tag(name = "渠道管理", description = "支付渠道配置相关接口")
@RestController
@RequestMapping("/api/channel")
@RequiredArgsConstructor
public class MerchantChannelController {

    private final MerchantChannelService channelService;

    @Operation(summary = "创建渠道配置")
    @PostMapping
    public Result<MerchantChannelVO> createChannel(@RequestBody ChannelConfigDTO dto) {
        return Result.success(channelService.createChannel(dto));
    }

    @Operation(summary = "更新渠道配置")
    @PutMapping("/{id}")
    public Result<MerchantChannelVO> updateChannel(@PathVariable Long id, @RequestBody ChannelConfigDTO dto) {
        return Result.success(channelService.updateChannel(id, dto));
    }

    @Operation(summary = "查询渠道配置")
    @GetMapping("/{id}")
    public Result<MerchantChannelVO> getChannel(@PathVariable Long id) {
        return Result.success(channelService.getChannel(id));
    }

    @Operation(summary = "查询商户的所有渠道")
    @GetMapping("/merchant/{merchantId}")
    public Result<List<MerchantChannelVO>> listChannels(@PathVariable Long merchantId,
                                                        @RequestParam(required = false) String channelType,
                                                        @RequestParam(required = false) Integer status) {
        return Result.success(channelService.listChannels(merchantId, channelType, status));
    }

    @Operation(summary = "查询渠道配置列表")
    @GetMapping("/list")
    public Result<List<MerchantChannelVO>> listAllChannels(@RequestParam(required = false) Long merchantId,
                                                           @RequestParam(required = false) String channelType,
                                                           @RequestParam(required = false) Integer status) {
        return Result.success(channelService.listChannels(merchantId, channelType, status));
    }

    @Operation(summary = "支付方式目录与开通指引")
    @GetMapping("/catalog")
    public Result<List<PaymentMethodGuideVO>> listCatalog() {
        return Result.success(channelService.listCatalog());
    }

    @Operation(summary = "查询 provider SPI 选项")
    @GetMapping("/provider-options")
    public Result<List<ProviderSpiOptionVO>> listProviderOptions(@RequestParam String channelType) {
        return Result.success(channelService.listProviderOptions(channelType));
    }

    @Operation(summary = "启用渠道")
    @PutMapping("/{id}/enable")
    public Result<Boolean> enableChannel(@PathVariable Long id) {
        return Result.success(channelService.enableChannel(id));
    }

    @Operation(summary = "禁用渠道")
    @PutMapping("/{id}/disable")
    public Result<Boolean> disableChannel(@PathVariable Long id) {
        return Result.success(channelService.disableChannel(id));
    }

    @Operation(summary = "删除渠道")
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteChannel(@PathVariable Long id) {
        return Result.success(channelService.deleteChannel(id));
    }
}
