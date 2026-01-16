package com.chua.starter.pay.support.pojo;

import com.chua.starter.pay.support.enums.WechatTransferBatchStatus;
import lombok.Builder;
import lombok.Data;

import java.util.LinkedList;
import java.util.List;

/**
 * 转账响应
 * @author CH
 * @since 2025/4/16 9:34
 */
@Data
public class CreateTransferV2Response {

    /**
     * 转账批次号
     */
    private String outBatchNo;

    public CreateTransferV2Response() {
        this.data = new LinkedList<>();
    }

    /**
     * 转账批次
     */
    private List<Batch> data;

    /**
     * 添加转账批次
     * @param batch 转账批次
     * @return this
     */
    public CreateTransferV2Response addBatch(Batch batch) {
        data.add(batch);
        return this;
    }

    /**
     * 转账批次
     */
    @Data
    @Builder
    public static class Batch {

        /**
         * 转账批次号
         */
        private String batchId;

        /**
         * 转账批次号
         */
        private String packageInfo;
        /**
         * 转账批次错误原因
         */
        private String error;

        /**
         * 转账批次状态
         */
        private String status;
        /**
         * 转账批次状态
         */
        private WechatTransferBatchStatus batchStatus;
    }
}
