package com.chua.payment.support.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分账接收方实体类
 *
 * <p>商户在渠道侧登记的分账接收方，按比例参与订单分账。</p>
 *
 * @since 4.0.0
 * @author CH
 */
@Data
@TableName("pay_profit_sharing_receiver")
public class ProfitSharingReceiver {

    @TableId(value = "pay_profit_sharing_receiver_id", type = IdType.AUTO)
    /**
     * 主键ID
     */
    private Long id;

    @TableField("pay_profit_sharing_receiver_merchant_id")
    /**
     * 商户ID
     */
    private Long merchantId;

    @TableField("pay_profit_sharing_receiver_channel_type")
    /**
     * 渠道类型: WECHAT/ALIPAY
     */
    private String channelType;

    @TableField("pay_profit_sharing_receiver_name")
    /**
     * 接收方名称
     */
    private String name;

    @TableField("pay_profit_sharing_receiver_account")
    /**
     * 接收方账号(商户号/支付宝用户号/登录账号)
     */
    private String account;

    @TableField("pay_profit_sharing_receiver_type")
    /**
     * 账号类型: MERCHANT_ID-商户号/PERSONAL_OPENID-个人openid/LOGIN_NAME-登录账号
     */
    private String type;

    @TableField("pay_profit_sharing_receiver_relation_type")
    /**
     * 与商户关系类型: PARTNER-合作方/SERVICE_PROVIDER-服务商/STORE-门店/STAFF-员工
     */
    private String relationType;

    @TableField("pay_profit_sharing_receiver_ratio")
    /**
     * 分账比例(%)
     */
    private BigDecimal ratio;

    @TableField("pay_profit_sharing_receiver_status")
    /**
     * 状态: ENABLED-启用/DISABLED-停用
     */
    private String status;

    @TableField("pay_profit_sharing_receiver_remark")
    /**
     * 备注
     */
    private String remark;

    @TableField("pay_profit_sharing_receiver_deleted")
    /**
     * 逻辑删除
     */
    private Integer deleted;

    @TableField(value = "pay_profit_sharing_receiver_created_at", fill = FieldFill.INSERT)
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    @TableField(value = "pay_profit_sharing_receiver_updated_at", fill = FieldFill.INSERT_UPDATE)
    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
