package com.chua.starter.common.support.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

/**
 * 返回结果
 *
 * @author CH
 * @since 2022/7/23 16:00
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@XmlRootElement(name = "ReturnResult")
public class ReturnXmlResult<T> {
    /**
     * http状态码
     */
    @XmlElement
    private Integer code;

    /**
     * 结果
     */
    @XmlElement
    private String data;
    /**
     * 信息
     */
    @XmlElement
    private String msg;

    /**
     * 初始化
     *
     * @param data 数据
     * @param msg  消息
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnXmlResult<T> ok(T data, String msg) {
        return new ReturnXmlResult<>(200, null, msg);
    }

    /**
     * 初始化
     *
     * @param <T> 类型
     * @return 结果
     */
    public static <T> ReturnXmlResult<T> noAuth() {
        return new ReturnXmlResult<>(403, null, null);
    }

    /**
     * 初始化
     *
     * @param data 数据
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnXmlResult<T> ok(T data) {
        return ok(data, "");
    }

    /**
     * 初始化
     *
     * @param <T> 类型
     * @return 结果
     */
    public static <T> ReturnXmlResult<T> ok() {
        return ok(null);
    }

    /**
     * 初始化
     *
     * @param data 数据
     * @param msg  消息
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnXmlResult<T> error(T data, String msg) {
        return new ReturnXmlResult<>(500, null, msg);
    }

    /**
     * 初始化
     *
     * @param data 数据
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnXmlResult<T> error(T data) {
        return error(data, "");
    }

    /**
     * 初始化
     *
     * @param <T> 类型
     * @return 结果
     */
    public static <T> ReturnXmlResult<T> error() {
        return error(null);
    }

    /**
     * 初始化
     *
     * @param data 数据
     * @param msg  消息
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnXmlResult<T> illegal(T data, String msg) {
        return new ReturnXmlResult<>(400, null, msg);
    }

    /**
     * 初始化
     *
     * @param data 数据
     * @param <T>  类型
     * @return 结果
     */
    public static <T> ReturnXmlResult<T> illegal(T data) {
        return illegal(data, "");
    }

    /**
     * 初始化
     *
     * @param <T> 类型
     * @return 结果
     */
    public static <T> ReturnXmlResult<T> illegal() {
        return illegal(null);
    }
}
