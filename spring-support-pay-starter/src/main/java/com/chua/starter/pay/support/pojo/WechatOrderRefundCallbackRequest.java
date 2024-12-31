package com.chua.starter.pay.support.pojo;


import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import com.wechat.pay.java.service.refund.model.Refund;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 微信支付通知回调接收
 * @author Administrator
 */
@JacksonXmlRootElement
@ApiModel("微信退款通知回调接收")
@Data
public class WechatOrderRefundCallbackRequest implements Serializable {

    @ApiModelProperty("通知的唯一ID。")
    private String id;


    @ApiModelProperty("格式为yyyy-MM-DDTHH:mm:ss+TIMEZONE")
    private String create_time;


    @ApiModelProperty("通知的类型:REFUND.SUCCESS：退款成功通知\n" +
            "REFUND.ABNORMAL：退款异常通知\n" +
            "REFUND.CLOSED：退款关闭通知")
    private String resource_type;


    @JacksonXmlProperty(localName = "mch_id")
    @ApiModelProperty("微信支付分配的商户号")
    private String event_type;

    @ApiModelProperty("通知简要说明。")
    private String summary;

    @ApiModelProperty("加密信息请用商户密钥")
    private Object resource;
    @ApiModelProperty("解密后参数")
    private Refund refund;



}
