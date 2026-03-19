package com.chua.payment.support.controller;

import com.chua.payment.support.common.Result;
import com.chua.payment.support.dto.ChannelConfigDTO;
import com.chua.payment.support.entity.MerchantChannel;
import com.chua.payment.support.service.MerchantChannelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商户渠道管理控制器
 *
 * @author CH
 * @since 2026-03-18
 */
@Tag(name = "渠道管理", description = "支付渠道配置相关接口")
@RestController
@RequestMapping("/api/channel")
@RequiredArgsConstructor
public class MerchantChannelController {

    private final MerchantChannelService channelService;

    @Operation(summary = "创建渠道配置")
    @PostMapping
    public Result<MerchantChannel> createChannel(@RequestBody ChannelConfigDTO dto) {
        return Result.success(channelService.createChannel(dto));
    }

    @Operation(summary = "更新渠道配置")
    @PutMapping("/{id}")
    public Result<MerchantChannel> updateChannel(@PathVariable Long id, @RequestBody ChannelConfigDTO dto) {
        return Result.success(channelService.updateChannel(id, dto));
    }

    @Operation(summary = "查询渠道配置")
    @GetMapping("/{id}")
    public Result<MerchantChannel> getChannel(@PathVariable Long id) {
        return Result.success(channelService.getChannel(id));
    }

    @Operation(summary = "查询商户的所有渠道")
    @GetMapping("/merchant/{merchantId}")
    public Result<List<MerchantChannel>> listChannels(@PathVariable Long merchantId) {
        return Result.success(channelService.listChannels(merchantId));
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
}
