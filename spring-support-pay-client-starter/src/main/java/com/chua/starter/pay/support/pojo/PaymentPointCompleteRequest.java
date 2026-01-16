package com.chua.starter.pay.support.pojo;

import com.google.gson.annotations.SerializedName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 创建支付分
 * @author CH
 * @since 2025/5/26 9:11
 */
@NoArgsConstructor
@Data
@Schema(description = "完成支付分")
public class PaymentPointCompleteRequest {

    @SerializedName("appid")
    private String appid;
    @SerializedName("service_id")
    private String serviceId;
    @SerializedName("post_payments")
    private List<PostPaymentsDTO> postPayments;
    @SerializedName("post_discounts")
    private List<PostDiscountsDTO> postDiscounts;
    @SerializedName("total_amount")
    private Integer totalAmount;
    @SerializedName("time_range")
    private TimeRangeDTO timeRange;
    @SerializedName("location")
    private LocationDTO location;
    @SerializedName("profit_sharing")
    private Boolean profitSharing;
    @SerializedName("goods_tag")
    private String goodsTag;
    @SerializedName("device")
    private DeviceDTO device;

    @NoArgsConstructor
    @Data
    public static class TimeRangeDTO {
        @SerializedName("start_time")
        private String startTime;
        @SerializedName("end_time")
        private String endTime;
        @SerializedName("start_time_remark")
        private String startTimeRemark;
        @SerializedName("end_time_remark")
        private String endTimeRemark;
    }

    @NoArgsConstructor
    @Data
    public static class LocationDTO {
        @SerializedName("end_location")
        private String endLocation;
    }

    @NoArgsConstructor
    @Data
    public static class DeviceDTO {
        @SerializedName("start_device_id")
        private String startDeviceId;
        @SerializedName("end_device_id")
        private String endDeviceId;
        @SerializedName("materiel_no")
        private String materielNo;
    }

    @NoArgsConstructor
    @Data
    public static class PostPaymentsDTO {
        @SerializedName("name")
        private String name;
        @SerializedName("amount")
        private Integer amount;
        @SerializedName("description")
        private String description;
        @SerializedName("count")
        private Integer count;
    }

    @NoArgsConstructor
    @Data
    public static class PostDiscountsDTO {
        @SerializedName("name")
        private String name;
        @SerializedName("description")
        private String description;
        @SerializedName("amount")
        private Integer amount;
        @SerializedName("count")
        private Integer count;
    }
}
