package com.chua.payment.support.controller;

import com.chua.payment.support.dto.CouponTemplateDTO;
import com.chua.payment.support.dto.CouponTemplateQueryDTO;
import com.chua.payment.support.service.CouponTemplateService;
import com.chua.payment.support.support.PaymentAccountPrincipalResolver;
import com.chua.payment.support.vo.CouponTemplateVO;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.starter.common.support.lang.ReturnPageResult;
import com.chua.starter.mybatis.utils.PageResultUtils;
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

/**
 * 优惠券模板控制器
 *
 * @author CH
 * @since 4.0.0
 */
@Tag(name = "优惠券模板管理", description = "优惠券模板的创建、更新、删除与查询")
@RestController
@RequestMapping("/coupon-template")
@RequiredArgsConstructor
public class CouponTemplateController {

    /**
     * 优惠券模板服务
     */
    private final CouponTemplateService couponTemplateService;
    /**
     * Principal解析器
     */
    private final PaymentAccountPrincipalResolver principalResolver;

    @Operation(summary = "创建优惠券模板")
    @PostMapping
    public ReturnResult<CouponTemplateVO> create(@RequestBody CouponTemplateDTO dto, HttpServletRequest request) {
        ReturnResult<CouponTemplateVO> denied = denyIfNecessary(request);
        return denied != null ? denied : ReturnResult.ok(couponTemplateService.create(dto));
    }

    @Operation(summary = "更新优惠券模板")
    @PutMapping("/{id}")
    public ReturnResult<CouponTemplateVO> update(@PathVariable Long id, @RequestBody CouponTemplateDTO dto,
                                                 HttpServletRequest request) {
        ReturnResult<CouponTemplateVO> denied = denyIfNecessary(request);
        return denied != null ? denied : ReturnResult.ok(couponTemplateService.update(id, dto));
    }

    @Operation(summary = "删除优惠券模板")
    @DeleteMapping("/{id}")
    public ReturnResult<Boolean> delete(@PathVariable Long id, HttpServletRequest request) {
        ReturnResult<Boolean> denied = denyIfNecessary(request);
        return denied != null ? denied : ReturnResult.ok(couponTemplateService.delete(id));
    }

    @Operation(summary = "查询优惠券模板详情")
    @GetMapping("/{id}")
    public ReturnResult<CouponTemplateVO> get(@PathVariable Long id, HttpServletRequest request) {
        ReturnResult<CouponTemplateVO> denied = denyIfNecessary(request);
        return denied != null ? denied : ReturnResult.ok(couponTemplateService.get(id));
    }

    @Operation(summary = "分页查询优惠券模板")
    @GetMapping("/page")
    public ReturnPageResult<CouponTemplateVO> page(@RequestParam(defaultValue = "1") int pageNum,
                                                   @RequestParam(defaultValue = "10") int pageSize,
                                                   @RequestParam(required = false) Long merchantId,
                                                   @RequestParam(required = false) String name,
                                                   @RequestParam(required = false) String status,
                                                   HttpServletRequest request) {
        ReturnPageResult<CouponTemplateVO> denied = denyPageIfNecessary(request);
        if (denied != null) {
            return denied;
        }
        return PageResultUtils.ok(couponTemplateService.page(
                new CouponTemplateQueryDTO(pageNum, pageSize, merchantId, name, status)));
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
