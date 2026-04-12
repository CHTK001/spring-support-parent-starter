package com.chua.payment.support.controller.account;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.common.PageResult;
import com.chua.payment.support.common.Result;
import com.chua.payment.support.entity.TransactionRecord;
import com.chua.payment.support.service.AccountPaymentQueryService;
import com.chua.payment.support.support.PaymentAccountPrincipalResolver;
import com.chua.payment.support.vo.OrderStateLogVO;
import com.chua.payment.support.vo.OrderVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
public class CurrentAccountOrderController {

    private final AccountPaymentQueryService accountPaymentQueryService;
    private final PaymentAccountPrincipalResolver principalResolver;

    @GetMapping("/orders")
    public Result<PageResult<OrderVO>> listOrders(HttpServletRequest request,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  @RequestParam(required = false) String orderNo,
                                                  @RequestParam(required = false) String status) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return Result.error(401, "未获取到当前账号");
        }
        return Result.success(PageResult.of(accountPaymentQueryService.listOrdersByUser(userId, page, size, orderNo, status)));
    }

    @GetMapping("/orders/{id}")
    public Result<OrderVO> getOrder(HttpServletRequest request, @PathVariable Long id) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return Result.error(401, "未获取到当前账号");
        }
        return Result.success(accountPaymentQueryService.getOrderByUser(userId, id));
    }

    @GetMapping("/orders/{id}/logs")
    public Result<List<OrderStateLogVO>> listOrderLogs(HttpServletRequest request, @PathVariable Long id) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return Result.error(401, "未获取到当前账号");
        }
        return Result.success(accountPaymentQueryService.listOrderLogsByUser(userId, id));
    }

    @GetMapping("/transactions")
    public Result<PageResult<TransactionRecord>> listTransactions(HttpServletRequest request,
                                                                  @RequestParam(defaultValue = "1") int pageNum,
                                                                  @RequestParam(defaultValue = "10") int pageSize,
                                                                  @RequestParam(required = false) String orderNo,
                                                                  @RequestParam(required = false) String transactionType,
                                                                  @RequestParam(required = false) Integer status) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return Result.error(401, "未获取到当前账号");
        }
        Page<TransactionRecord> page = accountPaymentQueryService.listTransactionsByUser(userId, pageNum, pageSize, orderNo, transactionType, status);
        return Result.success(PageResult.of(page));
    }
}
