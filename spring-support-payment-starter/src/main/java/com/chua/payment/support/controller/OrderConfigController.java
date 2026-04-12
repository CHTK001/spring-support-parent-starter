package com.chua.payment.support.controller;

import com.chua.payment.support.common.Result;
import com.chua.payment.support.dto.OrderPartitionConfigDTO;
import com.chua.payment.support.service.OrderPartitionConfigService;
import com.chua.payment.support.support.PaymentAccountPrincipalResolver;
import com.chua.payment.support.vo.OrderPartitionConfigVO;
import com.chua.payment.support.vo.OrderPartitionPreviewVO;
import com.chua.starter.oauth.client.support.user.UserResume;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "订单配置管理", description = "订单和流水分表策略配置")
@RestController
@RequestMapping("/api/order-config")
@RequiredArgsConstructor
public class OrderConfigController {

    private final OrderPartitionConfigService orderPartitionConfigService;
    private final PaymentAccountPrincipalResolver principalResolver;

    @Operation(summary = "查询分表配置")
    @GetMapping("/partitions")
    public Result<List<OrderPartitionConfigVO>> list(HttpServletRequest request) {
        Result<List<OrderPartitionConfigVO>> denied = denyIfNecessary(request);
        return denied != null ? denied : Result.success(orderPartitionConfigService.listConfigs());
    }

    @Operation(summary = "更新分表配置")
    @PutMapping("/partitions/{businessType}")
    public Result<OrderPartitionConfigVO> update(@PathVariable String businessType,
                                                 @RequestBody OrderPartitionConfigDTO dto,
                                                 HttpServletRequest request) {
        Result<OrderPartitionConfigVO> denied = denyIfNecessary(request);
        return denied != null ? denied : Result.success(orderPartitionConfigService.updateConfig(businessType, dto));
    }

    @Operation(summary = "预览分表策略")
    @GetMapping("/partitions/{businessType}/preview")
    public Result<OrderPartitionPreviewVO> preview(@PathVariable String businessType, HttpServletRequest request) {
        Result<OrderPartitionPreviewVO> denied = denyIfNecessary(request);
        return denied != null ? denied : Result.success(orderPartitionConfigService.preview(businessType));
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
