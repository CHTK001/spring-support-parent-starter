package com.chua.payment.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.payment.support.dto.ReconciliationDiffQueryDTO;
import com.chua.payment.support.dto.ReconciliationStatementQueryDTO;
import com.chua.payment.support.dto.ReconciliationTriggerDTO;
import com.chua.payment.support.service.ReconciliationService;
import com.chua.payment.support.support.PaymentAccountPrincipalResolver;
import com.chua.payment.support.vo.ReconciliationDiffVO;
import com.chua.payment.support.vo.ReconciliationStatementVO;
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

import java.time.LocalDate;
import java.util.List;

/**
 * 渠道对账控制器，提供对账触发与对账单/差异明细查询入口。
 *
 * @author CH
 * @since 4.0.0
 */
@Tag(name = "支付渠道对账管理", description = "渠道账单对账触发、对账单与差异明细查询")
@RestController
@RequestMapping("/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    /**
     * 渠道对账服务
     */
    private final ReconciliationService reconciliationService;
    /**
     * Principal解析器
     */
    private final PaymentAccountPrincipalResolver principalResolver;

    @Operation(summary = "触发渠道对账", description = "按账单日期下载渠道账单并与本地流水比对，账单日期缺省取昨日")
    @PostMapping("/run")
    public ReturnResult<List<ReconciliationStatementVO>> reconcile(
            @RequestBody(required = false) ReconciliationTriggerDTO dto,
            HttpServletRequest request) {
        ReturnResult<List<ReconciliationStatementVO>> denied = denyIfNecessary(request);
        return denied != null ? denied : ReturnResult.ok(reconciliationService.reconcile(dto));
    }

    @Operation(summary = "分页查询对账单")
    @GetMapping("/statements/page")
    public ReturnPageResult<ReconciliationStatementVO> pageStatements(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) String channelType,
            @RequestParam(required = false) LocalDate billDate,
            @RequestParam(required = false) LocalDate billDateFrom,
            @RequestParam(required = false) LocalDate billDateTo,
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        ReturnPageResult<ReconciliationStatementVO> denied = denyPageIfNecessary(request);
        if (denied != null) {
            return denied;
        }
        return PageResultUtils.ok(reconciliationService.pageStatements(
                new ReconciliationStatementQueryDTO(pageNum, pageSize, merchantId, channelType,
                        billDate, billDateFrom, billDateTo, status)));
    }

    @Operation(summary = "查询对账单详情")
    @GetMapping("/statements/{statementNo}")
    public ReturnResult<ReconciliationStatementVO> getStatement(@PathVariable String statementNo,
                                                                HttpServletRequest request) {
        ReturnResult<ReconciliationStatementVO> denied = denyIfNecessary(request);
        return denied != null ? denied : ReturnResult.ok(reconciliationService.getByStatementNo(statementNo));
    }

    @Operation(summary = "查询对账单差异明细列表")
    @GetMapping("/statements/{statementNo}/diffs")
    public ReturnResult<List<ReconciliationDiffVO>> listDiffs(@PathVariable String statementNo,
                                                              HttpServletRequest request) {
        ReturnResult<List<ReconciliationDiffVO>> denied = denyIfNecessary(request);
        return denied != null ? denied : ReturnResult.ok(reconciliationService.listDiffsByStatementNo(statementNo));
    }

    @Operation(summary = "分页查询对账差异明细")
    @GetMapping("/diffs/page")
    public ReturnPageResult<ReconciliationDiffVO> pageDiffs(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) Long statementId,
            @RequestParam(required = false) String statementNo,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) String channelType,
            @RequestParam(required = false) LocalDate billDate,
            @RequestParam(required = false) String diffType,
            HttpServletRequest request) {
        ReturnPageResult<ReconciliationDiffVO> denied = denyPageIfNecessary(request);
        if (denied != null) {
            return denied;
        }
        return PageResultUtils.ok(reconciliationService.pageDiffs(
                new ReconciliationDiffQueryDTO(pageNum, pageSize, statementId, statementNo,
                        merchantId, channelType, billDate, diffType)));
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
