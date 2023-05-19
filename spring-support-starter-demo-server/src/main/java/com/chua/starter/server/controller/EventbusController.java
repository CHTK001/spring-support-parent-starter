package com.chua.starter.server.controller;


import com.chua.starter.core.support.eventbus.EventbusTemplate;
import com.chua.starter.core.support.eventbus.EventbusType;
import com.chua.starter.core.support.eventbus.Subscribe;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * <p>
 * 机构 前端控制器
 * </p>
 *
 * @author yzj
 * @since 2021-12-08
 */
@RestController
@RequestMapping("/eb")
public class EventbusController {
    @Autowired
    private EventbusTemplate eventbusTemplate;

    @GetMapping("/send")
    public void list(String name, String message) {
        eventbusTemplate.post(name, message);
    }


    @Subscribe(type = EventbusType.REDIS, name = "demo")
    public void listen(String msg) {
        System.out.println(msg);
    }
}
