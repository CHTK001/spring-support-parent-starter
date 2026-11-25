package com.chua.payment.support.controller;

import com.chua.common.support.lang.code.ReturnResult;
import com.chua.payment.support.dto.ProfitSharingOrderQueryDTO;
import com.chua.payment.support.dto.ProfitSharingReturnDTO;
import com.chua.payment.support.service.ProfitSharingService;
import com.chua.payment.support.support.PaymentAccountPrincipalResolver;
import com.chua.payment.support.vo.ProfitSharingOrderVO;
import com.chua.payment.support.vo.ProfitSharingReturnVO;
import com.chua.starter.common.support.lang.ReturnPageResult;
import com.chua.starter.mybatis.utils.PageResultUtils;
import com.chua.starter.oauth.client.support.user.UserResume;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 支付分账控制器，提供分账单查询与分账回退入口。
 *
 * @author CH
 * @since 4.0.0
 */
@Tag(name = "支付分账管理", description = "分账单分页/详情查询与分账回退")
@RestController
@RequestMapping("/profit-sharing")
@RequiredArgsConstructor
public class ProfitSharingController {

    /**
     * 分账服务
     */
    private final ProfitSharingService profitSharingService;
    /**
     * Principal解析器
     */
    private final PaymentAccountPrincipalResolver principalResolver;

    @Operation(summary = "分页查询分账单")
    @GetMapping("/orders/page")
    public ReturnPageResult<ProfitSharingOrderVO> pageOrders(@RequestParam(defaultValue = "1") int pageNum,
                                                             @RequestParam(defaultValue = "10") int pageSize,
                                                             @RequestParam(required = false) Long merchantId,
                                                             @RequestParam(required = false) String sharingOrderNo,
                                                             @RequestParam(required = false) String orderNo,
                                                             @RequestParam(required = false) String status,
                                                             HttpServletRequest request) {
        ReturnPageResult<ProfitSharingOrderVO> denied = denyPageIfNecessary(request);
        if (denied != null) {
            return denied;
        }
        return PageResultUtils.ok(profitSharingService.pageOrders(
                new ProfitSharingOrderQueryDTO(pageNum, pageSize, merchantId, sharingOrderNo, orderNo, status)));
    }

    @Operation(summary = "查询分账单详情")
    @GetMapping("/orders/{sharingOrderNo}")
    public ReturnResult<ProfitSharingOrderVO> getOrder(@PathVariable String sharingOrderNo,
                                                       HttpServletRequest request) {
        ReturnResult<ProfitSharingOrderVO> denied = denyIfNecessary(request);
        return denied != null ? denied : ReturnResult.ok(profitSharingService.getBySharingOrderNo(sharingOrderNo));
    }

    @Operation(summary = "发起分账回退")
    @PostMapping("/returns")
    public ReturnResult<ProfitSharingReturnVO> returnSharing(@RequestBody ProfitSharingReturnDTO dto,
                                                             HttpServletRequest request) {
        ReturnResult<ProfitSharingReturnVO> denied = denyIfNecessary(request);
        return denied != null ? denied : ReturnResult.ok(profitSharingService.returnSharing(dto));
    }

    @Operation(summary = "查询分账回退记录")
    @GetMapping("/orders/{sharingOrderNo}/returns")
    public ReturnResult<List<ProfitSharingReturnVO>> listReturns(@PathVariable String sharingOrderNo,
                                                                 HttpServletRequest request) {
        ReturnResult<List<ProfitSharingReturnVO>> denied = denyIfNecessary(request);
        return denied != null ? denied : ReturnResult.ok(profitSharingService.listReturns(sharingOrderNo));
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

    /**
     * 分页接口权限校验（非管理员请求直接拒绝）
     *
     * @param request 请求
     * @param <T>     响应数据类型
     * @return 拒绝响应；校验通过返回 {@code null}
     */
    private <T> ReturnPageResult<T> denyPageIfNecessary(HttpServletRequest request) {
        UserResume userResume = principalResolver.resolve(request);
        if (userResume == null) {
            return ReturnPageResult.error("未获取到当前账户");
        }
        if (!principalResolver.isAdmin(userResume)) {
            return ReturnPageResult.error("仅超管和管理员可访问");
        }
        return null;
    }
}
