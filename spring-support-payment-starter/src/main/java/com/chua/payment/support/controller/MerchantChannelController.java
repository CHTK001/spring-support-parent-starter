package com.chua.payment.support.controller;

import com.chua.payment.support.common.Result;
import com.chua.payment.support.dto.ChannelConfigDTO;
import com.chua.payment.support.service.MerchantChannelService;
import com.chua.payment.support.vo.MerchantChannelVO;
import com.chua.payment.support.vo.PaymentMethodGuideVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商户渠道管理控制器
 * @author CH
 * @since 4.0.0
 */
@Tag(name = "渠道管理", description = "支付渠道配置相关接口")
@RestController
@RequestMapping("/channel")
@RequiredArgsConstructor
public class MerchantChannelController {

    /** 通道服务 */
    private final MerchantChannelService channelService;

    @Operation(summary = "创建渠道配置")
    @PostMapping
    /**
    * 创建通道
    *
    * @param dto dto
    * @return 创建通道的结果
    */
    public Result<MerchantChannelVO> createChannel(@RequestBody ChannelConfigDTO dto) {
        return Result.success(channelService.createChannel(dto));
    }

    @Operation(summary = "更新渠道配置")
    @PutMapping("/{id}")
    /**
     * 更新通道
     *
     * @param id 标识
     * @param dto dto
     * @return 更新通道的结果
     */
    public Result<MerchantChannelVO> updateChannel(@PathVariable Long id, @RequestBody ChannelConfigDTO dto) {
        return Result.success(channelService.updateChannel(id, dto));
    }

    @Operation(summary = "查询渠道配置")
    @GetMapping("/{id}")
    /**
     * 获取通道
     *
     * @param id 标识
     * @return 获取通道的结果
     */
    public Result<MerchantChannelVO> getChannel(@PathVariable Long id) {
        return Result.success(channelService.getChannel(id));
    }

    @Operation(summary = "查询商户的所有渠道")
    @GetMapping("/merchant/{merchantId}")
    /**
     * 列表通道
     * @param merchantId merchantid
     * @param channelType 通道类型
     * @param status 状态
     */
    public Result<List<MerchantChannelVO>> listChannels(@PathVariable Long merchantId,
                                                        @RequestParam(required = false) String channelType,
                                                        @RequestParam(required = false) Integer status) {
        return Result.success(channelService.listChannels(merchantId, channelType, status));
    }

    @Operation(summary = "查询渠道配置列表")
    @GetMapping("/list")
    /**
     * 列表全部通道
     *
     * @param false false
     * @return 列表全部通道的结果
     */
    public Result<List<MerchantChannelVO>> listAllChannels(@RequestParam(required = false) Long merchantId,
                                                           @RequestParam(required = false) String channelType,
                                                           @RequestParam(required = false) Integer status) {
        return Result.success(channelService.listChannels(merchantId, channelType, status));
    }

    @Operation(summary = "支付方式目录与开通指引")
    @GetMapping("/catalog")
    /**
     * 列表catalog
     *
     * @return 列表catalog的结果
     */
    public Result<List<PaymentMethodGuideVO>> listCatalog() {
        return Result.success(channelService.listCatalog());
    }

    @Operation(summary = "启用渠道")
    @PutMapping("/{id}/enable")
    /**
     * 启用通道
     *
     * @param id 标识
     * @return enable通道的结果
     */
    public Result<Boolean> enableChannel(@PathVariable Long id) {
        return Result.success(channelService.enableChannel(id));
    }

    @Operation(summary = "禁用渠道")
    @PutMapping("/{id}/disable")
    /**
     * 禁用通道
     *
     * @param id 标识
     * @return disable通道的结果
     */
    public Result<Boolean> disableChannel(@PathVariable Long id) {
        return Result.success(channelService.disableChannel(id));
    }

    @Operation(summary = "删除渠道")
    @DeleteMapping("/{id}")
    /**
     * 删除通道
     *
     * @param id 标识
     * @return 删除通道的结果
     */
    public Result<Boolean> deleteChannel(@PathVariable Long id) {
        return Result.success(channelService.deleteChannel(id));
    }
}

