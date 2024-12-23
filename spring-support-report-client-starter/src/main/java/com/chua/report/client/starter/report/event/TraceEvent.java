package com.chua.report.client.starter.report.event;

import com.chua.common.support.annotations.OnRouterEvent;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Path;

/**
 * 链路追踪事件类，继承自TimestampEvent，用于记录链路追踪相关信息
 * 包含方法名、类名、耗时、参数大小及参数详情，便于追踪和分析系统内调用链路
 *
 * @author CH
 * @since 2024/9/20
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class TraceEvent extends TimestampEvent{

    /**
     * 方法名，标识被追踪的方法
     */
    private String method;

    /**
     * 类名，标识方法所属的类
     */
    private String className;

    /**
     * 行号，标识方法调用的行号
     */
    private int lineNumber;
    /**
     * 耗时，记录方法执行所花费的时间，单位为毫秒
     */
    private long cost;

    /**
     * 参数大小，记录方法调用时参数的总大小
     */
    private long parameterSize;

    /**
     * 状态码，记录方法调用的状态码，用于标识方法调用是否成功
     * 0: 成功
     * 1: 失败
     */
    private int code;
    /**
     * 参数详情，保存方法调用时传递的所有参数
     */
    private Object[] parameters;

    public void setParameters(Object[] parameters) {
        Object[] newParams = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Object parameter = parameters[i];
            newParams[i] = null == parameter ? null : parameter.toString();
        }

        this.parameters = newParams;
    }
}
