package com.chua.payment.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.payment.support.common.PageResult;
import com.chua.payment.support.common.Result;
import com.chua.payment.support.dto.TransactionRecordQueryRequest;
import com.chua.payment.support.entity.TransactionRecord;
import com.chua.payment.support.service.TransactionRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
* 交易流水控制器
* @author CH
* @since 4.0.0
 */
@RestController
@RequestMapping("/transaction")
@RequiredArgsConstructor
public class TransactionController {

    /** Transactionrecord服务 */
    private final TransactionRecordService transactionRecordService;

    @GetMapping("/{id}")
    /**
    * 获取byid
    *
    * @param id 标识
    * @return 获取byid的结果
    */
    public Result<TransactionRecord> getById(@PathVariable Long id) {
        TransactionRecord record = transactionRecordService.getById(id);
        return record != null ? Result.success(record) : Result.error("流水记录不存在");
    }

    @GetMapping("/order/{orderNo}")
    /**
    * 列表by订单no
    * @param orderNo 订单no
    * @param pageNum pagenum
    * @param pageSize page大小
    */
    public Result<PageResult<TransactionRecord>> listByOrderNo(@PathVariable String orderNo,
                                                               @RequestParam(defaultValue = "1") int pageNum,
                                                               @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(PageResult.of(transactionRecordService.listByOrderNo(orderNo, pageNum, pageSize)));
    }

    @GetMapping("/merchant/{merchantId}")
    /**
    * 列表bymerchantid
    * @param merchantId merchantid
    * @param pageNum pagenum
    * @param pageSize page大小
    */
    public Result<PageResult<TransactionRecord>> listByMerchantId(@PathVariable Long merchantId,
                                                                  @RequestParam(defaultValue = "1") int pageNum,
                                                                  @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(PageResult.of(transactionRecordService.listByMerchantId(merchantId, pageNum, pageSize)));
    }

    @GetMapping("/page")
    /**
    * Page
    *
    * @param "1" "1"
    * @return page的结果
    */
    public Result<PageResult<TransactionRecord>> page(@RequestParam(defaultValue = "1") int pageNum,
                                                      @RequestParam(defaultValue = "10") int pageSize,
                                                      @RequestParam(required = false) Long merchantId,
                                                      @RequestParam(required = false) String orderNo,
                                                      @RequestParam(required = false) String transactionType,
                                                      @RequestParam(required = false) Integer status) {
        Page<TransactionRecord> page = transactionRecordService.page(new TransactionRecordQueryRequest(pageNum, pageSize, merchantId, orderNo, transactionType, status));
        return Result.success(PageResult.of(page));
    }
}

