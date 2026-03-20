package com.chua.payment.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.common.PageResult;
import com.chua.payment.support.common.Result;
import com.chua.payment.support.entity.TransactionRecord;
import com.chua.payment.support.service.TransactionRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 交易流水控制器
 */
@RestController
@RequestMapping("/api/transaction")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionRecordService transactionRecordService;

    @GetMapping("/{id}")
    public Result<TransactionRecord> getById(@PathVariable Long id) {
        TransactionRecord record = transactionRecordService.getById(id);
        return record != null ? Result.success(record) : Result.error("流水记录不存在");
    }

    @GetMapping("/order/{orderNo}")
    public Result<PageResult<TransactionRecord>> listByOrderNo(@PathVariable String orderNo,
                                                               @RequestParam(defaultValue = "1") int pageNum,
                                                               @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(PageResult.of(transactionRecordService.listByOrderNo(orderNo, pageNum, pageSize)));
    }

    @GetMapping("/merchant/{merchantId}")
    public Result<PageResult<TransactionRecord>> listByMerchantId(@PathVariable Long merchantId,
                                                                  @RequestParam(defaultValue = "1") int pageNum,
                                                                  @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(PageResult.of(transactionRecordService.listByMerchantId(merchantId, pageNum, pageSize)));
    }

    @GetMapping("/page")
    public Result<PageResult<TransactionRecord>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                      @RequestParam(defaultValue = "10") int pageSize,
                                                      @RequestParam(required = false) Long merchantId,
                                                      @RequestParam(required = false) String orderNo,
                                                      @RequestParam(required = false) String transactionType,
                                                      @RequestParam(required = false) Integer status) {
        Page<TransactionRecord> page = transactionRecordService.page(pageNum, pageSize, merchantId, orderNo, transactionType, status);
        return Result.success(PageResult.of(page));
    }
}
