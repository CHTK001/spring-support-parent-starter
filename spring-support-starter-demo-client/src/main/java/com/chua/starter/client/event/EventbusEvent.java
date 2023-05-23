package com.chua.starter.client.event;


import com.chua.common.support.eventbus.EventbusType;
import com.chua.common.support.eventbus.Subscribe;
import org.springframework.context.annotation.Configuration;


/**
 * <p>
 * 机构 前端控制器
 * </p>
 *
 * @author yzj
 * @since 2021-12-08
 */
@Configuration
public class EventbusEvent {
    @Subscribe(type = EventbusType.REDIS, name = "demo")
    public void listen(String msg) {
        System.out.println(msg);
    }

    @Subscribe(type = EventbusType.REDIS, name = "demo1")
    public void listen1(String msg) {
        System.out.println(msg);
    }
}
