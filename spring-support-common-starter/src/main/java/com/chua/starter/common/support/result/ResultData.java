package com.chua.starter.common.support.result;

import com.chua.common.support.lang.code.ResultCode;
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
    @XmlElement(name = "code")
    private String code;
    @XmlElement(name = "msg")
    private String msg;
    @XmlElement(name = "data")
    private T data;
    @XmlElement(name = "timestamp")
    private long timestamp;


    public ResultData() {
        this.timestamp = System.currentTimeMillis();
    }


    public static <T> ResultData<T> success(T data) {
        ResultData<T> resultData = new ResultData<>();
        resultData.setCode(ReturnCode.OK.getCode());
        resultData.setMsg(ReturnCode.OK.getMsg());
        resultData.setData(data);
        return resultData;
    }
    public static <T> ResultData<T> failure(ResultCode code, String message) {
        ResultData<T> resultData = new ResultData<>();
        resultData.setCode(code.getCode());
        resultData.setMsg(message);
        return resultData;
    }
    public static <T> ResultData<T> failure(String code, String message) {
        ResultData<T> resultData = new ResultData<>();
        resultData.setCode(code);
        resultData.setMsg(message);
        return resultData;
    }

    public static <T> ResultData<T> of(ReturnCode returnCode) {
        ResultData<T> resultData = new ResultData<>();
        resultData.setCode(returnCode.getCode());
        resultData.setMsg(returnCode.getMsg());
        return resultData;
    }
}