package com.chua.starter.server.controller;

import com.chua.starter.server.pojo.Doorplate;
import com.chua.starter.server.service.impl.DoorplateServiceImpl;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * (doorplate)表控制层
 *
 * @author xxxxx
 */
@RestController
@RequestMapping("/doorplate")
public class DoorplateController {
    /**
     * 服务对象
     */
    @Resource
    private DoorplateServiceImpl doorplateServiceImpl;

    /**
     * 通过主键查询单条数据
     *
     * @param id 主键
     * @return 单条数据
     */
    @GetMapping("selectOne")
    public Doorplate selectOne(String id) {
        return doorplateServiceImpl.getById(id);
    }

    /**
     * 通过主键查询单条数据
     *
     * @param id 主键
     * @return 单条数据
     */
    @GetMapping("test")
    public List<Doorplate> test(String id) {
        return doorplateServiceImpl.test(id);
    }

    /**
     *
     */
    @GetMapping("test2")
    public List<Doorplate> test2(String id, String name) {
        return doorplateServiceImpl.test2(id, name);
    }

}
