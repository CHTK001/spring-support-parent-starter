package com.chua.starter.common.support.logger;

import lombok.Data;

import java.util.Date;

/**
 * 系统日志
 *
 * @author CH
 */
@Data
public class SysLog {
    /**
     * 方法
     */
    private String methodName;
    /**
     * 创建时间
     */
    private Date createTime;
    /**
     * 类
     */
    private String className;
    /**
     * 模块
     */
    private String logName;
    /**
     * 访问地址
     */
    private String logMapping;
    /**
     * 编码
     */
    private String logCode;
    /**
     * 查询参数
     */
    private String logParam;
    /**
     * 操作人
     */
    private String createName;
    /**
     * 操作人ID
     */
    private String createBy;
    /**
     * 结果
     */
    private Object result;
    /**
     * 内容
     */
    private String logContent;
    /**
     * logWatch
     */
    private String logWatch;
    /**
     * 耗时(s)
     */
    private Double logCost;
    /**
     * 請求地址
     */
    private String logAddress;
    /**
     * 动作
     */
    private String logAction;
    /**
     * 状态
     */
    private String logStatus;
}
