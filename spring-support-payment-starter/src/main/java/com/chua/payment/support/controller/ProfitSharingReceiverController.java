package com.chua.payment.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.payment.support.dto.ProfitSharingReceiverDTO;
import com.chua.payment.support.service.ProfitSharingReceiverService;
import com.chua.payment.support.support.PaymentAccountPrincipalResolver;
import com.chua.payment.support.vo.ProfitSharingReceiverVO;
import com.chua.starter.oauth.client.support.user.UserResume;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
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
 * 分账接收方控制器，提供接收方的维护与启停入口。
 *
 * @author CH
 * @since 4.0.0
 */
@Tag(name = "分账接收方管理", description = "分账接收方的创建、更新、删除、启停与查询")
@RestController
@RequestMapping("/profit-sharing/receiver")
@RequiredArgsConstructor
public class ProfitSharingReceiverController {

    /**
     * 分账接收方服务
     */
    private final ProfitSharingReceiverService profitSharingReceiverService;
    /**
     * Principal解析器
     */
    private final PaymentAccountPrincipalResolver principalResolver;

    @Operation(summary = "创建分账接收方")
    @PostMapping
    public ReturnResult<ProfitSharingReceiverVO> create(@RequestBody ProfitSharingReceiverDTO dto,
                                                        HttpServletRequest request) {
        ReturnResult<ProfitSharingReceiverVO> denied = denyIfNecessary(request);
        return denied != null ? denied : ReturnResult.ok(profitSharingReceiverService.create(dto));
    }

    @Operation(summary = "更新分账接收方")
    @PutMapping("/{id}")
    public ReturnResult<ProfitSharingReceiverVO> update(@PathVariable Long id,
                                                        @RequestBody ProfitSharingReceiverDTO dto,
                                                        HttpServletRequest request) {
        ReturnResult<ProfitSharingReceiverVO> denied = denyIfNecessary(request);
        return denied != null ? denied : ReturnResult.ok(profitSharingReceiverService.update(id, dto));
    }

    @Operation(summary = "删除分账接收方")
    @DeleteMapping("/{id}")
    public ReturnResult<Boolean> delete(@PathVariable Long id, HttpServletRequest request) {
        ReturnResult<Boolean> denied = denyIfNecessary(request);
        return denied != null ? denied : ReturnResult.ok(profitSharingReceiverService.delete(id));
    }

    @Operation(summary = "启用分账接收方")
    @PostMapping("/{id}/enable")
    public ReturnResult<ProfitSharingReceiverVO> enable(@PathVariable Long id, HttpServletRequest request) {
        ReturnResult<ProfitSharingReceiverVO> denied = denyIfNecessary(request);
        return denied != null ? denied
                : ReturnResult.ok(profitSharingReceiverService.updateStatus(id, "ENABLED"));
    }

    @Operation(summary = "停用分账接收方")
    @PostMapping("/{id}/disable")
    public ReturnResult<ProfitSharingReceiverVO> disable(@PathVariable Long id, HttpServletRequest request) {
        ReturnResult<ProfitSharingReceiverVO> denied = denyIfNecessary(request);
        return denied != null ? denied
                : ReturnResult.ok(profitSharingReceiverService.updateStatus(id, "DISABLED"));
    }

    @Operation(summary = "查询分账接收方详情")
    @GetMapping("/{id}")
    public ReturnResult<ProfitSharingReceiverVO> get(@PathVariable Long id, HttpServletRequest request) {
        ReturnResult<ProfitSharingReceiverVO> denied = denyIfNecessary(request);
        return denied != null ? denied : ReturnResult.ok(profitSharingReceiverService.get(id));
    }

    @Operation(summary = "按商户与渠道查询分账接收方列表")
    @GetMapping("/list")
    public ReturnResult<List<ProfitSharingReceiverVO>> listByMerchant(@RequestParam Long merchantId,
                                                                      @RequestParam(required = false) String channelType,
                                                                      @RequestParam(required = false) String status,
                                                                      HttpServletRequest request) {
        ReturnResult<List<ProfitSharingReceiverVO>> denied = denyIfNecessary(request);
        return denied != null ? denied
                : ReturnResult.ok(profitSharingReceiverService.listByMerchant(merchantId, channelType, status));
    }

    /**
     * 权限校验（非管理员请求直接拒绝）
     *
     * @param request 请求
     * @param <T>     响应数据类型
     * @return 拒绝响应；校验通过返回 {@code null}
     */
    private <T> ReturnResult<T> denyIfNecessary(HttpServletRequest request) {
        UserResume userResume = principalResolver.resolve(request);
        if (userResume == null) {
            return ReturnResult.error("401", "未获取到当前账户");
        }
        if (!principalResolver.isAdmin(userResume)) {
            return ReturnResult.error("403", "仅超管和管理员可访问");
        }
        return null;
    }
}
