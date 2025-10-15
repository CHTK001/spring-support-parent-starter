package com.chua.starter.pay.support.transfer;

import com.google.gson.annotations.SerializedName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信转账响应实体类
 * @author CH
 * @since 2025/4/22 10:54
 */
@NoArgsConstructor
@Data
public class TransferBillsStatusResponse {

    /**
     * 商户号
     */
    @SerializedName("mch_id")
    @Schema(description = "商户号")
    private String mchId;

    /**
     * 商户转账单号
     */
    @SerializedName("out_bill_no")
    @Schema(description = "商户转账单号")
    private String outBillNo;

    /**
     * 微信转账单号
     */
    @SerializedName("transfer_bill_no")
    @Schema(description = "微信转账单号")
    private String transferBillNo;

    /**
     * 应用ID
     */
    @SerializedName("appid")
    @Schema(description = "应用ID")
    private String appid;

    /**
     * 转账状态
     */
    @SerializedName("state")
    @Schema(description = "转账状态")
    private String state;

    /**
     * 转账金额（单位：分）
     */
    @SerializedName("transfer_amount")
    @Schema(description = "转账金额（单位：分）")
    private Integer transferAmount;

    /**
     * 转账备注
     */
    @SerializedName("transfer_remark")
    @Schema(description = "转账备注")
    private String transferRemark;

    /**
     * 失败原因
     */
    @SerializedName("fail_reason")
    @Schema(description = "失败原因")
    private String failReason;

    /**
     * 用户openid
     */
    @SerializedName("openid")
    @Schema(description = "用户openid")
    private String openid;

    /**
     * 收款用户姓名
     */
    @SerializedName("user_name")
    @Schema(description = "收款用户姓名")
    private String userName;

    /**
     * 创建时间
     */
    @SerializedName("create_time")
    @Schema(description = "创建时间")
    private String createTime;

    /**
     * 更新时间
     */
    @SerializedName("update_time")
    @Schema(description = "更新时间")
    private String updateTime;
}
