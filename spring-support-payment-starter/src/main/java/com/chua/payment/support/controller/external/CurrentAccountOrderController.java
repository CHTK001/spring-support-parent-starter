package com.chua.payment.support.controller.external;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.common.PageResult;
import com.chua.payment.support.common.Result;
import com.chua.payment.support.dto.UserOrderQueryRequest;
import com.chua.payment.support.dto.UserTransactionQueryRequest;
import com.chua.payment.support.entity.TransactionRecord;
import com.chua.payment.support.service.AccountPaymentQueryService;
import com.chua.payment.support.service.PaymentOrderService;
import com.chua.payment.support.support.PaymentAccountPrincipalResolver;
import com.chua.payment.support.vo.OrderStateLogVO;
import com.chua.payment.support.vo.OrderVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
/** @author CH */

@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
public class CurrentAccountOrderController {

    /** Accountpaymentquery服务 */
    private final AccountPaymentQueryService accountPaymentQueryService;
    /** Principal解析器 */
    private final PaymentAccountPrincipalResolver principalResolver;
    /** Payment排序服务 */
    private final PaymentOrderService paymentOrderService;

    @GetMapping("/orders")
    /**
     * 列表Orders
     * @param request request
     * @param page page
     * @param size size
     * @param orderNo orderNo
     * @param status status
     */
    public Result<PageResult<OrderVO>> listOrders(HttpServletRequest request,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  @RequestParam(required = false) String orderNo,
                                                  @RequestParam(required = false) String status) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return Result.error(401, "未获取到当前账号");
        }
        return Result.success(PageResult.of(accountPaymentQueryService.listOrdersByUser(new UserOrderQueryRequest(userId, page, size, orderNo, status))));
    }

    @GetMapping("/orders/{id}")
    /** 获取Order */
    public Result<OrderVO> getOrder(HttpServletRequest request, @PathVariable Long id) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return Result.error(401, "未获取到当前账号");
        }
        return Result.success(accountPaymentQueryService.getOrderByUser(userId, id));
    }

    @GetMapping("/orders/{id}/logs")
    /** ListOrderLogs */
    public Result<List<OrderStateLogVO>> listOrderLogs(HttpServletRequest request, @PathVariable Long id) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return Result.error(401, "未获取到当前账号");
        }
        return Result.success(accountPaymentQueryService.listOrderLogsByUser(userId, id));
    }

    @GetMapping("/transactions")
    /**
     * 列表Transactions
     * @param request request
     * @param pageNum pageNum
     * @param pageSize pageSize
     * @param orderNo orderNo
     * @param transactionType transactionType
     * @param status status
     */
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
        Page<TransactionRecord> page = accountPaymentQueryService.listTransactionsByUser(new UserTransactionQueryRequest(userId, pageNum, pageSize, orderNo, transactionType, status));
        return Result.success(PageResult.of(page));
    }

    @PutMapping("/orders/{id}/cancel")
    /**
     * CancelOrder
     * @param request request
     * @param id id
     * @param reason reason
     */
    public Result<Boolean> cancelOrder(HttpServletRequest request,
                                       @PathVariable Long id,
                                       @RequestBody(required = false) String reason) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return Result.error(401, "未获取到当前账号");
        }
        // 用户取消：已支付则走退款取消，未支付则直接取消
        return Result.success(paymentOrderService.cancelOrder(id, "user", reason, true));
    }
}

