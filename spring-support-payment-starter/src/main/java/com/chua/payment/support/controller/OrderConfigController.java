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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
/** @作者 CH */

@Tag(name = "订单配置管理", description = "订单和流水分表策略配置")
@RestController
@RequestMapping("/order-config")
@RequiredArgsConstructor
public class OrderConfigController {

    /** 排序分区配置服务 */
    private final OrderPartitionConfigService orderPartitionConfigService;
    /** Principal解析器 */
    private final PaymentAccountPrincipalResolver principalResolver;

    @Operation(summary = "查询分表配置")
    @GetMapping("/partitions")
    /**
    * 列表
    *
    * @param request 请求
    * @return 列表的结果
     */
    public Result<List<OrderPartitionConfigVO>> list(HttpServletRequest request) {
        Result<List<OrderPartitionConfigVO>> denied = denyIfNecessary(request);
        return denied != null ? denied : Result.success(orderPartitionConfigService.listConfigs());
    }

    @Operation(summary = "更新分表配置")
    @PutMapping("/partitions/{businessType}")
    /**
    * 更新
    * @param businessType 业务类型
    * @param dto dto
    * @param request 请求
     */
    public Result<OrderPartitionConfigVO> update(@PathVariable String businessType,
                                                 @RequestBody OrderPartitionConfigDTO dto,
                                                 HttpServletRequest request) {
        Result<OrderPartitionConfigVO> denied = denyIfNecessary(request);
        return denied != null ? denied : Result.success(orderPartitionConfigService.updateConfig(businessType, dto));
    }

    @Operation(summary = "预览分表策略")
    @GetMapping("/partitions/{businessType}/preview")
    /**
    * Preview
    *
    * @param businessType 业务类型
    * @param request 请求
    * @return preview的结果
     */
    public Result<OrderPartitionPreviewVO> preview(@PathVariable String businessType, HttpServletRequest request) {
        Result<OrderPartitionPreviewVO> denied = denyIfNecessary(request);
        return denied != null ? denied : Result.success(orderPartitionConfigService.preview(businessType));
    }

    @Operation(summary = "手动触发建表")
    @PostMapping("/partitions/{businessType}/create")
    /**
    * Trigger创建
    *
    * @param businessType 业务类型
    * @param request 请求
    * @return trigger创建的结果
     */
    public Result<Void> triggerCreate(@PathVariable String businessType, HttpServletRequest request) {
        Result<Void> denied = denyIfNecessary(request);
        if (denied != null) {
            return denied;
        }
        orderPartitionConfigService.executeAutoCreate(businessType);
        return Result.success();
    }

    @Operation(summary = "手动触发迁移")
    @PostMapping("/partitions/{businessType}/migrate")
    /**
    * triggermigrate
    *
    * @param businessType 业务类型
    * @param request 请求
    * @return triggerMigrate的结果
     */
    public Result<Void> triggerMigrate(@PathVariable String businessType, HttpServletRequest request) {
        Result<Void> denied = denyIfNecessary(request);
        if (denied != null) {
            return denied;
        }
        orderPartitionConfigService.executeAutoMigrate(businessType);
        return Result.success();
    }

    /**
    * denyifnecessary
    *
    * @param request 请求
    * @return denyIfNecessary的结果
     */
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

