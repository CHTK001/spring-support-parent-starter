package com.chua.payment.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.common.PageResult;
import com.chua.payment.support.common.Result;
import com.chua.payment.support.dto.WechatPayScoreCancelDTO;
import com.chua.payment.support.dto.WechatPayScoreCompleteDTO;
import com.chua.payment.support.dto.WechatPayScoreCreateDTO;
import com.chua.payment.support.entity.WechatPayScoreOrder;
import com.chua.payment.support.service.WechatPayScoreOrderService;
import com.chua.payment.support.vo.WechatPayScoreOrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 微信支付分订单控制器
 */
@Tag(name = "微信支付分", description = "微信支付分服务订单相关接口")
@RestController
@RequestMapping("/api/wechat/payscore/order")
@RequiredArgsConstructor
public class WechatPayScoreOrderController {

    private final WechatPayScoreOrderService wechatPayScoreOrderService;

    @Operation(summary = "创建微信支付分订单")
    @PostMapping
    public Result<WechatPayScoreOrderVO> create(@RequestBody WechatPayScoreCreateDTO dto) {
        return Result.success(convert(wechatPayScoreOrderService.createOrder(dto)));
    }

    @Operation(summary = "查询微信支付分订单")
    @GetMapping("/{outOrderNo}")
    public Result<WechatPayScoreOrderVO> get(@PathVariable String outOrderNo) {
        WechatPayScoreOrder order = wechatPayScoreOrderService.getByOutOrderNo(outOrderNo);
        return order == null ? Result.error("微信支付分订单不存在") : Result.success(convert(order));
    }

    @Operation(summary = "分页查询微信支付分订单")
    @GetMapping("/page")
    public Result<PageResult<WechatPayScoreOrderVO>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                          @RequestParam(defaultValue = "10") int pageSize,
                                                          @RequestParam(required = false) Long merchantId,
                                                          @RequestParam(required = false) Long channelId,
                                                          @RequestParam(required = false) String openId,
                                                          @RequestParam(required = false) String state) {
        Page<WechatPayScoreOrder> page = wechatPayScoreOrderService.page(pageNum, pageSize, merchantId, channelId, openId, state);
        Page<WechatPayScoreOrderVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::convert).toList());
        return Result.success(PageResult.of(voPage));
    }

    @Operation(summary = "同步微信支付分订单状态")
    @PostMapping("/{outOrderNo}/sync")
    public Result<WechatPayScoreOrderVO> sync(@PathVariable String outOrderNo) {
        return Result.success(convert(wechatPayScoreOrderService.syncOrder(outOrderNo)));
    }

    @Operation(summary = "完结微信支付分订单")
    @PostMapping("/{outOrderNo}/complete")
    public Result<WechatPayScoreOrderVO> complete(@PathVariable String outOrderNo,
                                                  @RequestBody(required = false) WechatPayScoreCompleteDTO dto) {
        return Result.success(convert(wechatPayScoreOrderService.completeOrder(outOrderNo, dto)));
    }

    @Operation(summary = "取消微信支付分订单")
    @PostMapping("/{outOrderNo}/cancel")
    public Result<WechatPayScoreOrderVO> cancel(@PathVariable String outOrderNo,
                                                @RequestBody(required = false) WechatPayScoreCancelDTO dto) {
        return Result.success(convert(wechatPayScoreOrderService.cancelOrder(outOrderNo, dto)));
    }

    private WechatPayScoreOrderVO convert(WechatPayScoreOrder order) {
        WechatPayScoreOrderVO vo = new WechatPayScoreOrderVO();
        BeanUtils.copyProperties(order, vo);
        return vo;
    }
}
