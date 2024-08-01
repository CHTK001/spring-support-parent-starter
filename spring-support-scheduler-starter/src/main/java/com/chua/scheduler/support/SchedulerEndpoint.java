package com.chua.scheduler.support;

import lombok.AllArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;

import static org.springframework.boot.actuate.endpoint.annotation.Selector.Match.ALL_REMAINING;

/**
 * schedule 切入点
 * @author CH
 * @since 2024/8/1
 */
@AllArgsConstructor
@WebEndpoint(id = "scheduler")
public class SchedulerEndpoint {

    private ScheduleCornChangeHandler scheduleCornChangeHandler;

    /**
     * 热加载
     *
     * @return 结果
     */
    @WriteOperation
    public String reload(@Selector String name, @Selector(match = ALL_REMAINING) String express) {
        if (null == express || null == name) {
            return "加载器不存在";
        }

        return this.scheduleCornChangeHandler.cornChanged(express, name);
    }


    @ReadOperation
    public String scheduler() {
        return scheduleCornChangeHandler.scheduler();
    }
}
