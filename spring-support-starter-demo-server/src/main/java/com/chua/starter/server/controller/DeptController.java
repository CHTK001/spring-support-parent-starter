package com.chua.starter.server.controller;


import com.boren.biz.dict.service.IEleDictService;
import com.chua.starter.server.pojo.Dept;
import com.chua.starter.server.script.TDemoInfo;
import com.chua.starter.server.service.IDeptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;


/**
 * <p>
 * 机构 前端控制器
 * </p>
 *
 * @author yzj
 * @since 2021-12-08
 */
@RestController
@RequestMapping("/v2/dept")
public class DeptController {
    @Autowired
    private IDeptService mIDeptService;

    private IEleDictService iEleDictService;

    @Resource
    private ApplicationContext applicationContext;

    @GetMapping("/list")
    public List<Dept> list() {
        int list = iEleDictService.count2();
        return Collections.emptyList();
    }

    @GetMapping("/script")
    public String list3() {
        return applicationContext.getBeanProvider(TDemoInfo.class).stream().findFirst()
                .get().getId();
    }

    @GetMapping("/list1")
    public List<Dept> list1() {
        return mIDeptService.getSanmen1();
    }

}
