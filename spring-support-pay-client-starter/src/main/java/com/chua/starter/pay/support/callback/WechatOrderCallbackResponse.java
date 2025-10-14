package com.chua.starter.pay.support.callback;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 微信回调响应
 * @author CH
 * @since 2024/12/30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WechatOrderCallbackResponse implements Serializable {
    /**
     * 返回状态码
     */
    @JsonProperty("return_code")
    private String returnCode;
    /**
     * 返回信息
     */
    @JsonProperty("return_msg")
    private String returnMsg;

    /**
     * 数据
     */
    private Object data;

}
