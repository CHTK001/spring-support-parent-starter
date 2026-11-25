package com.chua.payment.support.controller;

import com.chua.payment.support.dto.CouponIssueDTO;
import com.chua.payment.support.dto.CouponQueryDTO;
import com.chua.payment.support.service.CouponService;
import com.chua.payment.support.support.PaymentAccountPrincipalResolver;
import com.chua.payment.support.vo.CouponVO;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.lang.ReturnPageResult;
import com.chua.starter.mybatis.utils.PageResultUtils;
import com.chua.starter.oauth.client.support.user.UserResume;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户优惠券控制器：发放（管理员）、分页查询（管理员）、名下券查询（当前账户）
 *
 * @author CH
 * @since 4.0.0
 */
@Tag(name = "用户优惠券管理", description = "优惠券发放、查询与名下券列表")
@RestController
@RequestMapping("/coupon")
@RequiredArgsConstructor
public class CouponController {

    /**
     * 用户优惠券服务
     */
    private final CouponService couponService;
    /**
     * Principal解析器
     */
    private final PaymentAccountPrincipalResolver principalResolver;

    @Operation(summary = "按模板发放优惠券")
    @PostMapping("/issue")
    public ReturnResult<List<CouponVO>> issue(@RequestBody CouponIssueDTO dto, HttpServletRequest request) {
        ReturnResult<List<CouponVO>> denied = denyIfNecessary(request);
        return denied != null ? denied : ReturnResult.ok(couponService.issue(dto));
    }

    @Operation(summary = "分页查询用户优惠券")
    @GetMapping("/page")
    public ReturnPageResult<CouponVO> page(@RequestParam(defaultValue = "1") int pageNum,
                                           @RequestParam(defaultValue = "10") int pageSize,
                                           @RequestParam(required = false) Long merchantId,
                                           @RequestParam(required = false) Long userId,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String couponNo,
                                           HttpServletRequest request) {
        ReturnPageResult<CouponVO> denied = denyPageIfNecessary(request);
        if (denied != null) {
            return denied;
        }
        return PageResultUtils.ok(couponService.page(
                new CouponQueryDTO(pageNum, pageSize, merchantId, userId, status, couponNo)));
    }

    @Operation(summary = "查询当前账户名下优惠券")
    @GetMapping("/mine")
    public ReturnResult<List<CouponVO>> mine(@RequestParam Long merchantId,
                                             @RequestParam(required = false) String status,
                                             HttpServletRequest request) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return ReturnResult.error("401", "未获取到当前账户");
        }
        return ReturnResult.ok(couponService.listByUser(merchantId, userId, status));
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

    /**
     * 分页接口权限校验（非管理员请求直接拒绝）
     *
     * @param request 请求
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
