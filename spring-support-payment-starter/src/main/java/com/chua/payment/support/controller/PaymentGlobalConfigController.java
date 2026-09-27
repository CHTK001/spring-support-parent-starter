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
 * @author CH
 * @since 4.0.0
 */
@Tag(name = "支付全局配置", description = "支付回调、返回地址、默认自动刷新等")
@RestController
@RequestMapping("/payment-global-config")
@RequiredArgsConstructor
public class PaymentGlobalConfigController {

    /**
     * 支付全局配置服务
     */
    private final PaymentGlobalConfigService paymentGlobalConfigService;
    /**
     * 登录主体解析器
     */
    private final PaymentAccountPrincipalResolver principalResolver;

    @Operation(summary = "查询支付全局配置")
    @GetMapping
    /**
     * 获取配置
     *
     * @param request 请求
     * @return 获取配置的结果
     */
    public Result<PaymentGlobalConfigVO> getConfig(HttpServletRequest request) {
        Result<PaymentGlobalConfigVO> denied = denyIfNecessary(request);
        return denied != null ? denied : Result.success(paymentGlobalConfigService.getConfig());
    }

    @Operation(summary = "保存支付全局配置")
    @PutMapping
    /**
     * 保存配置
     *
     * @param dto dto
     * @param request 请求
     * @return 保存配置的结果
     */
    public Result<PaymentGlobalConfigVO> saveConfig(@RequestBody PaymentGlobalConfigDTO dto, HttpServletRequest request) {
        Result<PaymentGlobalConfigVO> denied = denyIfNecessary(request);
        return denied != null ? denied : Result.success(paymentGlobalConfigService.saveOrUpdate(dto));
    }

    /**
     * 权限校验
     *
     * @param request 请求
     */
    private <T> Result<T> denyIfNecessary(HttpServletRequest request) {
        UserResume userResume = principalResolver.resolve(request);
        if (userResume == null) {
            return Result.error(401, "未获取到当前账户");
        }
        if (!principalResolver.isAdmin(userResume)) {
            return Result.error(403, "仅超管和管理员可访问");
        }
        return null;
    }
}
