package com.chua.payment.support.controller;

import com.chua.payment.support.common.Result;
import com.chua.payment.support.dto.PaymentGlobalConfigDTO;
import com.chua.payment.support.service.PaymentGlobalConfigService;
import com.chua.payment.support.support.PaymentAccountPrincipalResolver;
import com.chua.payment.support.vo.PaymentGlobalConfigVO;
import com.chua.starter.oauth.client.support.user.UserResume;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付全局配置控制器
 */
@Tag(name = "支付全局配置", description = "支付回调、返回地址、默认自动刷新配置")
@RestController
@RequestMapping("/api/payment-global-config")
@RequiredArgsConstructor
public class PaymentGlobalConfigController {

    private final PaymentGlobalConfigService paymentGlobalConfigService;
    private final PaymentAccountPrincipalResolver principalResolver;

    @Operation(summary = "查询支付全局配置")
    @GetMapping
    public Result<PaymentGlobalConfigVO> getConfig(HttpServletRequest request) {
        Result<PaymentGlobalConfigVO> denied = denyIfNecessary(request);
        return denied != null ? denied : Result.success(paymentGlobalConfigService.getConfig());
    }

    @Operation(summary = "保存支付全局配置")
    @PutMapping
    public Result<PaymentGlobalConfigVO> saveConfig(@RequestBody PaymentGlobalConfigDTO dto, HttpServletRequest request) {
        Result<PaymentGlobalConfigVO> denied = denyIfNecessary(request);
        return denied != null ? denied : Result.success(paymentGlobalConfigService.saveOrUpdate(dto));
    }

    private <T> Result<T> denyIfNecessary(HttpServletRequest request) {
        UserResume userResume = principalResolver.resolve(request);
        if (userResume == null) {
            return Result.error(401, "未获取到当前账号");
        }
        if (!principalResolver.isAdmin(userResume)) {
            return Result.error(403, "仅超管和管理员可访问");
        }
        return null;
    }
}
