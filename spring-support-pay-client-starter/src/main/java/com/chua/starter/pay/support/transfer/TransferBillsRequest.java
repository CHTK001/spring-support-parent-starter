package com.chua.starter.pay.support.transfer;

import com.google.gson.annotations.SerializedName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 微信转账请求实体类
 * @author CH
 * @since 2025/4/22 10:42
 */
@NoArgsConstructor
@Data
public class TransferBillsRequest {

    /**
     * 微信应用ID，用于标识发起转账的微信应用（必填）
     */
    @SerializedName("appid")
    private String appid;

    /**
     * 外部账单号，用于标识外部系统的账单（必填）
     */
    @SerializedName("out_bill_no")
    private String outBillNo;

    /**
     * 转账场景ID，用于标识转账的具体场景（必填）
     */
    @SerializedName("transfer_scene_id")
    private String transferSceneId;

    /**
     * 用户OpenID，接收转账的微信用户唯一标识（必填）
     */
    @SerializedName("openid")
    private String openid;

    /**
     * 用户姓名，接收转账的微信用户姓名（非必填）
     */
    @SerializedName("user_name")
    private String userName;

    /**
     * 转账金额，单位为分（必填）
     */
    @SerializedName("transfer_amount")
    private Integer transferAmount;

    /**
     * 转账备注，用于描述转账的具体信息（非必填）
     */
    @SerializedName("transfer_remark")
    private String transferRemark;

    /**
     * 通知URL，用于接收微信转账结果通知（必填）
     */
    @SerializedName("notify_url")
    private String notifyUrl;

    /**
     * 用户接收感知信息，用于描述用户接收转账的感知方式（非必填）
     */
    @SerializedName("user_recv_perception")
    private String userRecvPerception;

    /**
     * 转账场景报告信息列表，包含与转账场景相关的报告信息（必填）
     */
    @SerializedName("transfer_scene_report_infos")
    private List<TransferSceneReportInfo> transferSceneReportInfo;

    /**
     * 转账场景报告信息实体类
     */
    @NoArgsConstructor
    @Data
    public static class TransferSceneReportInfo {
        /**
         * 信息类型，用于标识报告信息的类型（必填）
         */
        @SerializedName("info_type")
        private String infoType;

        /**
         * 信息内容，包含报告信息的具体内容（必填）
         */
        @SerializedName("info_content")
        private String infoContent;
    }
}
