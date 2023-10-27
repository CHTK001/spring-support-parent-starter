package com.chua.starter.device.support.pojo;

import lombok.Data;

/**
 * @author CH
 */
@Data
public class StaticResult {

    /**
     * 总数
     */
    private long total = 0;
    /**
     * 成功数量
     */
    private long successTotal = 0;
    /**
     * 失败数量
     */
    private long failureTotal = 0;
    /**
     * 添加总数
     *
     * @param size 数量
     */
    public void addTotal(int size) {
        total += size;
    }
    /**
     * 添加成功数量
     *
     * @param size 数量
     */
    public void addSuccessTotal(int size) {
        successTotal += size;
    }
    /**
     * 添加失败数量
     *
     * @param size 数量
     */
    public void addFailureTotal(int size) {
        failureTotal += size;
    }
}
