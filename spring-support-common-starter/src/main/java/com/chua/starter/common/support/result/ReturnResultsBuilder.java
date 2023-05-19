package com.chua.starter.common.support.result;

import lombok.Data;

import java.util.LinkedList;
import java.util.List;

/**
 * 結果
 * @author CH
 */
@Data
public class ReturnResultsBuilder<T> {
    protected ReturnResultsBuilder() {
    }

    /**
     * 编码
     */
    private int code;
    /**
     * 数据
     */
    private List<T> data;
    /**
     * 信息
     */
    private String msg;
    /**
     * 编码
     * @param code 编码
     */
    public ReturnResultsBuilder<T> code(int code) {
        this.code = code;
        return this;
    }
    /**
     * 信息
     * @param msg 信息
     */
    public ReturnResultsBuilder<T> msg(String msg) {
        this.msg = msg;
        return this;
    }
    /**
     * 数据
     * @param data 数据
     */
    public ReturnResultsBuilder<T> data(List<T> data) {
        this.data = data;
        return this;
    }
    /**
     * 数据
     * @param data 数据
     */
    public ReturnResultsBuilder<T> addData(T data) {
        if(null == this.data) {
            synchronized (this) {
                if(null == this.data) {
                    this.data = new LinkedList<>();
                }
            }
        }
        this.data.add(data);
        return this;
    }

    /**
     * 构建消息
     * @return 消息
     */
    public ReturnResult<List<T>> build() {
        ReturnResult<List<T>> rs = ReturnResult.of();
        rs.setCode(code);
        rs.setMsg(msg);
        rs.setData(data);
        return rs;
    }
    /**
     * 构建结果
     * @param <T> 类型
     * @return 构造器
     */
    public static <T> ReturnResultsBuilder<T> newBuilder() {
        return new ReturnResultsBuilder<>();
    }
}
