package com.chua.payment.support.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chua.common.support.lang.code.ReturnResult;
import com.chua.payment.support.dto.TransactionRecordQueryRequest;
import com.chua.payment.support.entity.TransactionRecord;
import com.chua.payment.support.service.TransactionRecordService;
import com.chua.starter.common.support.lang.ReturnPageResult;
import com.chua.starter.mybatis.utils.PageResultUtils;
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

      /**
       * 交易流水服务
       */
    private final TransactionRecordService transactionRecordService;

    @GetMapping("/{id}")
      /**
       * 按id获取
       *
       * @param id 标识
       * @return 按id获取的结果
       */
    public ReturnResult<TransactionRecord> getById(@PathVariable Long id) {
        TransactionRecord record = transactionRecordService.getById(id);
        return record != null ? ReturnResult.ok(record) : ReturnResult.error("流水记录不存在");
    }

    @GetMapping("/order/{orderNo}")
      /**
       * 按订单号查询列表
       * @param orderNo 订单号
       * @param pageNum pagenum
       * @param pageSize page大小
       * @return 交易流水分页结果
       */
    public ReturnPageResult<TransactionRecord> listByOrderNo(@PathVariable String orderNo,
                                                               @RequestParam(defaultValue = "1") int pageNum,
                                                               @RequestParam(defaultValue = "10") int pageSize) {
        return PageResultUtils.ok(transactionRecordService.listByOrderNo(orderNo, pageNum, pageSize));
    }

    @GetMapping("/merchant/{merchantId}")
      /**
       * 按商户查询列表
       * @param merchantId merchantid
       * @param pageNum pagenum
       * @param pageSize page大小
       * @return 交易流水分页结果
       */
    public ReturnPageResult<TransactionRecord> listByMerchantId(@PathVariable Long merchantId,
                                                                  @RequestParam(defaultValue = "1") int pageNum,
                                                                  @RequestParam(defaultValue = "10") int pageSize) {
        return PageResultUtils.ok(transactionRecordService.listByMerchantId(merchantId, pageNum, pageSize));
    }

    @GetMapping("/page")
      /**
       * 分页查询交易流水。
       *
       * @param pageNum         页码（默认 1）
       * @param pageSize        每页条数（默认 10）
       * @param merchantId      商户标识（可选）
       * @param orderNo         订单号（可选）
       * @param transactionType 交易类型（可选）
       * @param status          流水状态（可选）
       * @return 交易流水分页结果
       */
    public ReturnPageResult<TransactionRecord> page(@RequestParam(defaultValue = "1") int pageNum,
                                                      @RequestParam(defaultValue = "10") int pageSize,
                                                      @RequestParam(required = false) Long merchantId,
                                                      @RequestParam(required = false) String orderNo,
                                                      @RequestParam(required = false) String transactionType,
                                                      @RequestParam(required = false) Integer status) {
        Page<TransactionRecord> page = transactionRecordService.page(new TransactionRecordQueryRequest(pageNum, pageSize, merchantId
            , orderNo, transactionType, status));
        return PageResultUtils.ok(page);
    }
}
