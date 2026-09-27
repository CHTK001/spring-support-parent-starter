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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
/**
 * 当前用户订单自助 API
 * <p>所有接口均校验当前用户身份，userId 从认证上下文获取，不允许客户端传入。
 *
 * @author CH
 * @since 4.0.0
 */

@RestController
@RequestMapping("/account")
@RequiredArgsConstructor
public class CurrentAccountOrderController {

    /**
     * 账户支付查询服务
     */
    private final AccountPaymentQueryService accountPaymentQueryService;
    /**
     * Principal解析器
     */
    private final PaymentAccountPrincipalResolver principalResolver;
    /**
     * 支付订单服务
     */
    private final PaymentOrderService paymentOrderService;

    @GetMapping("/orders")
    /**
     * 订单列表
     * @param request 请求
     * @param page page
     * @param size 大小
     * @param orderNo 订单号
     * @param status 状态
     */
    public Result<PageResult<OrderVO>> listOrders(HttpServletRequest request,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int size,
                                                  @RequestParam(required = false) String orderNo,
                                                  @RequestParam(required = false) String status) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return Result.error(401, "未获取到当前账户");
        }
        return Result.success(PageResult.of(accountPaymentQueryService.listOrdersByUser(new UserOrderQueryRequest(userId, page, size, orderNo, status))));
    }

    @GetMapping("/orders/{id}")
    /**
     * 获取订单
     *
     * @param request 请求
     * @param id 标识
     * @return 获取订单的结果
     */
    public Result<OrderVO> getOrder(HttpServletRequest request, @PathVariable Long id) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return Result.error(401, "未获取到当前账户");
        }
        return Result.success(accountPaymentQueryService.getOrderByUser(userId, id));
    }

    @GetMapping("/orders/{id}/logs")
    /**
     * 订单状态流转日志
     *
     * @param request 请求
     * @param id 标识
     * @return 订单状态流转日志的结果
     */
    public Result<List<OrderStateLogVO>> listOrderLogs(HttpServletRequest request, @PathVariable Long id) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return Result.error(401, "未获取到当前账户");
        }
        return Result.success(accountPaymentQueryService.listOrderLogsByUser(userId, id));
    }

    @GetMapping("/transactions")
    /**
     * 交易流水列表
     * @param request 请求
     * @param pageNum pagenum
     * @param pageSize page大小
     * @param orderNo 订单号
     * @param transactionType 交易类型
     * @param status 状态
     */
    public Result<PageResult<TransactionRecord>> listTransactions(HttpServletRequest request,
                                                                  @RequestParam(defaultValue = "1") int pageNum,
                                                                  @RequestParam(defaultValue = "10") int pageSize,
                                                                  @RequestParam(required = false) String orderNo,
                                                                  @RequestParam(required = false) String transactionType,
                                                                  @RequestParam(required = false) Integer status) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return Result.error(401, "未获取到当前账户");
        }
        Page<TransactionRecord> page = accountPaymentQueryService.listTransactionsByUser(new UserTransactionQueryRequest(userId, pageNum, pageSize, orderNo, transactionType, status));
        return Result.success(PageResult.of(page));
    }

    @PutMapping("/orders/{id}/cancel")
    /**
     * 取消订单
     * @param request 请求
     * @param id 标识
     * @param reason 取消原因
     */
    public Result<Boolean> cancelOrder(HttpServletRequest request,
                                       @PathVariable Long id,
                                       @RequestBody(required = false) String reason) {
        Long userId = principalResolver.resolveUserId(request);
        if (userId == null) {
            return Result.error(401, "未获取到当前账户");
        }
        // 用户取消：已支付则走退款取消，未支付则直接取消
        return Result.success(paymentOrderService.cancelOrder(id, "user", reason, true));
    }
}
