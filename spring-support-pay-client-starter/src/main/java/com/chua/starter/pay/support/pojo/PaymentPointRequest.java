package com.chua.starter.pay.support.pojo;

import com.google.gson.annotations.SerializedName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 创建支付分
 *
 * @author CH
 * @since 2025/5/26 9:11
 */
@NoArgsConstructor
@Data
@Schema(description = "创建支付分")
public class PaymentPointRequest {

    /**
     * 商户服务订单号
     */
    @SerializedName("out_order_no")
    @Schema(description = "商户服务订单号")
    private String outOrderNo;

    /**
     * 应用ID
     */
    @SerializedName("appid")
    @Schema(description = "应用ID")
    private String appid;

    /**
     * 服务ID
     */
    @SerializedName("service_id")
    @Schema(description = "服务ID")
    private String serviceId;

    /**
     * 服务信息描述
     */
    @SerializedName("service_introduction")
    @Schema(description = "服务信息描述")
    private String serviceIntroduction;

    /**
     * 后付费项目列表
     */
    @SerializedName("post_payments")
    @Schema(description = "后付费项目列表")
    private List<PostPaymentsDTO> postPayments;

    /**
     * 后付费优惠列表
     */
    @SerializedName("post_discounts")
    @Schema(description = "后付费优惠列表")
    private List<PostDiscountsDTO> postDiscounts;

    /**
     * 服务时间范围
     */
    @SerializedName("time_range")
    @Schema(description = "服务时间范围")
    private TimeRangeDTO timeRange;

    /**
     * 服务位置信息
     */
    @SerializedName("location")
    @Schema(description = "服务位置信息")
    private LocationDTO location;

    /**
     * 风险金信息
     */
    @SerializedName("risk_fund")
    @Schema(description = "风险金信息")
    private RiskFundDTO riskFund;

    /**
     * 附加数据
     */
    @SerializedName("attach")
    @Schema(description = "附加数据")
    private String attach;

    /**
     * 通知URL
     */
    @SerializedName("notify_url")
    @Schema(description = "通知URL")
    private String notifyUrl;

    /**
     * 是否需要用户确认
     */
    @SerializedName("need_user_confirm")
    @Schema(description = "是否需要用户确认")
    private Boolean needUserConfirm;

    /**
     * 设备信息
     */
    @SerializedName("device")
    @Schema(description = "设备信息")
    private DeviceDTO device;

    /**
     * 时间范围信息
     */
    @NoArgsConstructor
    @Data
    @Schema(description = "时间范围信息")
    public static class TimeRangeDTO {

        /**
         * 开始时间
         */
        @SerializedName("start_time")
        @Schema(description = "开始时间")
        private String startTime;

        /**
         * 结束时间
         */
        @SerializedName("end_time")
        @Schema(description = "结束时间")
        private String endTime;

        /**
         * 开始时间备注
         */
        @SerializedName("start_time_remark")
        @Schema(description = "开始时间备注")
        private String startTimeRemark;

        /**
         * 结束时间备注
         */
        @SerializedName("end_time_remark")
        @Schema(description = "结束时间备注")
        private String endTimeRemark;
    }

    /**
     * 位置信息
     */
    @NoArgsConstructor
    @Data
    @Schema(description = "位置信息")
    public static class LocationDTO {

        /**
         * 起始位置
         */
        @SerializedName("start_location")
        @Schema(description = "起始位置")
        private String startLocation;

        /**
         * 结束位置
         */
        @SerializedName("end_location")
        @Schema(description = "结束位置")
        private String endLocation;
    }

    /**
     * 风险金信息
     */
    @NoArgsConstructor
    @Data
    @Schema(description = "风险金信息")
    public static class RiskFundDTO {

        /**
         * 风险金名称
         */
        @SerializedName("name")
        @Schema(description = "风险金名称")
        private String name;

        /**
         * 风险金额度(单位:分)
         */
        @SerializedName("amount")
        @Schema(description = "风险金额度(单位:分)")
        private Integer amount;

        /**
         * 风险金说明
         */
        @SerializedName("description")
        @Schema(description = "风险金说明")
        private String description;
    }

    /**
     * 设备信息
     */
    @NoArgsConstructor
    @Data
    @Schema(description = "设备信息")
    public static class DeviceDTO {

        /**
         * 起始设备ID
         */
        @SerializedName("start_device_id")
        @Schema(description = "起始设备ID")
        private String startDeviceId;

        /**
         * 结束设备ID
         */
        @SerializedName("end_device_id")
        @Schema(description = "结束设备ID")
        private String endDeviceId;

        /**
         * 物料编号
         */
        @SerializedName("materiel_no")
        @Schema(description = "物料编号")
        private String materielNo;
    }

    /**
     * 后付费项目信息
     */
    @NoArgsConstructor
    @Data
    @Schema(description = "后付费项目信息")
    public static class PostPaymentsDTO {

        /**
         * 项目名称
         */
        @SerializedName("name")
        @Schema(description = "项目名称")
        private String name;

        /**
         * 金额(单位:分)
         */
        @SerializedName("amount")
        @Schema(description = "金额(单位:分)")
        private Integer amount;

        /**
         * 项目说明
         */
        @SerializedName("description")
        @Schema(description = "项目说明")
        private String description;

        /**
         * 数量
         */
        @SerializedName("count")
        @Schema(description = "数量")
        private Integer count;
    }

    /**
     * 后付费优惠信息
     */
    @NoArgsConstructor
    @Data
    @Schema(description = "后付费优惠信息")
    public static class PostDiscountsDTO {

        /**
         * 优惠名称
         */
        @SerializedName("name")
        @Schema(description = "优惠名称")
        private String name;

        /**
         * 优惠说明
         */
        @SerializedName("description")
        @Schema(description = "优惠说明")
        private String description;

        /**
         * 优惠数量
         */
        @SerializedName("count")
        @Schema(description = "优惠数量")
        private Integer count;
    }
}
