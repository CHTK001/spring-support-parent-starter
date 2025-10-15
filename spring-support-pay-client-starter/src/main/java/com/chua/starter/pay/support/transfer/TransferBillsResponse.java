package com.chua.starter.pay.support.transfer;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信转账响应实体类
 * @author CH
 * @since 2025/4/22 10:54
 */
@NoArgsConstructor
@Data
public class TransferBillsResponse {

    /**
     * 外部账单号，用于标识外部系统的账单
     */
    @SerializedName("out_bill_no")
    private String outBillNo;

    /**
     * 转账账单号，微信生成的唯一转账账单标识
     */
    @SerializedName("transfer_bill_no")
    private String transferBillNo;

    /**
     * 账单创建时间，格式为 yyyy-MM-dd HH:mm:ss
     */
    @SerializedName("create_time")
    private String createTime;

    /**
     * 转账状态，可能的值为 SUCCESS（成功）、FAIL（失败）等
     */
    @SerializedName("state")
    private String state;

    /**
     * 转账失败原因，当状态为 FAIL 时，此字段会包含失败的具体原因
     */
    @SerializedName("fail_reason")
    private String failReason;

    /**
     * 套餐信息，包含与转账相关的套餐详情
     */
    @SerializedName("package_info")
    private String packageInfo;
}
