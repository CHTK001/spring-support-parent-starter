package com.chua.starter.common.support.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;


/**
 * @author CH
 * @since 2024/12/9
 */
@Endpoint(id = "scheduler")
@RequiredArgsConstructor
public class SchedulerEndpoint {

    private final SchedulingRegistration schedulingRegistration;


    /**
     * 更新配置信息
     *
     * 该方法通过调用schedulingConfigurer的updateConfigurer方法来更新配置信息
     * 主要用于在运行时对配置进行修改，以适应动态配置的需求
     *
     * @param name 配置项的名称，用于标识特定的配置项
     * @param time 新的时间配置，表示配置项的新值
     * @return 返回更新操作的结果，通常是一个表示成功或失败的字符串消息
     */
    @WriteOperation
    public String update(@Selector String name, @Selector String time) {
        return schedulingRegistration.updateConfigurer(name, time);
    }
}
