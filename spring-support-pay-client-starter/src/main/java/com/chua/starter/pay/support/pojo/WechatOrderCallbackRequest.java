package com.chua.starter.pay.support.pojo;


import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import io.swagger.annotations.ApiModel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 微信支付通知回调接收
 *
 * @author Administrator
 */
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JacksonXmlRootElement
@ApiModel("支付通知回调接收")
@Data
public class WechatOrderCallbackRequest extends OrderCallbackRequest {


    /**
     * 事件记录的唯一标识符
     */
    private String id;

    /**
     * 事件创建的时间戳，用于记录事件发生的时间
     */
    private String createTime;

    /**
     * 资源类型，标识事件相关的资源种类
     */
    private String resourceType;

    /**
     * 事件类型，描述事件的性质或类别
     */
    private String eventType;

    /**
     * 事件摘要，简要说明事件的内容或影响
     */
    private String summary;

    /**
     * 关联的资源详细信息，包含对资源的具体描述
     */
    private ResourceDTO resource;

    /**
     * 资源数据传输对象，用于封装资源的相关信息
     */
    @NoArgsConstructor
    @Data
    public static class ResourceDTO {
        /**
         * 资源的原始类型，表示资源未加工前的类型
         */
        private String originalType;

        /**
         * 使用的算法类型，用于资源加密或处理时所用的算法
         */
        private String algorithm;

        /**
         * 密文，资源内容经过加密后的形式
         */
        private String ciphertext;

        /**
         * 关联数据，与加密资源相关的附加信息
         */
        private String associatedData;

        /**
         * 随机数，加密过程中使用的非重复数值，增强安全性
         */
        private String nonce;
    }
}
