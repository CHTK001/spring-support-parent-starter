package com.chua.starter.common.support.result;

import lombok.Data;

import javax.xml.bind.annotation.*;
import java.io.Serializable;

/**
 * @author Administrator
 */
@Data
@XmlAccessorType(XmlAccessType.FIELD)
@XmlRootElement(name = "root")
@XmlSeeAlso(Object.class)
public class ResultData<T> implements Serializable {
    /**
     * 结果状态 ,具体状态码参见ResultData.java
     */
    @XmlElement(name = "status")
    private int status;
    @XmlElement(name = "message")

    private String message;
    @XmlElement(name = "data")
    private T data;
    @XmlElement(name = "timestamp")
    private long timestamp;


    public ResultData() {
        this.timestamp = System.currentTimeMillis();
    }


    public static <T> ResultData<T> success(T data) {
        ResultData<T> resultData = new ResultData<>();
        resultData.setStatus(ReturnCode.RC0.getCode());
        resultData.setMessage(ReturnCode.RC0.getMessage());
        resultData.setData(data);
        return resultData;
    }

    public static <T> ResultData<T> failure(int code, String message) {
        ResultData<T> resultData = new ResultData<>();
        resultData.setStatus(code);
        resultData.setMessage(message);
        return resultData;
    }

    public static <T> ResultData<T> of(ReturnCode returnCode) {
        ResultData<T> resultData = new ResultData<>();
        resultData.setStatus(returnCode.getCode());
        resultData.setMessage(returnCode.getMessage());
        return resultData;
    }
}