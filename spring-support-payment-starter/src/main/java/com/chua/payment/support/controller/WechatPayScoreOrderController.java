package com.chua.payment.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.payment.support.dto.WechatPayScoreCancelDTO;
import com.chua.payment.support.dto.WechatPayScoreCompleteDTO;
import com.chua.payment.support.dto.WechatPayScoreCreateDTO;
import com.chua.payment.support.dto.WechatPayScoreOrderQueryRequest;
import com.chua.payment.support.entity.WechatPayScoreOrder;
import com.chua.payment.support.service.WechatPayScoreOrderService;
import com.chua.payment.support.vo.WechatPayScoreOrderVO;
import com.chua.starter.common.support.lang.ReturnPageResult;
import com.chua.starter.mybatis.utils.PageResultUtils;
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
   * @author CH
   * @since 4.0.0
   */
@Tag(name = "微信支付分", description = "微信支付分服务订单相关接口")
@RestController
@RequestMapping("/wechat/payscore/order")
@RequiredArgsConstructor
public class WechatPayScoreOrderController {

      /**
       * 微信支付分服务
       */
    private final WechatPayScoreOrderService wechatPayScoreOrderService;

    @Operation(summary = "创建微信支付分订单")
    @PostMapping
      /**
       * 创建
       *
       * @param dto dto
       * @return 创建的结果
       */
    public ReturnResult<WechatPayScoreOrderVO> create(@RequestBody WechatPayScoreCreateDTO dto) {
        return ReturnResult.ok(convert(wechatPayScoreOrderService.createOrder(dto)));
    }

    @Operation(summary = "查询微信支付分订单")
    @GetMapping("/{outOrderNo}")
      /**
       * 获取
       *
       * @param outOrderNo 商户订单号
       * @return 获取的结果
       */
    public ReturnResult<WechatPayScoreOrderVO> get(@PathVariable String outOrderNo) {
        WechatPayScoreOrder order = wechatPayScoreOrderService.getByOutOrderNo(outOrderNo);
        return order == null ? ReturnResult.error("微信支付分订单不存在") : ReturnResult.ok(convert(order));
    }

    @Operation(summary = "分页查询微信支付分订单")
    @GetMapping("/page")
      /**
       * 分页查询微信支付分订单。
       *
       * @param pageNum    页码（默认 1）
       * @param pageSize   每页条数（默认 10）
       * @param merchantId 商户标识（可选）
       * @param channelId  支付渠道标识（可选）
       * @param openId     用户 openId（可选）
       * @param state      订单状态（可选）
       * @return 微信支付分订单分页结果
       */
    public ReturnPageResult<WechatPayScoreOrderVO> page(@RequestParam(defaultValue = "1") int pageNum,
                                                          @RequestParam(defaultValue = "10") int pageSize,
                                                          @RequestParam(required = false) Long merchantId,
                                                          @RequestParam(required = false) Long channelId,
                                                          @RequestParam(required = false) String openId,
                                                          @RequestParam(required = false) String state) {
        Page<WechatPayScoreOrder> page = wechatPayScoreOrderService.page(new WechatPayScoreOrderQueryRequest(pageNum, pageSize
            , merchantId, channelId, openId, state));
        Page<WechatPayScoreOrderVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::convert).toList());
        return PageResultUtils.ok(voPage);
    }

    @Operation(summary = "同步微信支付分订单状态")
    @PostMapping("/{outOrderNo}/sync")
      /**
       * 同步
       *
       * @param outOrderNo 商户订单号
       * @return 同步的结果
       */
    public ReturnResult<WechatPayScoreOrderVO> sync(@PathVariable String outOrderNo) {
        return ReturnResult.ok(convert(wechatPayScoreOrderService.syncOrder(outOrderNo)));
    }

    @Operation(summary = "完结微信支付分订单")
    @PostMapping("/{outOrderNo}/complete")
      /**
       * 完结
       * @param outOrderNo 商户订单号
       * @param dto dto
       * @return 完结后的订单结果
       */
    public ReturnResult<WechatPayScoreOrderVO> complete(@PathVariable String outOrderNo,
                                                  @RequestBody(required = false) WechatPayScoreCompleteDTO dto) {
        return ReturnResult.ok(convert(wechatPayScoreOrderService.completeOrder(outOrderNo, dto)));
    }

    @Operation(summary = "取消微信支付分订单")
    @PostMapping("/{outOrderNo}/cancel")
      /**
       * 取消
       * @param outOrderNo 商户订单号
       * @param dto dto
       * @return 取消后的订单结果
       */
    public ReturnResult<WechatPayScoreOrderVO> cancel(@PathVariable String outOrderNo,
                                                @RequestBody(required = false) WechatPayScoreCancelDTO dto) {
        return ReturnResult.ok(convert(wechatPayScoreOrderService.cancelOrder(outOrderNo, dto)));
    }

      /**
       * 转换
       *
       * @param order 订单
       * @return 转换的结果
       */
    private WechatPayScoreOrderVO convert(WechatPayScoreOrder order) {
        return new WechatPayScoreOrderVO(
                order.getId(),
                order.getMerchantId(),
                order.getChannelId(),
                order.getUserId(),
                order.getOutOrderNo(),
                order.getServiceOrderNo(),
                order.getAppId(),
                order.getServiceId(),
                order.getOpenId(),
                order.getState(),
                order.getTotalAmount(),
                order.getNotifyUrl(),
                order.getServiceIntroduction(),
                order.getStartTime(),
                order.getEndTime(),
                order.getFinishType(),
                order.getReason(),
                order.getFinishReason(),
                order.getPackageInfo(),
                order.getAttach(),
                order.getRequestPayload(),
                order.getResponsePayload(),
                order.getRemark(),
                order.getCompletedAt(),
                order.getCancelledAt(),
                order.getCreateTime(),
                order.getUpdateTime());
    }
}
