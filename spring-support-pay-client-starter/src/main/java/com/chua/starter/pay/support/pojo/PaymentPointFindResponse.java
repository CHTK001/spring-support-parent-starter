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
@Schema(description = "查询支付分")
public class PaymentPointFindResponse {

    @SerializedName("out_order_no")
    private String outOrderNo;
    @SerializedName("service_id")
    private String serviceId;
    @SerializedName("appid")
    private String appid;
    @SerializedName("mchid")
    private String mchid;
    @SerializedName("service_introduction")
    private String serviceIntroduction;
    @SerializedName("state")
    private String state;
    @SerializedName("state_description")
    private String stateDescription;
    @SerializedName("post_payments")
    private List<PostPaymentsDTO> postPayments;
    @SerializedName("post_discounts")
    private List<PostDiscountsDTO> postDiscounts;
    @SerializedName("risk_fund")
    private RiskFundDTO riskFund;
    @SerializedName("total_amount")
    private Integer totalAmount;
    @SerializedName("need_collection")
    private Boolean needCollection;
    @SerializedName("collection")
    private CollectionDTO collection;
    @SerializedName("time_range")
    private TimeRangeDTO timeRange;
    @SerializedName("location")
    private LocationDTO location;
    @SerializedName("attach")
    private String attach;
    @SerializedName("notify_url")
    private String notifyUrl;
    @SerializedName("openid")
    private String openid;
    @SerializedName("order_id")
    private String orderId;
    private String error;

    @NoArgsConstructor
    @Data
    public static class RiskFundDTO {
        @SerializedName("name")
        private String name;
        @SerializedName("amount")
        private Integer amount;
        @SerializedName("description")
        private String description;
    }

    @NoArgsConstructor
    @Data
    public static class CollectionDTO {
        @SerializedName("state")
        private String state;
        @SerializedName("total_amount")
        private Integer totalAmount;
        @SerializedName("paying_amount")
        private Integer payingAmount;
        @SerializedName("paid_amount")
        private Integer paidAmount;
        @SerializedName("details")
        private List<DetailsDTO> details;

        @NoArgsConstructor
        @Data
        public static class DetailsDTO {
            @SerializedName("seq")
            private Integer seq;
            @SerializedName("amount")
            private Integer amount;
            @SerializedName("paid_type")
            private String paidType;
            @SerializedName("paid_time")
            private String paidTime;
            @SerializedName("transaction_id")
            private String transactionId;
            @SerializedName("promotion_detail")
            private List<PromotionDetailDTO> promotionDetail;

            @NoArgsConstructor
            @Data
            public static class PromotionDetailDTO {
                @SerializedName("coupon_id")
                private String couponId;
                @SerializedName("name")
                private String name;
                @SerializedName("scope")
                private String scope;
                @SerializedName("type")
                private String type;
                @SerializedName("amount")
                private Integer amount;
                @SerializedName("stock_id")
                private String stockId;
                @SerializedName("wechatpay_contribute")
                private Integer wechatpayContribute;
                @SerializedName("merchant_contribute")
                private Integer merchantContribute;
                @SerializedName("other_contribute")
                private Integer otherContribute;
                @SerializedName("currency")
                private String currency;
                @SerializedName("goods_detail")
                private List<GoodsDetailDTO> goodsDetail;

                @NoArgsConstructor
                @Data
                public static class GoodsDetailDTO {
                    @SerializedName("goods_id")
                    private String goodsId;
                    @SerializedName("quantity")
                    private Integer quantity;
                    @SerializedName("unit_price")
                    private Integer unitPrice;
                    @SerializedName("discount_amount")
                    private Integer discountAmount;
                    @SerializedName("goods_remark")
                    private String goodsRemark;
                }
            }
        }
    }

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
        @SerializedName("start_location")
        private String startLocation;
        @SerializedName("end_location")
        private String endLocation;
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
