// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.payment.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
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
/**
 * 订单配置控制器
 *
 * @author CH
 * @since 4.0.0
 */

@Tag(name = "订单配置管理", description = "订单和流水分表策略配置")
@RestController
@RequestMapping("/order-config")
@RequiredArgsConstructor
public class OrderConfigController {

    /**
     * 排序分区配置服务
     */
    private final OrderPartitionConfigService orderPartitionConfigService;
    /**
     * Principal解析器
     */
    private final PaymentAccountPrincipalResolver principalResolver;

    @Operation(summary = "查询分表配置")
    @GetMapping("/partitions")
    /**
     * 列表
     *
     * @param request 请求
     * @return 列表的结果
     */
    public ReturnResult<List<OrderPartitionConfigVO>> list(HttpServletRequest request) {
        ReturnResult<List<OrderPartitionConfigVO>> denied = denyIfNecessary(request);
        return denied != null ? denied : ReturnResult.ok(orderPartitionConfigService.listConfigs());
    }

    @Operation(summary = "更新分表配置")
    @PutMapping("/partitions/{businessType}")
    /**
    * 更新分表配置
    * @param businessType 业务类型
    * @param dto 分表配置
    * @param request 请求
    * @return 更新后的分表配置
     */
    public ReturnResult<OrderPartitionConfigVO> update(@PathVariable String businessType,
                                                 @RequestBody OrderPartitionConfigDTO dto,
                                                 HttpServletRequest request) {
        ReturnResult<OrderPartitionConfigVO> denied = denyIfNecessary(request);
        return denied != null ? denied : ReturnResult.ok(orderPartitionConfigService.updateConfig(businessType, dto));
    }

    @Operation(summary = "预览分表策略")
    @GetMapping("/partitions/{businessType}/preview")
    /**
     * Preview
     * @param businessType 业务类型
     * @param request 请求
     * @return 返回值
    */
    public ReturnResult<OrderPartitionPreviewVO> preview(@PathVariable String businessType, HttpServletRequest request) {
        ReturnResult<OrderPartitionPreviewVO> denied = denyIfNecessary(request);
        return denied != null ? denied : ReturnResult.ok(orderPartitionConfigService.preview(businessType));
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
    public ReturnResult<Void> triggerCreate(@PathVariable String businessType, HttpServletRequest request) {
        ReturnResult<Void> denied = denyIfNecessary(request);
        if (denied != null) {
            return denied;
        }
        orderPartitionConfigService.executeAutoCreate(businessType);
        return ReturnResult.ok();
    }

    @Operation(summary = "手动触发迁移")
    @PostMapping("/partitions/{businessType}/migrate")
    /**
     * triggermigrate
     * @param businessType 业务类型
     * @param request 请求
     * @return 返回值
    */
    public ReturnResult<Void> triggerMigrate(@PathVariable String businessType, HttpServletRequest request) {
        ReturnResult<Void> denied = denyIfNecessary(request);
        if (denied != null) {
            return denied;
        }
        orderPartitionConfigService.executeAutoMigrate(businessType);
        return ReturnResult.ok();
    }

    /**
     * 权限校验（非管理员请求直接拒绝）
     *
     * @param request 请求
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
