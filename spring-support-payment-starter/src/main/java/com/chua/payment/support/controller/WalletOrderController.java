package com.chua.payment.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.channel.RechargeRequest;
import com.chua.payment.support.channel.TransferRequest;
import com.chua.payment.support.channel.WithdrawRequest;
import com.chua.payment.support.common.PageResult;
import com.chua.payment.support.common.Result;
import com.chua.payment.support.dto.WalletOrderNotifyDTO;
import com.chua.payment.support.dto.WalletRechargeDTO;
import com.chua.payment.support.dto.WalletTransferDTO;
import com.chua.payment.support.dto.WalletWithdrawDTO;
import com.chua.payment.support.entity.WalletOrder;
import com.chua.payment.support.service.PaymentCallbackUrlResolver;
import com.chua.payment.support.service.WalletNotifyService;
import com.chua.payment.support.service.WalletOrderService;
import com.chua.payment.support.vo.WalletOrderVO;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * 钱包订单控制器
 */
@RestController
@RequestMapping("/api/wallet/order")
@RequiredArgsConstructor
public class WalletOrderController {

    private final WalletOrderService walletOrderService;
    private final PaymentCallbackUrlResolver paymentCallbackUrlResolver;
    private final WalletNotifyService walletNotifyService;
    private final ObjectMapper objectMapper;

    @PostMapping("/recharge")
    public Result<WalletOrderVO> createRecharge(@RequestBody WalletRechargeDTO dto) {
        RechargeRequest request = new RechargeRequest();
        BeanUtils.copyProperties(dto, request);
        return Result.success(convert(walletOrderService.createRechargeOrder(request)));
    }

    @PostMapping("/transfer")
    public Result<WalletOrderVO> createTransfer(@RequestBody WalletTransferDTO dto) {
        TransferRequest request = new TransferRequest();
        BeanUtils.copyProperties(dto, request);
        return Result.success(convert(walletOrderService.createTransferOrder(request)));
    }

    @PostMapping("/withdraw")
    public Result<WalletOrderVO> createWithdraw(@RequestBody WalletWithdrawDTO dto) {
        WithdrawRequest request = new WithdrawRequest();
        BeanUtils.copyProperties(dto, request);
        return Result.success(convert(walletOrderService.createWithdrawOrder(request)));
    }

    @GetMapping("/{orderNo}")
    public Result<WalletOrderVO> getByOrderNo(@PathVariable String orderNo) {
        WalletOrder order = walletOrderService.getByOrderNo(orderNo);
        return order == null ? Result.error("钱包订单不存在") : Result.success(convert(order));
    }

    @GetMapping("/page")
    public Result<PageResult<WalletOrderVO>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                  @RequestParam(defaultValue = "10") int pageSize,
                                                  @RequestParam(required = false) Long merchantId,
                                                  @RequestParam(required = false) Long userId,
                                                  @RequestParam(required = false) String orderType,
                                                  @RequestParam(required = false) String status) {
        Page<WalletOrder> page = walletOrderService.page(pageNum, pageSize, merchantId, userId, orderType, status);
        Page<WalletOrderVO> voPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::convert).toList());
        return Result.success(PageResult.of(voPage));
    }

    @PostMapping("/{orderNo}/simulate-notify")
    public Result<WalletOrderVO> simulateNotify(@PathVariable String orderNo,
                                                @RequestBody(required = false) WalletOrderNotifyDTO dto) {
        WalletOrder order = walletOrderService.getByOrderNo(orderNo);
        if (order == null) {
            return Result.error("钱包订单不存在");
        }
        walletNotifyService.handleNotify(
                order.getOrderType(),
                orderNo,
                dto != null ? dto.getThirdPartyOrderNo() : null,
                dto != null ? dto.getStatus() : null,
                buildNotifyPayload(dto),
                dto != null ? dto.getReason() : null);
        return Result.success(convert(walletOrderService.getByOrderNo(orderNo)));
    }

    private WalletOrderVO convert(WalletOrder order) {
        WalletOrderVO vo = new WalletOrderVO();
        BeanUtils.copyProperties(order, vo);
        vo.setNotifyUrl(paymentCallbackUrlResolver.defaultWalletNotifyUrl(order.getOrderType(), order.getOrderNo()));
        return vo;
    }

    private String buildNotifyPayload(WalletOrderNotifyDTO dto) {
        if (dto == null) {
            return "{}";
        }
        if (dto.getPayload() != null && !dto.getPayload().isBlank()) {
            return dto.getPayload();
        }
        try {
            java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("status", dto.getStatus());
            payload.put("thirdPartyOrderNo", dto.getThirdPartyOrderNo());
            payload.put("reason", dto.getReason());
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }
}
