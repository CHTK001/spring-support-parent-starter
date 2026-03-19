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
    
    /**
     * 根据ID查询流水
     */
    @GetMapping("/{id}")
    public Result<TransactionRecord> getById(@PathVariable Long id) {
        TransactionRecord record = transactionRecordService.getById(id);
        return record != null ? Result.success(record) : Result.error("流水记录不存在");
    }
    
    /**
     * 根据订单号查询流水列表
     */
    @GetMapping("/order/{orderNo}")
    public Result<PageResult<TransactionRecord>> listByOrderNo(
            @PathVariable String orderNo,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        Page<TransactionRecord> page = transactionRecordService.listByOrderNo(orderNo, pageNum, pageSize);
        return Result.success(PageResult.of(page));
    }
    
    /**
     * 根据商户ID查询流水列表
     */
    @GetMapping("/merchant/{merchantId}")
    public Result<PageResult<TransactionRecord>> listByMerchantId(
            @PathVariable Long merchantId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        Page<TransactionRecord> page = transactionRecordService.listByMerchantId(merchantId, pageNum, pageSize);
        return Result.success(PageResult.of(page));
    }
    
    /**
     * 分页查询流水列表
     */
    @GetMapping("/page")
    public Result<PageResult<TransactionRecord>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        Page<TransactionRecord> page = transactionRecordService.page(pageNum, pageSize);
        return Result.success(PageResult.of(page));
    }
}
