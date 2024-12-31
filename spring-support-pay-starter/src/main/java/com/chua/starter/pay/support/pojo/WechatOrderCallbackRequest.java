package com.chua.starter.pay.support.pojo;


import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 微信支付通知回调接收
 * @author Administrator
 */
@JacksonXmlRootElement
@ApiModel("微信支付通知回调接收")
@Data
public class WechatOrderCallbackRequest implements Serializable {

    @JacksonXmlProperty(localName = "return_code")
    @ApiModelProperty("此字段是通信标识，非交易标识，交易是否成功需要查看result_code来判断")
    private String returnCode;


    @JacksonXmlProperty(localName = "return_msg")
    @ApiModelProperty("返回信息，如非空，为错误原因签名失败参数格式校验错误")
    private String returnMsg;


    @JacksonXmlProperty(localName = "appid")
    @ApiModelProperty("微信分配的小程序ID")
    private String appId;

    @JacksonXmlProperty(localName = "mch_id")
    @ApiModelProperty("微信支付分配的商户号")
    private String mchId;

    @JacksonXmlProperty(localName = "device_info")
    @ApiModelProperty("微信支付分配的终端设备号")
    private String deviceInfo;

    @JacksonXmlProperty(localName = "nonce_str")
    @ApiModelProperty("随机字符串，长度要求在32位以内")
    private String nonceStr;

    @JacksonXmlProperty(localName = "sign")
    @ApiModelProperty("通过签名算法计算得出的签名值")
    private String sign;

    @JacksonXmlProperty(localName = "result_code")
    @ApiModelProperty("SUCCESS/FAIL")
    private String resultCode;

    @JacksonXmlProperty(localName = "err_code")
    @ApiModelProperty("通过签名算法计算得出的签名值")
    private String errCode;

    @JacksonXmlProperty(localName = "err_code_des")
    @ApiModelProperty("错误返回的信息描述")
    private String errCodeDes;

    @JacksonXmlProperty(localName = "openid")
    @ApiModelProperty("用户在商户appid下的唯一标识")
    private String openId;


    @JacksonXmlProperty(localName = "bank_type")
    @ApiModelProperty("用户在商户appid下的唯一标识")
    private String bankType;

    @JacksonXmlProperty(localName = "total_fee")
    @ApiModelProperty("订单金额")
    private int totalFee;

    @JacksonXmlProperty(localName = "cash_fee")
    @ApiModelProperty("订单金额")
    private int cashFee;

    @JacksonXmlProperty(localName = "time_end")
    @ApiModelProperty("支付完成时间，格式为yyyyMMddHHmmss")
    private String timeEnd;

    @JacksonXmlProperty(localName = "out_trade_no")
    @ApiModelProperty("商户系统内部订单号，要求32个字符内，只能是数字、大小写字母_-|*且在同一个商户号下唯一")
    private String outTradeNo;

    @JacksonXmlProperty(localName = "trade_type")
    @ApiModelProperty("小程序取值如下：JSAPI")
    private String tradeType;

    @JacksonXmlProperty(localName = "transaction_id")
    @ApiModelProperty("微信的订单号")
    private String transactionId;


}
