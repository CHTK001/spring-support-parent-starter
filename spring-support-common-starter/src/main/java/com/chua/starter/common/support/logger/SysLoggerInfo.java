package com.chua.starter.common.support.logger;

import lombok.Getter;
import lombok.Setter;
import org.springframework.context.ApplicationEvent;

import java.util.Date;

/**
 * 系统日志
 *
 * @author CH
 */
@Getter
@Setter
public class SysLoggerInfo extends ApplicationEvent {
    /**
     * 方法
     */
    private String methodName;
    /**
     * 创建时间
     */
    private Date createTime;
    /**
     * 操作名
     */
    private String logName;

    /**
     * 浏览器指纹
     */
    private String fingerprint;

    /**
     * 登录方式
     */
    private String loginType;
    /**
     * 浏览器
     */
    private String system;

    /**
     * ua
     */
    private String ua;
    /**
     * 浏览器
     */
    private String browser;
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
    private Long logCost;
    /**
     * 請求地址
     */
    private String clientIp;
    /**
     * 操作模块
     */
    private String logModule;
    /**
     * 状态;0失败
     */
    private Integer logStatus;

    public SysLoggerInfo(Object source) {
        super(source);
    }
}
