package com.chua.starter.pay.support.transfer;

import lombok.Data;

import java.util.List;

/**
 * @author CH
 * @since 2025/4/16 9:59
 */
@Data
public class TransferBatchEntity {
    private String mchid;
    private String out_batch_no;
    private String batch_id;
    private String batch_status;
    private String batch_name;
    private String batch_remark;
    private String create_time;
    private String finish_time;
    private String fail_reason;
    private Integer total_amount;
    private Integer total_num;
    private List<TransferDetail> transfer_detail_list;

    @Data
    public static class TransferDetail {
        private String out_detail_no;
        private String detail_status;
        private Integer transfer_amount;
        private String fail_reason;
        private String openid;
        private String user_name;
        private String transfer_remark;
    }
}