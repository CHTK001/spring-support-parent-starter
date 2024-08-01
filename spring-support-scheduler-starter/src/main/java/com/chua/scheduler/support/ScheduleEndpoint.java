package com.chua.scheduler.support;

import lombok.AllArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;

/**
 * schedule 切入点
 * @author CH
 * @since 2024/8/1
 */
@AllArgsConstructor
@WebEndpoint(id = "schedule-change")
public class ScheduleEndpoint {

    private ScheduleCornChangeHandler scheduleCornChangeHandler;

    /**
     * 热加载
     *
     * @return 结果
     */
    @WriteOperation
    public String reload(@Selector String bean, @Selector String name) {
        if (null == bean || null == name) {
            return "加载器不存在";
        }

        return this.scheduleCornChangeHandler.cornChanged(bean, name);
    }
}
